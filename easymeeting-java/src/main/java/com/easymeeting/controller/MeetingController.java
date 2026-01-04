package com.easymeeting.controller;

import com.easymeeting.dto.JoinMeetingDto;
import com.easymeeting.dto.MeetingCreateDto;
import com.easymeeting.dto.TokenUserInfoDto;
import com.easymeeting.entity.MeetingInfo;
import com.easymeeting.entity.MeetingMember;
import com.easymeeting.enums.MeetingMemberStatusEnum;
import com.easymeeting.enums.MeetingStatusEnum;
import com.easymeeting.exception.BusinessException;
import com.easymeeting.interceptor.TokenInterceptor;
import com.easymeeting.redis.RedisComponent;
import com.easymeeting.service.MeetingInfoService;
import com.easymeeting.service.impl.MeetingMemberServiceImpl;
import com.easymeeting.utils.StringUtils;
import com.easymeeting.vo.PageResult;
import com.easymeeting.vo.ResponseVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;


@RestController
@RequestMapping("/meeting")
@Validated
@Slf4j
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingInfoService meetingInfoService;
    private final RedisComponent redisComponent;
    private final MeetingMemberServiceImpl meetingMemberServiceImpl;

    /**
     * 加载所有历史会议（我创建的 + 我参加的）
     */
    @GetMapping("/loadMeeting")
    public ResponseVO<PageResult<MeetingInfo>> loadMeeting(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        String userId = tokenUserInfoDto.getUserId();
        
        PageResult<MeetingInfo> result = meetingInfoService.loadAllMeetings(userId, pageNo, pageSize);
        return ResponseVO.success(result);
    }

    /**
     * 加载我创建的会议
     */
    @GetMapping("/loadMyCreatedMeeting")
    public ResponseVO<PageResult<MeetingInfo>> loadMyCreatedMeeting(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        String userId = tokenUserInfoDto.getUserId();
        
        PageResult<MeetingInfo> result = meetingInfoService.loadCreatedMeetings(userId, pageNo, pageSize);
        return ResponseVO.success(result);
    }

    /**
     * 加载我参加的会议（不包括我创建的）
     */
    @GetMapping("/loadMyJoinedMeeting")
    public ResponseVO<PageResult<MeetingInfo>> loadMyJoinedMeeting(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        String userId = tokenUserInfoDto.getUserId();
        
        PageResult<MeetingInfo> result = meetingInfoService.loadJoinedMeetings(userId, pageNo, pageSize);
        return ResponseVO.success(result);
    }
    /**
     * 快速会议 - 创建会议
     * 使用个人会议号还是系统生成，会议主题，是否需要会议密码
     */
    @PostMapping("/quickMeeting")
    public ResponseVO<String> quickMeeting(@RequestBody @Validated MeetingCreateDto meetingCreateDto, HttpServletRequest request) {
        TokenUserInfoDto currentUser = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        if(currentUser.getCurrentMeetingId()!=null){
            throw new BusinessException("你有未结束的会议，无法创建新的会议");
        }
        MeetingInfo meetingInfo=new MeetingInfo();
        meetingInfo.setMeetingName(meetingCreateDto.getMeetingName());
        meetingInfo.setMeetingNo(meetingCreateDto.getMeetingNoType()==0?currentUser.getMeetingNo() : StringUtils.generateMeetingNo());
        meetingInfo.setJoinPassword(meetingCreateDto.getJoinPassword());
        meetingInfo.setCreateUserId(currentUser.getUserId());
        meetingInfoService.quickMeeting(meetingInfo,currentUser.getNickName());

        currentUser.setCurrentMeetingId(meetingInfo.getMeetingId());
        currentUser.setCurrentNickName(currentUser.getNickName());

        redisComponent.updateTokenUserInfo(currentUser);

        return ResponseVO.success(meetingInfo.getMeetingId());
    }

    /**
     *  加入会议实现这个接口后，前端跳到joinMeeting这个接口
     * @param meetingNo
     * @param nickName
     * @param password
     * @param request
     * @return
     */
    @PostMapping("/preJoinMeeting")
    public ResponseVO<String> preJoinMeeting(@NotNull String meetingNo,@NotEmpty String nickName,String password, HttpServletRequest request) {
            TokenUserInfoDto currentUser = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
            meetingNo=meetingNo.replace(" ","");
            currentUser.setNickName(nickName);
            String meetingId=meetingInfoService.preJoinMeeting(meetingNo,currentUser,password);
        return ResponseVO.success(meetingId);
    }


    /**
     * 加入会议
     * 
     * 安全说明：
     * 1. 用户必须先通过 preJoinMeeting 验证，才能调用此接口
     * 2. 用户敏感信息（userId、nickName、sex）从 token 获取，不信任前端传入
     * 3. meetingId 必须与 token 中的 currentMeetingId 一致，防止绕过 preJoin
     */
    @PostMapping("/joinMeeting")
    public ResponseVO<Void> joinMeeting(@RequestBody JoinMeetingDto joinMeetingDto, HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        
        // 问题3修复：验证用户是否已通过 preJoinMeeting
        // currentMeetingId 在 preJoinMeeting 中设置，如果为空说明没有通过预验证
        if (StringUtils.isEmpty(tokenUserInfoDto.getCurrentMeetingId())) {
            throw new BusinessException("请先通过会议号加入会议");
        }
        
        // 问题3修复：验证 meetingId 与 token 中记录的一致（防止绕过 preJoin）
        String requestMeetingId = joinMeetingDto.getMeetingId();
        if (StringUtils.isNotEmpty(requestMeetingId) 
                && !requestMeetingId.equals(tokenUserInfoDto.getCurrentMeetingId())) {
            throw new BusinessException("会议ID不匹配，请重新加入会议");
        }
        
        // 问题2修复：从 token 获取用户信息，不信任前端传入
        // 这样即使前端伪造 userId 也无效
        joinMeetingDto.setUserId(tokenUserInfoDto.getUserId());
        joinMeetingDto.setNickName(tokenUserInfoDto.getNickName());
        joinMeetingDto.setSex(tokenUserInfoDto.getSex());
        joinMeetingDto.setMeetingId(tokenUserInfoDto.getCurrentMeetingId());
        
        meetingInfoService.joinMeeting(joinMeetingDto);
        return ResponseVO.success();
    }
    @GetMapping("/exitMeeting")
    public ResponseVO<Void> exitMeeting(HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);

        meetingInfoService.exitMeetingRoom(tokenUserInfoDto, MeetingMemberStatusEnum.EXIT_MEETING);
        return ResponseVO.success();
    }
    @GetMapping("/kickOutMeeting")
    public ResponseVO<Void>  kickOutMeeting(@RequestParam String userId, HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        meetingInfoService.forceExitMeetingRoom(tokenUserInfoDto, userId, MeetingMemberStatusEnum.KICK_OUT);
        return ResponseVO.success();
    }
    @GetMapping("/blackMeeting")
    public ResponseVO<Void> blackMeeting(@RequestParam String userId, HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        meetingInfoService.forceExitMeetingRoom(tokenUserInfoDto, userId, MeetingMemberStatusEnum.BLACKLIST);
        return ResponseVO.success();
    }
    @GetMapping("/finishMeeting")
    public ResponseVO<Void> finishMeeting( HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        meetingInfoService.finishMeeting(tokenUserInfoDto.getCurrentMeetingId(),tokenUserInfoDto.getUserId());
        return ResponseVO.success();
    }
    @GetMapping("/getCurrentMeeting")
    public ResponseVO<MeetingInfo> getCurrentMeeting( HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        if(StringUtils.isEmpty(tokenUserInfoDto.getCurrentMeetingId())){
            return ResponseVO.success(null);
        }
        MeetingInfo meetingInfo=meetingInfoService.getMeetingById(tokenUserInfoDto.getCurrentMeetingId());
        if(MeetingStatusEnum.FINISHED.getStatus().equals(meetingInfo.getStatus())){
            return ResponseVO.success(null);
        }
        return ResponseVO.success(meetingInfo);
    }

    /**
     * 删除历史会议的显示
     * @param meetingId
     * @param request
     * @return
     */
    @GetMapping("/delMeetingRecord")
    public ResponseVO<Void> delMeetingRecord( @NotEmpty String meetingId, HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        MeetingMember meetingMember=meetingMemberServiceImpl.getMember(meetingId,tokenUserInfoDto.getUserId());
        meetingMember.setStatus(MeetingMemberStatusEnum.DEL_MEETING.getStatus());
        meetingMemberServiceImpl.updateMember(meetingMember);
        return ResponseVO.success();
    }

    /**
     * 查看历史会议会议的成员信息
     * @param meetingId
     * @param request
     * @return
     */
    @GetMapping("/loadMeetingMembers")
    public ResponseVO<List<MeetingMember>> loadMeetingMembers( @NotEmpty String meetingId, HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        List<MeetingMember> meetingMemberList=meetingMemberServiceImpl.getMembersByMeetingId(meetingId);
        // 检查当前用户是否在会议成员列表中
        Optional<MeetingMember> currentUserMember = meetingMemberList.stream()
                .filter(member -> member.getUserId().equals(tokenUserInfoDto.getUserId()))
                .findFirst();
        if(!currentUserMember.isPresent()){
            throw new BusinessException("你不在会议中，无法查看成员信息");
        }
        return ResponseVO.success(meetingMemberList);
    }
    @RequestMapping("/reserveJoinMeeting")
    public ResponseVO<Void> reserveJoinMeeting( @NotEmpty String meetingId,@NotEmpty String nickName, String password,HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        tokenUserInfoDto.setNickName(nickName);
        meetingInfoService.reserveJoinMeeting(meetingId,tokenUserInfoDto,password);
        return ResponseVO.success(null);
    }
    /**
     * 邀请联系人加入会议
     * 
     * 邀请者必须在会议中，被邀请者必须是邀请者的好友
     * 邀请信息会保存到 Redis，有效期 3 分钟
     */
    @PostMapping("/inviteContactToMeeting")
    public ResponseVO<Void> inviteContactToMeeting(@RequestBody List<String> contactsId, HttpServletRequest request) {
        if (contactsId == null || contactsId.isEmpty()) {
            throw new BusinessException("邀请列表不能为空");
        }
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        meetingInfoService.inviteContact(tokenUserInfoDto, contactsId);
        return ResponseVO.success();
    }
    
    /**
     * 接受会议邀请
     * 
     * 被邀请用户接受邀请后，不需要输入密码，直接设置 currentMeetingId
     * 然后前端跳转到会议页面，调用 joinMeeting 正式加入
     * 
     * @param meetingId 会议ID
     * @return 会议ID（供前端跳转使用）
     */
    @PostMapping("/acceptInvite")
    public ResponseVO<String> acceptInvite(@RequestParam @NotEmpty String meetingId, HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        meetingInfoService.acceptInvite(tokenUserInfoDto, meetingId);
        // 返回 meetingId，前端可以用来跳转到会议页面
        return ResponseVO.success(meetingId);
    }






}


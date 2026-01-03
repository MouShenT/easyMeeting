package com.easymeeting.controller;

import com.easymeeting.dto.TokenUserInfoDto;
import com.easymeeting.entity.MeetingReserve;
import com.easymeeting.enums.MeetingReserveStatusEnum;
import com.easymeeting.interceptor.TokenInterceptor;
import com.easymeeting.service.MeetingReserveService;
import com.easymeeting.vo.ResponseVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/meetingReserve")
@Validated
@Slf4j
@RequiredArgsConstructor
public class MeetingReserveController {

    private final MeetingReserveService meetingReserveService;

    /**
     * 加载预约会议列表（未开始的，按开始时间倒序）
     */
    @RequestMapping("/loadMeetingReserve")
    public ResponseVO<List<MeetingReserve>> loadMeetingReserve(HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        String userId = tokenUserInfoDto.getUserId();
        // 查询未开始的预约会议，按 start_time desc 排序
        List<MeetingReserve> list = meetingReserveService.loadCreatedReservesByStatus(
                userId, MeetingReserveStatusEnum.NO_START.getStatus());
        return ResponseVO.success(list);
    }

    /**
     * 创建预约会议
     * TODO 应该后面添加联系人后要有一个联系人接口补充下面的inviteUserId
     */
    @RequestMapping("/createMeetingReserve")
    public ResponseVO<Void> createMeetingReserve(MeetingReserve meetingReserve, 
                                                  @RequestParam(required = false) List<String> inviteUserId, 
                                                  HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        meetingReserve.setCreateUserId(tokenUserInfoDto.getUserId());
        meetingReserveService.createReserve(meetingReserve, inviteUserId);
        return ResponseVO.success();
    }

    /**
     * 创建人删除预约会议
     */
    @RequestMapping("/delMeetingReserve")
    public ResponseVO<Void> delMeetingReserve(@NotEmpty String meetingId, HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        String userId = tokenUserInfoDto.getUserId();
        meetingReserveService.deleteReserve(meetingId, userId);
        return ResponseVO.success();
    }

    /**
     * 用户退出预约会议（不想参加了）
     */
    @RequestMapping("/delMeetingReserveByUser")
    public ResponseVO<Void> delMeetingReserveByUser(@NotEmpty String meetingId, HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        String userId = tokenUserInfoDto.getUserId();
        meetingReserveService.removeInviteMember(meetingId, userId);
        return ResponseVO.success();
    }

    /**
     * 获取今天的会议（创建的 + 被邀请的）
     */
    @RequestMapping("/loadTodayMeeting")
    public ResponseVO<List<MeetingReserve>> loadTodayMeeting(HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        String userId = tokenUserInfoDto.getUserId();
        List<MeetingReserve> list = meetingReserveService.loadTodayReserves(userId);
        return ResponseVO.success(list);
    }

    /**
     * 开始预约会议 - 创建人点击开始
     * @param reserveId 预约会议ID
     * @return 实际会议ID (MeetingInfo.meetingId)
     */
    @RequestMapping("/startReserveMeeting")
    public ResponseVO<String> startReserveMeeting(@NotEmpty String reserveId, HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        String realMeetingId = meetingReserveService.startReserveMeeting(reserveId, tokenUserInfoDto);
        return ResponseVO.success(realMeetingId);
    }

    /**
     * 加入预约会议 - 任何已认证用户加入
     * @param reserveId 预约会议ID
     * @return 实际会议ID
     */
    @RequestMapping("/joinReserveMeeting")
    public ResponseVO<String> joinReserveMeeting(@NotEmpty String reserveId, HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        String realMeetingId = meetingReserveService.joinReserveMeeting(reserveId, tokenUserInfoDto);
        return ResponseVO.success(realMeetingId);
    }
}

package com.easymeeting.service.impl;

import com.easymeeting.dto.*;
import com.easymeeting.entity.MeetingInfo;
import com.easymeeting.entity.MeetingMember;
import com.easymeeting.entity.MeetingReserve;
import com.easymeeting.entity.UserContact;
import com.easymeeting.enums.*;
import com.easymeeting.exception.BusinessException;
import com.easymeeting.mapper.*;
import com.easymeeting.redis.RedisComponent;
import com.easymeeting.service.MeetingInfoService;
import com.easymeeting.utils.StringUtils;
import com.easymeeting.vo.PageResult;
import com.easymeeting.websocket.ChannelContextUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MeetingInfoServiceImpl implements MeetingInfoService {

    @Resource
    private MeetingInfoMapper meetingInfoMapper;
    @Resource
    private ChannelContextUtils channelContextUtils;
    @Resource
    private MeetingMemberMapper meetingMemberMapper;
    @Resource
    private RedisComponent redisComponent;
    @Resource
    private MeetingReserveMemberMapper meetingReserveMemberMapper;
    @Resource
    private MeetingReserveMapper meetingReserveMapper;
    @Resource
    private UserContactMapper userContactMapper;

    @Override
    public MeetingInfo createMeeting(MeetingInfo meetingInfo) {
        meetingInfoMapper.insert(meetingInfo);
        return meetingInfo;
    }

    @Override
    public MeetingInfo updateMeeting(MeetingInfo meetingInfo) {
        meetingInfoMapper.updateById(meetingInfo);
        return meetingInfo;
    }

    @Override
    public MeetingInfo getMeetingById(String meetingId) {
        return meetingInfoMapper.selectById(meetingId);
    }

    @Override
    public MeetingInfo getMeetingByNo(String meetingNo) {
        return meetingInfoMapper.selectByMeetingNo(meetingNo);
    }

    @Override
    public List<MeetingInfo> getMeetingsByUserId(String userId) {
        return meetingInfoMapper.selectByCreateUserId(userId);
    }

    @Override
    public List<MeetingInfo> listMeetings(Integer status) {
        return meetingInfoMapper.selectList(status);
    }

    @Override
    public void deleteMeeting(String meetingId) {
        meetingInfoMapper.deleteById(meetingId);
    }

    @Override
    public PageResult<MeetingInfo> loadAllMeetings(String userId, Integer pageNo, Integer pageSize) {
        int offset = (pageNo - 1) * pageSize;
        Long total = meetingInfoMapper.countAllMeetings(userId);
        List<MeetingInfo> list = meetingInfoMapper.selectAllMeetings(userId, offset, pageSize);
        return PageResult.of(pageNo, pageSize, total, list);
    }

    @Override
    public PageResult<MeetingInfo> loadCreatedMeetings(String userId, Integer pageNo, Integer pageSize) {
        int offset = (pageNo - 1) * pageSize;
        Long total = meetingInfoMapper.countCreatedMeetings(userId);
        List<MeetingInfo> list = meetingInfoMapper.selectCreatedMeetings(userId, offset, pageSize);
        return PageResult.of(pageNo, pageSize, total, list);
    }

    @Override
    public PageResult<MeetingInfo> loadJoinedMeetings(String userId, Integer pageNo, Integer pageSize) {
        int offset = (pageNo - 1) * pageSize;
        Long total = meetingInfoMapper.countJoinedMeetings(userId);
        List<MeetingInfo> list = meetingInfoMapper.selectJoinedMeetings(userId, offset, pageSize);
        return PageResult.of(pageNo, pageSize, total, list);
    }

    @Override
    public void quickMeeting(MeetingInfo meetingInfo, String nickName) {
        meetingInfo.setCreateTime(LocalDateTime.now());
        meetingInfo.setMeetingId(StringUtils.generateMeetingNo());
        // 如果没有设置 meetingNo，自动生成一个（预约会议开始时会用到）
        if (StringUtils.isEmpty(meetingInfo.getMeetingNo())) {
            meetingInfo.setMeetingNo(StringUtils.generateMeetingNo());
        }
        meetingInfo.setStartTime(LocalDateTime.now());
        meetingInfo.setStatus(MeetingStatusEnum.RUNING.getStatus());
        meetingInfoMapper.insert(meetingInfo);
    }
    private void addMeetingMember(String meetingId, String userId,String nickName,Integer memberType) {
        MeetingMember meetingMember=new MeetingMember();
        meetingMember.setMeetingId(meetingId);
        meetingMember.setUserId(userId);
        meetingMember.setNickName(nickName);
        meetingMember.setLastJoinTime(LocalDateTime.now());
        meetingMember.setStatus(MeetingMemberStatusEnum.NORMAL.getStatus());
        meetingMember.setMemberType(memberType);
        meetingMember.setMeetingStatus(MeetingStatusEnum.RUNING.getStatus());
        if(meetingMemberMapper.selectByMeetingIdAndUserId(meetingId, userId) == null) {
            meetingMemberMapper.insert(meetingMember);
        }else {
            meetingMemberMapper.updateByMeetingIdAndUserId(meetingMember);
        }
    }
    private void addToMeeting(String meetingId, String userId,String nickName,Integer sex,Integer memberType,Boolean videoOpen) {
        MeetingMemberDto meetingMemberDto=new MeetingMemberDto();
        meetingMemberDto.setUserId(userId);
        meetingMemberDto.setNickName(nickName);
        meetingMemberDto.setJoinTime(System.currentTimeMillis());
        meetingMemberDto.setMemberType(memberType);
        meetingMemberDto.setStatus(MeetingMemberStatusEnum.NORMAL.getStatus());
        meetingMemberDto.setVideoOpen(videoOpen);
        meetingMemberDto.setSex(sex);
        redisComponent.addToMeeting(meetingId,meetingMemberDto);

    }
    private void checkMeetingJoin(String meetingId, String userId) {
        // 1. 先检查 Redis 中的状态（如果用户还在会议中）
        MeetingMemberDto meetingMemberDto = redisComponent.getMeetingMember(meetingId, userId);
        if (meetingMemberDto != null && MeetingMemberStatusEnum.BLACKLIST.getStatus().equals(meetingMemberDto.getStatus())) {
            throw new BusinessException("已经被拉黑");
        }
        
        // 2. 检查数据库中的状态（用户被拉黑后会从 Redis 移除，但数据库有记录）
        MeetingMember meetingMember = meetingMemberMapper.selectByMeetingIdAndUserId(meetingId, userId);
        if (meetingMember != null && MeetingMemberStatusEnum.BLACKLIST.getStatus().equals(meetingMember.getStatus())) {
            throw new BusinessException("已经被拉黑");
        }
    }
    /**
     * 加入会议
     * 
     * 问题1修复说明：
     * - preJoinMeeting 已经完成了完整的校验（会议存在性、密码、黑名单等）
     * - joinMeeting 只需要做轻量级检查，避免重复校验
     * - 信任 Controller 层已经验证了 meetingId 与 token.currentMeetingId 一致
     * 
     * @param joinMeetingDto 加入会议的参数（userId、nickName、sex 已由 Controller 从 token 填充）
     */
    @Override
    public void joinMeeting(JoinMeetingDto joinMeetingDto) {
        String meetingId = joinMeetingDto.getMeetingId();
        String userId = joinMeetingDto.getUserId();
        
        // 基本参数校验
        if (StringUtils.isEmpty(meetingId)) {
            throw new BusinessException("会议ID不能为空");
        }
        
        // 轻量级检查：只检查会议是否存在和状态
        // 注意：不再调用 checkMeetingJoin()，因为 preJoinMeeting 已经校验过了
        MeetingInfo meetingInfo = meetingInfoMapper.selectById(meetingId);
        if (meetingInfo == null) {
            throw new BusinessException("会议不存在");
        }
        if (MeetingStatusEnum.FINISHED.getStatus().equals(meetingInfo.getStatus())) {
            throw new BusinessException("会议已结束");
        }

        // 加入成员
        MemberTypeEnum memberTypeEnum = meetingInfo.getCreateUserId().equals(userId)
                ? MemberTypeEnum.COMPERE : MemberTypeEnum.NORMAL;
        addMeetingMember(meetingId, userId, joinMeetingDto.getNickName(), memberTypeEnum.getType());
        
        // 加入会议（Redis）
        addToMeeting(meetingId, userId, joinMeetingDto.getNickName(), 
                joinMeetingDto.getSex(), memberTypeEnum.getType(), joinMeetingDto.getVideoOpen());
        
        // 加入 WebSocket 房间
        channelContextUtils.joinMeetingRoom(meetingId, channelContextUtils.getChannel(userId));
        
        // 发送 WebSocket 消息通知其他成员
        MeetingJoinDto meetingJoinDto = new MeetingJoinDto();
        meetingJoinDto.setNewMember(redisComponent.getMeetingMember(meetingId, userId));
        meetingJoinDto.setMeetingMemberList(redisComponent.getMeetingMemberList(meetingId));

        MessageSendDto<MeetingJoinDto> messageSendDto = new MessageSendDto<>();
        messageSendDto.setMessageType(MessageTypeEnum.ADD_MEETING_ROOM.getType());
        messageSendDto.setMeetingId(meetingId);
        messageSendDto.setMessageSendToType(MessageSendToTypeEnum.GROUP.getType());
        messageSendDto.setMessageContent(meetingJoinDto);

        channelContextUtils.sendMessage(messageSendDto);
    }
    //加入会议实现这个接口后，前端跳到joinMeeting这个接口
    @Override
    public String preJoinMeeting(String meetingNo, TokenUserInfoDto tokenUserInfoDto, String password) {
        String userId=tokenUserInfoDto.getUserId();
        MeetingInfo meetingInfo = meetingInfoMapper.selectRunningMeetingByMeetingNo(meetingNo);
        if(meetingInfo==null){
            throw new BusinessException("会议不存在");
        }
        if(StringUtils.isNotEmpty(tokenUserInfoDto.getCurrentMeetingId())&&!meetingInfo.getMeetingId().equals(tokenUserInfoDto.getCurrentMeetingId())) {
            throw new BusinessException("你有未结束的会议");
        }
        checkMeetingJoin(meetingInfo.getMeetingId(),userId);
        if(MeetingJoinTypeEnum.PASSWORD.getType().equals(meetingInfo.getJoinType())&&!meetingInfo.getJoinPassword().equals(password)) {
            throw new BusinessException("密码错误");
        }
        tokenUserInfoDto.setCurrentMeetingId(meetingInfo.getMeetingId());
        redisComponent.updateTokenUserInfo(tokenUserInfoDto);
        return meetingInfo.getMeetingId();
    }

    @Override
    public void exitMeetingRoom(TokenUserInfoDto tokenUserInfoDto, MeetingMemberStatusEnum statusEnum) {
        String meetingId = tokenUserInfoDto.getCurrentMeetingId();
        if (StringUtils.isEmpty(meetingId)) {
            return;
        }
        String userId = tokenUserInfoDto.getUserId();
        
        // 1. 从 Redis 会议成员列表中移除
        Boolean exit = redisComponent.exitMeeting(meetingId, userId, statusEnum);
        
        // 2. 清除用户的当前会议ID（Redis 中的 token 信息）
        tokenUserInfoDto.setCurrentMeetingId(null);
        redisComponent.updateTokenUserInfo(tokenUserInfoDto);
        
        if (!exit) {
            return;
        }
        
        // 3. 如果被拉黑，更新数据库中的成员状态
        if (MeetingMemberStatusEnum.BLACKLIST.equals(statusEnum)) {
            MeetingMember meetingMember = meetingMemberMapper.selectByMeetingIdAndUserId(meetingId, userId);
            if (meetingMember != null) {
                meetingMember.setStatus(MeetingMemberStatusEnum.BLACKLIST.getStatus());
                meetingMemberMapper.updateByMeetingIdAndUserId(meetingMember);
            }
        }
        
        // 4. 构建退出消息
        List<MeetingMemberDto> meetingMemberDtoList = redisComponent.getMeetingMemberList(meetingId);
        MeetingExitDto meetingExitDto = new MeetingExitDto();
        meetingExitDto.setMeetingMemberList(meetingMemberDtoList);
        meetingExitDto.setExitUserId(userId);
        meetingExitDto.setExitStatus(statusEnum.getStatus());

        MessageSendDto<MeetingExitDto> messageSendDto = new MessageSendDto<>();
        messageSendDto.setMessageType(MessageTypeEnum.EXIT_MEETING_ROOM.getType());
        messageSendDto.setMessageContent(meetingExitDto);
        messageSendDto.setMeetingId(meetingId);
        messageSendDto.setMessageSendToType(MessageSendToTypeEnum.GROUP.getType());
        messageSendDto.setSendUserId(userId);
        
        // 5. 发送消息并清理 Channel（从会议房间移除）
        channelContextUtils.sendExitMessageAndCleanup(messageSendDto, userId);
        
        // 6. 如果被拉黑或被踢出，可选择强制断开 WebSocket 连接
        // 注意：这会导致用户需要重新建立 WebSocket 连接
        // 如果不需要强制断开，可以注释掉这段代码
        if (MeetingMemberStatusEnum.BLACKLIST.equals(statusEnum) || 
            MeetingMemberStatusEnum.KICK_OUT.equals(statusEnum)) {
            // 强制关闭用户的 WebSocket 连接，同时清理 USER_CONTEXT_MAP
            channelContextUtils.closeContext(userId);
        }
        
        // 7. 检查会议是否还有人，没人则自动结束会议
        if (meetingMemberDtoList == null || meetingMemberDtoList.isEmpty()) {
            // 自动结束会议时传入 null，跳过权限检查
            finishMeeting(meetingId, null);
        }
    }

    @Override
    public void forceExitMeetingRoom(TokenUserInfoDto tokenUserInfoDto, String userId,MeetingMemberStatusEnum meetingMemberStatusEnum) {
        //检验操作的是不是会议创建者，不是就不能踢人和拉黑
        MeetingInfo meetingInfo=meetingInfoMapper.selectById(tokenUserInfoDto.getCurrentMeetingId());
        if(!meetingInfo.getCreateUserId().equals(tokenUserInfoDto.getUserId())){
            throw new BusinessException("你没有权限");
        }
        // 先通过 userId 获取 token，再获取 TokenUserInfoDto
        String token = redisComponent.getTokenByUserId(userId);
        if (token == null) {
            throw new BusinessException("用户不在线");
        }
        TokenUserInfoDto userInfoDto = redisComponent.getTokenUserInfo(token);
        if (userInfoDto == null) {
            throw new BusinessException("用户信息不存在");
        }
        exitMeetingRoom(userInfoDto, meetingMemberStatusEnum);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishMeeting(String meetingId, String userId) {
        MeetingInfo meetingInfo = meetingInfoMapper.selectById(meetingId);
        if (meetingInfo == null) {
            throw new BusinessException("会议不存在");
        }
        if (MeetingStatusEnum.FINISHED.getStatus().equals(meetingInfo.getStatus())) {
            // 会议已结束，直接返回，避免重复处理
            return;
        }
        // 只有主持人可以主动结束会议，自动结束（userId=null）时跳过权限检查
        if (userId != null && !meetingInfo.getCreateUserId().equals(userId)) {
            throw new BusinessException("你没有权限");
        }
        
        // 1. 更新 MeetingInfo 表状态
        meetingInfo.setStatus(MeetingStatusEnum.FINISHED.getStatus());
        meetingInfo.setEndTime(LocalDateTime.now());
        meetingInfoMapper.updateById(meetingInfo);
        
        // 2. 获取会议中的所有成员（从 Redis）
        List<MeetingMemberDto> memberList = redisComponent.getMeetingMemberList(meetingId);
        
        // 3. 构建并发送会议结束消息（只有还有成员时才发送）
        if (memberList != null && !memberList.isEmpty()) {
            MessageSendDto<String> messageSendDto = new MessageSendDto<>();
            messageSendDto.setMessageType(MessageTypeEnum.FINIS_MEETING.getType());
            messageSendDto.setMeetingId(meetingId);
            messageSendDto.setMessageSendToType(MessageSendToTypeEnum.GROUP.getType());
            messageSendDto.setSendUserId(userId);
            messageSendDto.setMessageContent("会议已结束");
            channelContextUtils.sendMessage(messageSendDto);
        }
        
        // 4. 批量更新 MeetingMember 表（更新会议状态为已结束）
        List<MeetingMember> dbMembers = meetingMemberMapper.selectByMeetingId(meetingId);
        for (MeetingMember member : dbMembers) {
            member.setMeetingStatus(MeetingStatusEnum.FINISHED.getStatus());
            meetingMemberMapper.updateByMeetingIdAndUserId(member);
        }
        
        // 4.1 更新预约会议状态为已结束（如果该会议是从预约会议开始的）
        MeetingReserve meetingReserve = meetingReserveMapper.selectByRealMeetingId(meetingId);
        if (meetingReserve != null) {
            meetingReserve.setStatus(MeetingReserveStatusEnum.FINISHED.getStatus());
            meetingReserveMapper.updateById(meetingReserve);
        }
        
        // 5. 批量更新 TokenUserInfo（清除 currentMeetingId）并清理 WebSocket 房间
        if (memberList != null) {
            for (MeetingMemberDto member : memberList) {
                String memberUserId = member.getUserId();
                String token = redisComponent.getTokenByUserId(memberUserId);
                if (token != null) {
                    TokenUserInfoDto tokenUserInfo = redisComponent.getTokenUserInfo(token);
                    if (tokenUserInfo != null && meetingId.equals(tokenUserInfo.getCurrentMeetingId())) {
                        tokenUserInfo.setCurrentMeetingId(null);
                        redisComponent.updateTokenUserInfo(tokenUserInfo);
                    }
                }
                // 从 WebSocket 房间移除（需要检查 channel 是否存在）
                io.netty.channel.Channel channel = channelContextUtils.getChannel(memberUserId);
                if (channel != null) {
                    channelContextUtils.leaveMeetingRoom(meetingId, channel);
                }
            }
        }
        
        // 6. 清理 Redis 中的会议成员数据
        redisComponent.removeMeetingMembers(meetingId);
    }

    @Override
    public void reserveJoinMeeting(String meetingId, TokenUserInfoDto tokenUserInfoDto, String password) {
        String userId = tokenUserInfoDto.getUserId();
        
        // 1. 查询会议是否存在
        MeetingInfo meetingInfo = meetingInfoMapper.selectById(meetingId);
        if (meetingInfo == null) {
            throw new BusinessException("会议不存在");
        }
        
        // 2. 检查会议状态
        if (MeetingStatusEnum.FINISHED.getStatus().equals(meetingInfo.getStatus())) {
            throw new BusinessException("会议已结束");
        }
        
        // 3. 检查用户是否有未结束的其他会议
        if (StringUtils.isNotEmpty(tokenUserInfoDto.getCurrentMeetingId()) 
                && !meetingId.equals(tokenUserInfoDto.getCurrentMeetingId())) {
            throw new BusinessException("你有未结束的会议");
        }
        
        // 4. 检查是否被拉黑
        checkMeetingJoin(meetingId, userId);
        
        // 5. 验证密码（如果需要）
        if (MeetingJoinTypeEnum.PASSWORD.getType().equals(meetingInfo.getJoinType()) 
                && !meetingInfo.getJoinPassword().equals(password)) {
            throw new BusinessException("密码错误");
        }
        
        // 6. 设置 currentMeetingId 到 token
        tokenUserInfoDto.setCurrentMeetingId(meetingId);
        redisComponent.updateTokenUserInfo(tokenUserInfoDto);
    }

    /**
     * 邀请联系人加入会议
     * 
     * 流程：
     * 1. 验证邀请者是否在会议中
     * 2. 验证被邀请者是否是邀请者的好友
     * 3. 跳过已在会议中的用户
     * 4. 保存邀请信息到 Redis
     * 5. 发送 WebSocket 邀请消息给被邀请者
     */
    @Override
    public void inviteContact(TokenUserInfoDto tokenUserInfoDto, List<String> contactsId) {
        if (contactsId == null || contactsId.isEmpty()) {
            throw new BusinessException("邀请列表不能为空");
        }
        
        String currentMeetingId = tokenUserInfoDto.getCurrentMeetingId();
        if (StringUtils.isEmpty(currentMeetingId)) {
            throw new BusinessException("你当前不在会议中");
        }

        String userId = tokenUserInfoDto.getUserId();

        // 获取当前用户的所有正常状态的联系人ID
        Set<String> myContactIds = userContactMapper.selectNormalContactsByUserId(userId)
                .stream()
                .map(UserContact::getContactId)
                .collect(Collectors.toSet());

        // 验证并过滤出有效的联系人
        for (String contactId : contactsId) {
            if (!myContactIds.contains(contactId)) {
                throw new BusinessException("用户 " + contactId + " 不是您的好友");
            }
        }

        // 获取会议信息
        MeetingInfo meetingInfo = meetingInfoMapper.selectById(currentMeetingId);
        if (meetingInfo == null) {
            throw new BusinessException("会议不存在");
        }
        if (MeetingStatusEnum.FINISHED.getStatus().equals(meetingInfo.getStatus())) {
            throw new BusinessException("会议已结束");
        }
        
        // 邀请每个联系人
        for (String contactId : contactsId) {
            // 检查用户是否已在会议中
            MeetingMemberDto meetingMemberDto = redisComponent.getMeetingMember(currentMeetingId, contactId);
            if (meetingMemberDto != null && MeetingMemberStatusEnum.NORMAL.getStatus().equals(meetingMemberDto.getStatus())) {
                // 用户已在会议中，跳过
                continue;
            }
            
            // 检查用户是否被拉黑
            MeetingMember dbMember = meetingMemberMapper.selectByMeetingIdAndUserId(currentMeetingId, contactId);
            if (dbMember != null && MeetingMemberStatusEnum.BLACKLIST.getStatus().equals(dbMember.getStatus())) {
                // 用户被拉黑，跳过
                continue;
            }
            
            // 保存邀请信息到 Redis
            redisComponent.addInviteInfo(currentMeetingId, contactId);
            
            // 构建邀请消息
            MeetingInviteDto meetingInviteDto = new MeetingInviteDto();
            meetingInviteDto.setMeetingId(currentMeetingId);
            meetingInviteDto.setMeetingName(meetingInfo.getMeetingName());
            meetingInviteDto.setInviteUserName(tokenUserInfoDto.getNickName());
            
            // 发送 WebSocket 消息给被邀请者
            MessageSendDto<MeetingInviteDto> messageSendDto = new MessageSendDto<>();
            messageSendDto.setMessageType(MessageTypeEnum.INVITE_MESSAGE_MEETING.getType());
            messageSendDto.setMeetingId(currentMeetingId);
            messageSendDto.setMessageSendToType(MessageSendToTypeEnum.USER.getType());
            messageSendDto.setSendUserId(userId);
            messageSendDto.setReceiveUserId(contactId);
            messageSendDto.setMessageContent(meetingInviteDto);
            
            channelContextUtils.sendMessage(messageSendDto);
        }
    }
    
    /**
     * 接受会议邀请
     * 
     * 流程：
     * 1. 验证邀请是否存在且有效
     * 2. 验证会议是否存在且进行中
     * 3. 检查用户是否有其他未结束的会议
     * 4. 检查用户是否被拉黑
     * 5. 设置 currentMeetingId 到 token（类似 preJoinMeeting）
     * 6. 删除邀请信息
     * 
     * 注意：被邀请用户不需要输入密码，直接加入
     */
    @Override
    public void acceptInvite(TokenUserInfoDto tokenUserInfoDto, String meetingId) {
        String userId = tokenUserInfoDto.getUserId();
        
        // 1. 验证邀请是否存在
        String redisMeetingId = redisComponent.getInviteInfo(meetingId, userId);
        if (redisMeetingId == null) {
            throw new BusinessException("邀请已过期或您不在邀请名单中");
        }
        
        // 2. 验证会议是否存在且进行中
        MeetingInfo meetingInfo = meetingInfoMapper.selectById(meetingId);
        if (meetingInfo == null) {
            throw new BusinessException("会议不存在");
        }
        if (MeetingStatusEnum.FINISHED.getStatus().equals(meetingInfo.getStatus())) {
            throw new BusinessException("会议已结束");
        }
        
        // 3. 检查用户是否有其他未结束的会议
        if (StringUtils.isNotEmpty(tokenUserInfoDto.getCurrentMeetingId()) 
                && !meetingId.equals(tokenUserInfoDto.getCurrentMeetingId())) {
            throw new BusinessException("你有未结束的会议");
        }
        
        // 4. 检查用户是否被拉黑
        checkMeetingJoin(meetingId, userId);
        
        // 5. 设置 currentMeetingId 到 token（类似 preJoinMeeting 的效果）
        // 这样用户后续调用 joinMeeting 时可以直接加入
        tokenUserInfoDto.setCurrentMeetingId(meetingId);
        redisComponent.updateTokenUserInfo(tokenUserInfoDto);
        
        // 6. 删除邀请信息（一次性使用）
        redisComponent.removeInviteInfo(meetingId, userId);
    }



}

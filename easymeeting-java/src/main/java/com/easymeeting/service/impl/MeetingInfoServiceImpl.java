package com.easymeeting.service.impl;

import com.easymeeting.dto.*;
import com.easymeeting.entity.MeetingInfo;
import com.easymeeting.entity.MeetingMember;
import com.easymeeting.entity.MeetingReserve;
import com.easymeeting.enums.*;
import com.easymeeting.exception.BusinessException;
import com.easymeeting.mapper.MeetingInfoMapper;
import com.easymeeting.mapper.MeetingMemberMapper;
import com.easymeeting.mapper.MeetingReserveMapper;
import com.easymeeting.mapper.MeetingReserveMemberMapper;
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
    @Override
    public void joinMeeting(JoinMeetingDto joinMeetingDto) {
        if(StringUtils.isEmpty(joinMeetingDto.getMeetingId())) {
            throw new BusinessException("没有此会议");
        }
        MeetingInfo meetingInfo = meetingInfoMapper.selectById(joinMeetingDto.getMeetingId());
        if(meetingInfo == null||MeetingStatusEnum.FINISHED.getStatus().equals(meetingInfo.getStatus())) {
            throw new BusinessException("会议已结束");
        }
        //校验用户
        checkMeetingJoin(joinMeetingDto.getMeetingId(),joinMeetingDto.getUserId());

        //加入成员
         MemberTypeEnum memberTypeEnum =meetingInfo.getCreateUserId().equals(joinMeetingDto.getUserId())
                ?MemberTypeEnum.COMPERE:MemberTypeEnum.NORMAL;
        addMeetingMember(joinMeetingDto.getMeetingId(),joinMeetingDto.getUserId(), joinMeetingDto.getNickName(),memberTypeEnum.getType() );
        //加入会议
        addToMeeting(joinMeetingDto.getMeetingId(), joinMeetingDto.getUserId(), joinMeetingDto.getNickName(), joinMeetingDto.getSex(), memberTypeEnum.getType(),joinMeetingDto.getVideoOpen());
        //加入ws房间
        channelContextUtils.joinMeetingRoom(joinMeetingDto.getMeetingId(), channelContextUtils.getChannel(joinMeetingDto.getUserId()));
        //发送ws消息
        MeetingJoinDto meetingJoinDto=new MeetingJoinDto();
        meetingJoinDto.setNewMember(redisComponent.getMeetingMember(joinMeetingDto.getMeetingId(), joinMeetingDto.getUserId()));
        meetingJoinDto.setMeetingMemberList(redisComponent.getMeetingMemberList(joinMeetingDto.getMeetingId()));

        MessageSendDto<MeetingJoinDto> messageSendDto=new MessageSendDto<MeetingJoinDto>();
        messageSendDto.setMessageType(MessageTypeEnum.ADD_MEETING_ROOM.getType());
        messageSendDto.setMeetingId(joinMeetingDto.getMeetingId());
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


}

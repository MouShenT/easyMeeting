package com.easymeeting.service.impl;

import com.easymeeting.dto.*;
import com.easymeeting.entity.MeetingInfo;
import com.easymeeting.entity.MeetingMember;
import com.easymeeting.enums.*;
import com.easymeeting.exception.BusinessException;
import com.easymeeting.mapper.MeetingInfoMapper;
import com.easymeeting.mapper.MeetingMemberMapper;
import com.easymeeting.redis.RedisComponent;
import com.easymeeting.service.MeetingInfoService;
import com.easymeeting.utils.StringUtils;
import com.easymeeting.vo.PageResult;
import com.easymeeting.websocket.ChannelContextUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

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
            meetingMemberMapper.updateByUserId(meetingMember);
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
    private void checkMeetingJoin(String meetingId,String userId) {
        MeetingMemberDto meetingMemberDto=redisComponent.getMeetingMember(meetingId,userId);
        if(meetingMemberDto!=null&&MeetingMemberStatusEnum.BLACKLIST.getStatus().equals(meetingMemberDto.getMemberType())) {
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

}

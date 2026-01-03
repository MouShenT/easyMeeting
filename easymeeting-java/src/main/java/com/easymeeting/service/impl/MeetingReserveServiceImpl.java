package com.easymeeting.service.impl;

import com.easymeeting.dto.TokenUserInfoDto;
import com.easymeeting.entity.MeetingInfo;
import com.easymeeting.entity.MeetingReserve;
import com.easymeeting.entity.MeetingReserveMember;
import com.easymeeting.enums.MeetingReserveStatusEnum;
import com.easymeeting.exception.BusinessException;
import com.easymeeting.mapper.MeetingReserveMapper;
import com.easymeeting.mapper.MeetingReserveMemberMapper;
import com.easymeeting.redis.RedisComponent;
import com.easymeeting.service.MeetingInfoService;
import com.easymeeting.service.MeetingReserveService;
import com.easymeeting.utils.StringUtils;
import com.easymeeting.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MeetingReserveServiceImpl implements MeetingReserveService {

    private final MeetingReserveMapper meetingReserveMapper;
    private final MeetingReserveMemberMapper meetingReserveMemberMapper;
    private final MeetingInfoService meetingInfoService;
    private final RedisComponent redisComponent;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MeetingReserve createReserve(MeetingReserve meetingReserve, List<String> inviteUserIds) {
        // 生成会议ID
        meetingReserve.setMeetingId(StringUtils.generateMeetingNo());
        meetingReserve.setCreateTime(LocalDateTime.now());
        meetingReserve.setStatus(MeetingReserveStatusEnum.NO_START.getStatus()); // 待开始

        meetingReserveMapper.insert(meetingReserve);

        // 添加邀请成员
        if (inviteUserIds != null && !inviteUserIds.isEmpty()) {
            List<MeetingReserveMember> members = new ArrayList<>();
            for (String userId : inviteUserIds) {
                MeetingReserveMember member = new MeetingReserveMember();
                member.setMeetingId(meetingReserve.getMeetingId());
                member.setInviteUserId(userId);
                members.add(member);
            }
            meetingReserveMemberMapper.batchInsert(members);
        }

        log.info("创建预约会议成功: meetingId={}, meetingName={}", 
                meetingReserve.getMeetingId(), meetingReserve.getMeetingName());
        return meetingReserve;
    }

    @Override
    public MeetingReserve updateReserve(MeetingReserve meetingReserve) {
        MeetingReserve existing = meetingReserveMapper.selectById(meetingReserve.getMeetingId());
        if (existing == null) {
            throw new BusinessException("预约会议不存在");
        }
        meetingReserveMapper.updateById(meetingReserve);
        return meetingReserveMapper.selectById(meetingReserve.getMeetingId());
    }

    @Override
    public MeetingReserve getReserveById(String meetingId) {
        return meetingReserveMapper.selectById(meetingId);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReserve(String meetingId, String userId) {
        MeetingReserve existing = meetingReserveMapper.selectById(meetingId);
        if (existing == null) {
            throw new BusinessException("预约会议不存在");
        }
        if (!existing.getCreateUserId().equals(userId)) {
            throw new BusinessException("只有创建者才能删除预约会议");
        }
        // 先删除邀请成员
        meetingReserveMemberMapper.deleteByMeetingId(meetingId);
        // 再删除预约会议
        meetingReserveMapper.deleteById(meetingId);
        log.info("删除预约会议成功: meetingId={}", meetingId);
    }

    @Override
    public void cancelReserve(String meetingId, String userId) {
        MeetingReserve existing = meetingReserveMapper.selectById(meetingId);
        if (existing == null) {
            throw new BusinessException("预约会议不存在");
        }
        if (!existing.getCreateUserId().equals(userId)) {
            throw new BusinessException("只有创建者才能取消预约会议");
        }
        existing.setStatus(MeetingReserveStatusEnum.CANCELLED.getStatus()); // 已取消
        meetingReserveMapper.updateById(existing);
        log.info("取消预约会议成功: meetingId={}", meetingId);
    }

    @Override
    public PageResult<MeetingReserve> loadCreatedReserves(String userId, Integer pageNo, Integer pageSize) {
        int offset = (pageNo - 1) * pageSize;
        List<MeetingReserve> list = meetingReserveMapper.selectCreatedReserves(userId, offset, pageSize);
        Long total = meetingReserveMapper.countCreatedReserves(userId);
        return new PageResult<>(pageNo, pageSize, total, list);
    }

    @Override
    public PageResult<MeetingReserve> loadInvitedReserves(String userId, Integer pageNo, Integer pageSize) {
        int offset = (pageNo - 1) * pageSize;
        List<MeetingReserve> list = meetingReserveMapper.selectInvitedReserves(userId, offset, pageSize);
        Long total = meetingReserveMapper.countInvitedReserves(userId);
        return new PageResult<>(pageNo, pageSize, total, list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addInviteMembers(String meetingId, List<String> inviteUserIds) {
        MeetingReserve existing = meetingReserveMapper.selectById(meetingId);
        if (existing == null) {
            throw new BusinessException("预约会议不存在");
        }
        if (inviteUserIds != null && !inviteUserIds.isEmpty()) {
            List<MeetingReserveMember> members = new ArrayList<>();
            for (String userId : inviteUserIds) {
                // 检查是否已存在
                MeetingReserveMember existingMember = meetingReserveMemberMapper
                        .selectByMeetingIdAndUserId(meetingId, userId);
                if (existingMember == null) {
                    MeetingReserveMember member = new MeetingReserveMember();
                    member.setMeetingId(meetingId);
                    member.setInviteUserId(userId);
                    members.add(member);
                }
            }
            if (!members.isEmpty()) {
                meetingReserveMemberMapper.batchInsert(members);
            }
        }
    }

    @Override
    public void removeInviteMember(String meetingId, String inviteUserId) {
        meetingReserveMemberMapper.deleteByMeetingIdAndUserId(meetingId, inviteUserId);
    }

    @Override
    public List<MeetingReserveMember> getInviteMembers(String meetingId) {
        return meetingReserveMemberMapper.selectByMeetingId(meetingId);
    }

    @Override
    public List<MeetingReserve> loadCreatedReservesByStatus(String userId, Integer status) {
        return meetingReserveMapper.selectCreatedReservesByStatus(userId, status);
    }

    @Override
    public List<MeetingReserve> loadTodayReserves(String userId) {
        return meetingReserveMapper.selectTodayReserves(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String startReserveMeeting(String reserveId, TokenUserInfoDto currentUser) {
        // 1. 查询预约会议
        MeetingReserve reserve = meetingReserveMapper.selectById(reserveId);
        if (reserve == null) {
            throw new BusinessException("预约会议不存在");
        }

        // 2. 验证是否为创建人
        if (!reserve.getCreateUserId().equals(currentUser.getUserId())) {
            throw new BusinessException("只有创建人才能开始会议");
        }

        // 3. 如果已有 realMeetingId，直接返回（幂等性）
        if (StringUtils.isNotEmpty(reserve.getRealMeetingId())) {
            log.info("预约会议已开始，返回已有的 realMeetingId: {}", reserve.getRealMeetingId());
            // 设置 currentMeetingId 到 token
            currentUser.setCurrentMeetingId(reserve.getRealMeetingId());
            currentUser.setCurrentNickName(currentUser.getNickName());
            redisComponent.updateTokenUserInfo(currentUser);
            return reserve.getRealMeetingId();
        }

        // 4. 创建实际会议（复用 quickMeeting 逻辑）
        MeetingInfo meetingInfo = new MeetingInfo();
        meetingInfo.setMeetingName(reserve.getMeetingName());
        meetingInfo.setJoinType(reserve.getJoinType());
        meetingInfo.setJoinPassword(reserve.getJoinPassword());
        meetingInfo.setCreateUserId(currentUser.getUserId());
        
        meetingInfoService.quickMeeting(meetingInfo, currentUser.getNickName());
        String realMeetingId = meetingInfo.getMeetingId();

        // 5. 更新预约会议的 realMeetingId 和 status
        reserve.setRealMeetingId(realMeetingId);
        reserve.setStatus(MeetingReserveStatusEnum.RUNNING.getStatus()); // 进行中
        meetingReserveMapper.updateById(reserve);

        // 6. 设置 currentMeetingId 到 token
        currentUser.setCurrentMeetingId(realMeetingId);
        currentUser.setCurrentNickName(currentUser.getNickName());
        redisComponent.updateTokenUserInfo(currentUser);

        log.info("开始预约会议成功: reserveId={}, realMeetingId={}", reserveId, realMeetingId);
        return realMeetingId;
    }

    @Override
    public String joinReserveMeeting(String reserveId, TokenUserInfoDto currentUser) {
        // 1. 查询预约会议
        MeetingReserve reserve = meetingReserveMapper.selectById(reserveId);
        if (reserve == null) {
            throw new BusinessException("预约会议不存在");
        }

        // 2. 验证会议是否已开始
        if (StringUtils.isEmpty(reserve.getRealMeetingId())) {
            throw new BusinessException("会议尚未开始，请等待创建人开始会议");
        }

        // 3. 设置 currentMeetingId 到 token
        currentUser.setCurrentMeetingId(reserve.getRealMeetingId());
        currentUser.setCurrentNickName(currentUser.getNickName());
        redisComponent.updateTokenUserInfo(currentUser);

        // 4. 返回实际会议ID
        return reserve.getRealMeetingId();
    }
}

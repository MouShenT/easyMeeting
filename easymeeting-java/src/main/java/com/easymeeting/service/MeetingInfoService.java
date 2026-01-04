package com.easymeeting.service;

import com.easymeeting.dto.JoinMeetingDto;
import com.easymeeting.dto.TokenUserInfoDto;
import com.easymeeting.entity.MeetingInfo;
import com.easymeeting.entity.UserContact;
import com.easymeeting.enums.MeetingMemberStatusEnum;
import com.easymeeting.interceptor.TokenInterceptor;
import com.easymeeting.vo.PageResult;
import java.util.List;

public interface MeetingInfoService {

    MeetingInfo createMeeting(MeetingInfo meetingInfo);

    MeetingInfo updateMeeting(MeetingInfo meetingInfo);

    MeetingInfo getMeetingById(String meetingId);

    MeetingInfo getMeetingByNo(String meetingNo);

    List<MeetingInfo> getMeetingsByUserId(String userId);

    List<MeetingInfo> listMeetings(Integer status);

    void deleteMeeting(String meetingId);

    /**
     * 加载所有历史会议（我创建的 + 我参加的）
     */
    PageResult<MeetingInfo> loadAllMeetings(String userId, Integer pageNo, Integer pageSize);

    /**
     * 加载我创建的会议
     */
    PageResult<MeetingInfo> loadCreatedMeetings(String userId, Integer pageNo, Integer pageSize);

    /**
     * 加载我参加的会议（不包括我创建的）
     */
    PageResult<MeetingInfo> loadJoinedMeetings(String userId, Integer pageNo, Integer pageSize);

    void quickMeeting(MeetingInfo meetingInfo,String nickName);
    void joinMeeting(JoinMeetingDto joinMeetingDto);
    String preJoinMeeting(String meetingNo, TokenUserInfoDto tokenUserInfoDto, String password);
    void exitMeetingRoom(TokenUserInfoDto tokenUserInfoDto, MeetingMemberStatusEnum statusEnum);

    void forceExitMeetingRoom(TokenUserInfoDto tokenUserInfoDto, String userId,MeetingMemberStatusEnum meetingMemberStatusEnum);
    void finishMeeting(String meetingId,String userId);
    void reserveJoinMeeting(String meetingId,TokenUserInfoDto tokenUserInfoDto,String password);
    void inviteContact(TokenUserInfoDto tokenUserInfoDto, List<String> contactsId);
    void acceptInvite(TokenUserInfoDto tokenUserInfoDto, String meetingId);
}

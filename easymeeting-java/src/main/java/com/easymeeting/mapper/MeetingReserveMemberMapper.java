package com.easymeeting.mapper;

import com.easymeeting.entity.MeetingReserveMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface MeetingReserveMemberMapper {

    int insert(MeetingReserveMember member);

    int batchInsert(@Param("list") List<MeetingReserveMember> members);

    MeetingReserveMember selectByMeetingIdAndUserId(@Param("meetingId") String meetingId, 
                                                     @Param("inviteUserId") String inviteUserId);

    List<MeetingReserveMember> selectByMeetingId(@Param("meetingId") String meetingId);

    List<MeetingReserveMember> selectByUserId(@Param("inviteUserId") String inviteUserId);

    int deleteByMeetingId(@Param("meetingId") String meetingId);

    int deleteByMeetingIdAndUserId(@Param("meetingId") String meetingId, 
                                    @Param("inviteUserId") String inviteUserId);
}

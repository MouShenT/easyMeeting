package com.easymeeting.mapper;

import com.easymeeting.entity.MeetingMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface MeetingMemberMapper {

    int insert(MeetingMember meetingMember);

    int updateByMeetingIdAndUserId(MeetingMember meetingMember);

    MeetingMember selectByUserId(@Param("userId") String userId);

    MeetingMember selectByMeetingIdAndUserId(@Param("meetingId") String meetingId, @Param("userId") String userId);

    List<MeetingMember> selectByMeetingId(@Param("meetingId") String meetingId);

    int deleteByUserId(@Param("userId") String userId);

    int deleteByMeetingId(@Param("meetingId") String meetingId);
}

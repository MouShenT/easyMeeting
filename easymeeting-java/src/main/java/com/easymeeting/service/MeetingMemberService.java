package com.easymeeting.service;

import com.easymeeting.entity.MeetingMember;
import java.util.List;

public interface MeetingMemberService {

    MeetingMember addMember(MeetingMember meetingMember);

    MeetingMember updateMember(MeetingMember meetingMember);

    MeetingMember getMemberByUserId(String userId);

    MeetingMember getMember(String meetingId, String userId);

    List<MeetingMember> getMembersByMeetingId(String meetingId);

    void removeMemberByUserId(String userId);

    void removeMembersByMeetingId(String meetingId);
}

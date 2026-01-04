package com.easymeeting.dto;

import lombok.Data;

@Data
public class MeetingInviteDto {
    private String meetingName;
    private String inviteUserName;
    private String meetingId;
}

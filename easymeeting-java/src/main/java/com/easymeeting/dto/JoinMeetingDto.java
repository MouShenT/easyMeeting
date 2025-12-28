package com.easymeeting.dto;

import lombok.Data;

@Data
public class JoinMeetingDto {
    String meetingId;
    String userId;
    String nickName;
    Integer sex;
    Boolean videoOpen;
}

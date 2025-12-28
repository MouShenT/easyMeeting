package com.easymeeting.dto;

import lombok.Data;

@Data
public class MeetingMemberDto {
    private String userId;
    private String nickName;
    private long joinTime;
    private Integer memberType;
    private Integer status;
    private Boolean videoOpen;
    private Integer sex;
}

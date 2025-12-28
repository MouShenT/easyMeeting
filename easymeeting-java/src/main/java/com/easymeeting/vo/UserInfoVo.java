package com.easymeeting.vo;

import lombok.Data;

@Data
public class UserInfoVo {

    private String userId;

    private String nickName;
    private Integer sex;

    private String meetingNo;
    private String token;
    private Boolean isAdmin;
}

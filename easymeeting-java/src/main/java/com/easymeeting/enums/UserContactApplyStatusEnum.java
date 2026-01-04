package com.easymeeting.enums;

import com.easymeeting.utils.StringUtils;

public enum UserContactApplyStatusEnum {
    INIT(0,"待处理"),
    PASS(1,"已同意"),
    REJECT(2,"已拒绝"),
    BLACKLIST(3,"已拉黑");
    private Integer status;
    private String desc;
    UserContactApplyStatusEnum(Integer status, String desc) {
        this.status = status;
        this.desc = desc;
    }
    public static UserContactApplyStatusEnum getUserContactApplyStatusEnum(String status) {
        try {
            if(StringUtils.isEmpty(status)){
                return null;
            }
            return UserContactApplyStatusEnum.valueOf(status.toUpperCase());
        }catch (Exception e){
            return null;
        }
    }
    public static UserContactApplyStatusEnum getUserContactApplyStatusEnum(Integer status) {
        UserContactApplyStatusEnum[] userContactApplyStatusEnums = UserContactApplyStatusEnum.values();
        for (UserContactApplyStatusEnum userContactApplyStatusEnum : userContactApplyStatusEnums) {
            if(userContactApplyStatusEnum.getStatus().equals(status)) {
                return userContactApplyStatusEnum;
            }
        }
        return null;
    }
    public Integer getStatus() {
        return status;
    }

    public String getDesc() {
        return desc;
    }

}

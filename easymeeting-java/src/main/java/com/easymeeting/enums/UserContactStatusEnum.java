package com.easymeeting.enums;

public enum UserContactStatusEnum {
    NORMAL(0,"好友"),
    DEL(1,"删除"),
    BLACK(2,"拉黑");
    private Integer status;
    private String desc;
    UserContactStatusEnum(Integer status, String desc) {
        this.status = status;
        this.desc = desc;

    }
    public static UserContactStatusEnum getUserContactStatusEnum(Integer status) {
        UserContactStatusEnum[] values = UserContactStatusEnum.values();
        for (UserContactStatusEnum value : values) {
            if (value.getStatus().equals(status)) {
                return value;
            }
        }
        return null;
    }
    public Integer getStatus() {
        return status;
    }    public String getDesc() {
        return desc;
    }

}

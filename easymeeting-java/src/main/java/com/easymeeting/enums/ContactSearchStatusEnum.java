package com.easymeeting.enums;

public enum ContactSearchStatusEnum {
    SELF(-1, "自己"),
    NOT_FRIEND(0, "非好友，可申请"),
    FRIEND(1, "已是好友"),
    PENDING(2, "申请待处理"),
    BLACKLISTED(3, "已被拉黑"),
    BE_FRIEND(4, "对方已添加我");

    private final Integer status;
    private final String desc;

    ContactSearchStatusEnum(Integer status, String desc) {
        this.status = status;
        this.desc = desc;
    }

    public Integer getStatus() {
        return status;
    }

    public String getDesc() {
        return desc;
    }
}

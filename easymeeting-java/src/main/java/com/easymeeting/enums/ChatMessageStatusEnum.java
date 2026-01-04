package com.easymeeting.enums;

public enum ChatMessageStatusEnum {
    SENDING(0,"正在发送"),
    SENDEND(1,"发送完毕");
    private Integer status;
    private String desc;
    ChatMessageStatusEnum(Integer status, String desc) {
        this.status = status;
        this.desc = desc;
    }
    public static ChatMessageStatusEnum getEnumByStatus(Integer status) {
        for (ChatMessageStatusEnum e : ChatMessageStatusEnum.values()) {
            if (e.getStatus().equals(status)) {
                return e;
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

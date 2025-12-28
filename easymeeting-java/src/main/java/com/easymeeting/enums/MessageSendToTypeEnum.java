package com.easymeeting.enums;

public enum MessageSendToTypeEnum {
    USER(0,"个人"),
    GROUP(1,"群");
    private Integer type;
    private String desc;
    MessageSendToTypeEnum(Integer type, String desc) {
        this.type = type;
        this.desc = desc;
    }
    public Integer getType() {
        return type;
    }
    public String getDesc() {
        return desc;
    }
}

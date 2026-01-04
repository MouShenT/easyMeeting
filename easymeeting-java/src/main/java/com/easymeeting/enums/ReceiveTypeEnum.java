package com.easymeeting.enums;

public enum ReceiveTypeEnum {
    ALL(0,"全员"),
    USER(1,"个人");
    private Integer type;
    private String desc;
    public static ReceiveTypeEnum getByType(Integer type) {
        for (ReceiveTypeEnum receiveTypeEnum : ReceiveTypeEnum.values()) {
            if(receiveTypeEnum.getType().equals(type)) {
                return receiveTypeEnum;
            }
        }
        return null;
    }
    ReceiveTypeEnum(Integer type, String desc) {
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

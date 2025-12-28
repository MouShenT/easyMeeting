package com.easymeeting.enums;

public enum MeetingJoinTypeEnum {
    NP_PASSWORD(0,"无需密码"),
    PASSWORD(1,"需要密码");
    private Integer type;
    private String desc;
    MeetingJoinTypeEnum(Integer type, String desc) {
        this.type = type;
        this.desc = desc;
    }
    public static MeetingJoinTypeEnum getByType(Integer type) {
        for (MeetingJoinTypeEnum item : MeetingJoinTypeEnum.values()) {
            if(item.getType().equals(type)) {
                return item;
            }
        }
        return null;
    }
    public Integer getType() {
        return type;
    }
    public String getDesc() {
        return desc;
    }
}

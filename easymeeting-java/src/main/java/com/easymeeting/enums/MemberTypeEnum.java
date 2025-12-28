package com.easymeeting.enums;

public enum MemberTypeEnum {
    NORMAL(0,"普通成员"),
    COMPERE(1,"主持人");
    private Integer type;
    private String desc;
    MemberTypeEnum(Integer type, String desc) {
        this.type = type;
        this.desc = desc;
    }
    public static MemberTypeEnum getMemberTypeEnum(Integer type) {
        for (MemberTypeEnum memberTypeEnum : MemberTypeEnum.values()) {
            if(memberTypeEnum.type.equals(type)){
                return memberTypeEnum;
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

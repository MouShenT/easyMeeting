package com.easymeeting.enums;

public enum UserStatusEnum {
    DISABLE(0,"禁用"),
    ENABLE(1,"启用");
    private Integer status;
    private String desc;


    UserStatusEnum(Integer status, String desc) {
        this.status = status;
        this.desc = desc;
    }
    public  static UserStatusEnum getUserStatusEnum(Integer status){
        for(UserStatusEnum item : UserStatusEnum.values()){
            if(item.getStatus().equals(status)){
                return item;
            }
        }
        return null;
    }
    //valueof()如果填了没有的枚举会报异常，上面values会报null
    public Integer getStatus() {
        return status;
    }
    public String getDesc() {
        return desc;
    }
}

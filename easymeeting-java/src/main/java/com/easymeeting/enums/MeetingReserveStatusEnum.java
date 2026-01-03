package com.easymeeting.enums;

public enum MeetingReserveStatusEnum {
    NO_START(0, "待开始"),
    RUNNING(1, "进行中"),
    FINISHED(2, "已结束"),
    CANCELLED(3, "已取消");

    private Integer status;
    private String desc;

    MeetingReserveStatusEnum(Integer status, String desc) {
        this.status = status;
        this.desc = desc;
    }

    public static MeetingReserveStatusEnum getMeetingStatusEnum(Integer status) {
        for (MeetingReserveStatusEnum item : MeetingReserveStatusEnum.values()) {
            if (item.status.equals(status)) {
                return item;
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

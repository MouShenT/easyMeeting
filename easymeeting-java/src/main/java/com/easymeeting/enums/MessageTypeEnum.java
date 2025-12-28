package com.easymeeting.enums;

public enum MessageTypeEnum {
    INIT(0,"连接ws获取信息"),
    ADD_MEETING_ROOM(1,"加入房间"),
    PEER(2,"发送peer"),
    EXIT_MEETING_ROOM(3,"退出房间"),
    FINIS_MEETING(4,"结束会议"),
    CHAT_TEXT_MESSAGE(5,"文本消息"),
    CHAT_MEDIA_MESSAGE(6,"媒体消息"),
    CHAT_MEDIA_MESSAGE_UPDATE(7,"媒体消息更新"),
    USER_CONTACT_APPLY(8,"好友申请消息"),
    INVITE_MESSAGE_MEETING(9,"邀请入会"),
    FORCE_OFF_LINE(10,"强制下线"),
    MEETING_USER_VIDEO_CHANGE(11,"用户视频改变"),
    // WebRTC 信令类型
    WEBRTC_OFFER(12,"WebRTC Offer"),
    WEBRTC_ANSWER(13,"WebRTC Answer"),
    WEBRTC_ICE_CANDIDATE(14,"ICE Candidate");
    
    private final Integer type;
    private final String desc;
    
    MessageTypeEnum(Integer type, String desc){
        this.type = type;
        this.desc = desc;
    }
    
    /**
     * 根据 type 值获取枚举
     */
    public static MessageTypeEnum getByType(Integer type) {
        if (type == null) {
            return null;
        }
        for (MessageTypeEnum e : values()) {
            if (e.type.equals(type)) {
                return e;
            }
        }
        return null;
    }
    
    public MessageTypeEnum getMesssageType(){
        for(MessageTypeEnum e : MessageTypeEnum.values()){
            if(e.type.equals(this.type)){
                return e;
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

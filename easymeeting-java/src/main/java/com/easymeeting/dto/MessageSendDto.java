package com.easymeeting.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class MessageSendDto<T> implements Serializable {
    private static final long serialVersionUID = -1045752033171142417L;
    //发给人还是群组
    private Integer messageSendToType;
    private String meetingId;
    //消息类型
    private Integer messageType;
    private String sendUserId;
    private String sendUserNickName;
    private T messageContent;
    private String receiveUserId;
    private Long sendTime;
    private Long messageId;
    private Integer status;
    private String fileName;
    private Integer fileType;
    private Long fileSize;

}

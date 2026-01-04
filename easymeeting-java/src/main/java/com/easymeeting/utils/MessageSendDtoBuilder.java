package com.easymeeting.utils;

import com.easymeeting.dto.MessageSendDto;
import com.easymeeting.entity.ChatMessage;
import com.easymeeting.enums.MessageSendToTypeEnum;
import com.easymeeting.enums.MessageTypeEnum;
import com.easymeeting.enums.ReceiveTypeEnum;

/**
 * 消息发送DTO构建器
 * 统一构建 WebSocket 推送的消息对象
 */
public class MessageSendDtoBuilder {

    /**
     * 从会议消息构建推送DTO
     * 根据 receiveType 设置正确的 messageSendToType：
     * - receiveType=0(群发) -> messageSendToType=1(GROUP)
     * - receiveType=1(私聊) -> messageSendToType=0(USER)
     *
     * @param message 会议消息实体
     * @return WebSocket 推送 DTO
     */
    public static MessageSendDto<String> fromChatMessage(ChatMessage message) {
        MessageSendDto<String> dto = new MessageSendDto<>();
        
        // 设置基本消息信息
        dto.setMessageId(message.getMessageId());
        dto.setMeetingId(message.getMeetingId());
        dto.setMessageType(message.getMessageType());
        dto.setSendUserId(message.getSendUserId());
        dto.setSendUserNickName(message.getSendUserNickName());
        dto.setMessageContent(message.getMessageContent());
        dto.setSendTime(message.getSendTime());
        dto.setStatus(message.getStatus());

        // 根据接收类型设置推送目标
        if (ReceiveTypeEnum.USER.getType().equals(message.getReceiveType())) {
            // 私聊消息：发送给指定用户
            dto.setMessageSendToType(MessageSendToTypeEnum.USER.getType());
            dto.setReceiveUserId(message.getReceiveUserId());
        } else {
            // 群发消息：发送给会议房间所有成员
            dto.setMessageSendToType(MessageSendToTypeEnum.GROUP.getType());
        }

        // 媒体消息附加文件信息
        if (MessageTypeEnum.CHAT_MEDIA_MESSAGE.getType().equals(message.getMessageType())) {
            dto.setFileName(message.getFileName());
            dto.setFileType(message.getFileType());
            dto.setFileSize(message.getFileSize());
        }

        return dto;
    }
}

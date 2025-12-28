package com.easymeeting.websocket.handler;

import com.easymeeting.dto.MessageSendDto;
import com.easymeeting.enums.MessageTypeEnum;
import com.easymeeting.websocket.message.MessageHandler;
import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 默认转发处理器
 * 处理不需要特殊业务逻辑的消息，直接转发
 */
@Component
@Order(Integer.MAX_VALUE)  // 最低优先级，作为默认处理器
@Slf4j
@RequiredArgsConstructor
public class DefaultForwardHandler implements MessageTypeHandler {
    
    private final MessageHandler messageHandler;
    
    @Override
    public List<MessageTypeEnum> getSupportedTypes() {
        return Arrays.asList(
            MessageTypeEnum.CHAT_TEXT_MESSAGE,
            MessageTypeEnum.CHAT_MEDIA_MESSAGE,
            MessageTypeEnum.CHAT_MEDIA_MESSAGE_UPDATE,
            MessageTypeEnum.MEETING_USER_VIDEO_CHANGE,
            MessageTypeEnum.INVITE_MESSAGE_MEETING,
            MessageTypeEnum.USER_CONTACT_APPLY,
            MessageTypeEnum.ADD_MEETING_ROOM,
            MessageTypeEnum.PEER
        );
    }
    
    @Override
    public void handle(ChannelHandlerContext ctx, MessageSendDto<?> message) {
        log.debug("默认转发消息: type={}", message.getMessageType());
        messageHandler.sendMessage(message);
    }
}

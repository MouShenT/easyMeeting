package com.easymeeting.websocket.handler;

import com.easymeeting.dto.MessageSendDto;
import com.easymeeting.enums.MessageTypeEnum;
import com.easymeeting.websocket.message.MessageHandler;
import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * WebRTC 信令处理器
 * 处理 Offer/Answer/ICE Candidate 等信令消息
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebRTCSignalingHandler implements MessageTypeHandler {
    
    private final MessageHandler messageHandler;
    
    @Override
    public List<MessageTypeEnum> getSupportedTypes() {
        return Arrays.asList(
            MessageTypeEnum.WEBRTC_OFFER,
            MessageTypeEnum.WEBRTC_ANSWER,
            MessageTypeEnum.WEBRTC_ICE_CANDIDATE
        );
    }
    
    @Override
    public void handle(ChannelHandlerContext ctx, MessageSendDto<?> message) {
        String targetUserId = message.getReceiveUserId();
        
        if (targetUserId == null || targetUserId.isEmpty()) {
            log.warn("WebRTC 信令消息缺少目标用户ID, type={}", message.getMessageType());
            return;
        }
        
        log.info("转发 WebRTC 信令: {} -> {}, type={}", 
            message.getSendUserId(), targetUserId, message.getMessageType());
        
        // 点对点转发
        messageHandler.sendMessage(message);
    }
}

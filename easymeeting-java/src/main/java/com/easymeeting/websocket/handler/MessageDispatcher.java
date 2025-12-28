package com.easymeeting.websocket.handler;

import com.easymeeting.dto.MessageSendDto;
import com.easymeeting.dto.TokenUserInfoDto;
import com.easymeeting.websocket.ChannelContextUtils;
import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 消息分发器
 * 根据消息类型路由到对应的处理器
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MessageDispatcher {
    
    private final HandlerRegistry handlerRegistry;
    private final ChannelContextUtils channelContextUtils;
    
    /**
     * 分发消息到对应的处理器
     */
    public void dispatch(ChannelHandlerContext ctx, MessageSendDto<?> message) {
        // 设置发送者信息
        TokenUserInfoDto userInfo = channelContextUtils.getUserInfo(ctx.channel());
        if (userInfo != null) {
            message.setSendUserId(userInfo.getUserId());
            message.setSendUserNickName(userInfo.getNickName());
        }
        
        // 查找处理器
        Integer messageType = message.getMessageType();
        MessageTypeHandler handler = handlerRegistry.getHandler(messageType)
            .orElse(handlerRegistry.getDefaultHandler());
        
        if (handler != null) {
            log.debug("分发消息类型 {} 到处理器 {}", messageType, handler.getClass().getSimpleName());
            handler.handle(ctx, message);
        } else {
            log.warn("未找到消息类型 {} 的处理器，且无默认处理器", messageType);
        }
    }
}

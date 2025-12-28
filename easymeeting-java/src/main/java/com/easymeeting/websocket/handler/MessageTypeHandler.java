package com.easymeeting.websocket.handler;

import com.easymeeting.dto.MessageSendDto;
import com.easymeeting.enums.MessageTypeEnum;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

/**
 * 消息类型处理器接口
 * 每个实现类负责处理特定类型的消息
 */
public interface MessageTypeHandler {
    
    /**
     * 获取此处理器支持的消息类型列表
     * @return 支持的消息类型
     */
    List<MessageTypeEnum> getSupportedTypes();
    
    /**
     * 处理消息
     * @param ctx Netty 通道上下文
     * @param message 消息内容
     */
    void handle(ChannelHandlerContext ctx, MessageSendDto<?> message);
}

package com.easymeeting.websocket.message;

import com.easymeeting.dto.MessageSendDto;

/**
 * 消息处理器接口
 * 支持多种实现：Local（单机）、Redis、RabbitMQ、Kafka
 */
public interface MessageHandler {
    
    /**
     * 开始监听消息
     * 应用启动时调用
     */
    void listenMessage();
    
    /**
     * 发送消息
     * @param messageSendDto 消息内容
     */
    void sendMessage(MessageSendDto messageSendDto);
}

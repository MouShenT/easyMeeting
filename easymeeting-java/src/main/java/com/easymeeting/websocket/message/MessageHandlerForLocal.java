package com.easymeeting.websocket.message;

import com.easymeeting.dto.MessageSendDto;
import com.easymeeting.entity.constants.Constants;
import com.easymeeting.websocket.ChannelContextUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 本地消息处理器（单机模式）
 * 直接在本机内存中查找用户并发送消息
 * 
 * 当配置 messaging.handle.channel=local 或未配置时启用
 */
@Component
@ConditionalOnProperty(
    name = Constants.MESSAGE_HANDLE_CHANNEL, 
    havingValue = "local", 
    matchIfMissing = true  // 如果没配置，默认使用这个
)
@Slf4j
@RequiredArgsConstructor
public class MessageHandlerForLocal implements MessageHandler {

    private final ChannelContextUtils channelContextUtils;

    @PostConstruct
    public void init() {
        listenMessage();
    }

    @Override
    public void listenMessage() {
        // 单机模式不需要监听外部消息
        log.info("本地消息处理器已启动（单机模式）");
    }

    @Override
    public void sendMessage(MessageSendDto messageSendDto) {
        // 直接在本机发送
        channelContextUtils.sendMessage(messageSendDto);
    }
}

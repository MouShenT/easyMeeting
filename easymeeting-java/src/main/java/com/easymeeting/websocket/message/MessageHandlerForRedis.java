package com.easymeeting.websocket.message;

import com.alibaba.fastjson.JSONObject;
import com.easymeeting.dto.MessageSendDto;
import com.easymeeting.entity.constants.Constants;
import com.easymeeting.websocket.ChannelContextUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Redis 消息处理器（集群模式）
 * 使用 Redis Pub/Sub 实现跨服务器消息传递
 * 
 * 当配置 messaging.handle.channel=redis 时启用
 */
@Component
@ConditionalOnProperty(name = Constants.MESSAGE_HANDLE_CHANNEL, havingValue = Constants.MESSAGE_CHANNEL_REDIS)
@Slf4j
@RequiredArgsConstructor
public class MessageHandlerForRedis implements MessageHandler {

    private final RedissonClient redissonClient;
    private final ChannelContextUtils channelContextUtils;
    
    private int listenerId;

    /**
     * 应用启动后自动开始监听
     */
    @PostConstruct
    public void init() {
        listenMessage();
        log.info("Redis 消息监听器已启动，Topic: {}", Constants.REDIS_CHANNEL_MESSAGE);
    }

    @Override
    public void listenMessage() {
        RTopic topic = redissonClient.getTopic(Constants.REDIS_CHANNEL_MESSAGE);
        
        // 添加监听器
        listenerId = topic.addListener(MessageSendDto.class, (channel, sendDto) -> {
            log.info("Redis 收到消息：{}", JSONObject.toJSONString(sendDto));
            // 在本机尝试发送消息
            channelContextUtils.sendMessage(sendDto);
        });
    }

    @Override
    public void sendMessage(MessageSendDto messageSendDto) {
        RTopic topic = redissonClient.getTopic(Constants.REDIS_CHANNEL_MESSAGE);
        long receiversCount = topic.publish(messageSendDto);
        log.info("消息已发布到 Redis，接收者数量: {}", receiversCount);
    }

    @PreDestroy
    public void destroy() {
        RTopic topic = redissonClient.getTopic(Constants.REDIS_CHANNEL_MESSAGE);
        topic.removeListener(listenerId);
        log.info("Redis 消息监听器已关闭");
    }
}

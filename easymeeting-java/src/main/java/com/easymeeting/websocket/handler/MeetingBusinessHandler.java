package com.easymeeting.websocket.handler;

import com.easymeeting.dto.MessageSendDto;
import com.easymeeting.enums.MessageTypeEnum;
import com.easymeeting.redis.RedisComponent;
import com.easymeeting.websocket.message.MessageHandler;
import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 会议业务处理器
 * 处理需要业务逻辑的会议相关消息
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MeetingBusinessHandler implements MessageTypeHandler {
    
    private final MessageHandler messageHandler;
    private final RedisComponent redisComponent;
    
    @Override
    public List<MessageTypeEnum> getSupportedTypes() {
        return Arrays.asList(
            MessageTypeEnum.FINIS_MEETING,
            MessageTypeEnum.EXIT_MEETING_ROOM
        );
    }
    
    @Override
    public void handle(ChannelHandlerContext ctx, MessageSendDto<?> message) {
        MessageTypeEnum type = MessageTypeEnum.getByType(message.getMessageType());
        if (type == null) {
            log.warn("未知的消息类型: {}", message.getMessageType());
            return;
        }
        
        switch (type) {
            case FINIS_MEETING:
                handleFinishMeeting(message);
                break;
            case EXIT_MEETING_ROOM:
                handleExitMeeting(message);
                break;
            default:
                log.warn("未处理的会议消息类型: {}", type);
        }
    }
    
    /**
     * 处理结束会议
     */
    private void handleFinishMeeting(MessageSendDto<?> message) {
        String meetingId = message.getMeetingId();
        log.info("处理结束会议: meetingId={}, userId={}", meetingId, message.getSendUserId());
        
        // TODO: 更新数据库中的会议状态
        // meetingService.finishMeeting(meetingId);
        
        // 清理 Redis 中的会议数据
        redisComponent.removeMeetingMembers(meetingId);
        
        // 广播给所有参会者
        messageHandler.sendMessage(message);
    }
    
    /**
     * 处理退出会议
     */
    private void handleExitMeeting(MessageSendDto<?> message) {
        String meetingId = message.getMeetingId();
        String userId = message.getSendUserId();
        log.info("处理退出会议: meetingId={}, userId={}", meetingId, userId);
        
        // 从 Redis 移除成员
        redisComponent.removeMeetingMember(meetingId, userId);
        
        // 广播给其他参会者
        messageHandler.sendMessage(message);
    }
}

package com.easymeeting.websocket.handler;

import com.easymeeting.dto.MessageSendDto;
import com.easymeeting.dto.TokenUserInfoDto;
import com.easymeeting.entity.MeetingInfo;
import com.easymeeting.enums.MeetingMemberStatusEnum;
import com.easymeeting.enums.MeetingStatusEnum;
import com.easymeeting.enums.MessageTypeEnum;
import com.easymeeting.redis.RedisComponent;
import com.easymeeting.service.MeetingInfoService;
import com.easymeeting.websocket.ChannelContextUtils;
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
    private final MeetingInfoService meetingInfoService;
    private final ChannelContextUtils channelContextUtils;
    
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
                handleFinishMeeting(ctx, message);
                break;
            case EXIT_MEETING_ROOM:
                handleExitMeeting(ctx, message);
                break;
            default:
                log.warn("未处理的会议消息类型: {}", type);
        }
    }
    
    /**
     * 处理结束会议
     */
    private void handleFinishMeeting(ChannelHandlerContext ctx, MessageSendDto<?> message) {
        String meetingId = message.getMeetingId();
        log.info("处理结束会议: meetingId={}, userId={}", meetingId, message.getSendUserId());

        // 更新数据库中的会议状态
        MeetingInfo meeting = meetingInfoService.getMeetingById(meetingId);
        if (meeting != null) {
            meeting.setStatus(MeetingStatusEnum.FINISHED.getStatus());
            meetingInfoService.updateMeeting(meeting);
        }

        // 清理 Redis 中的会议数据
        redisComponent.removeMeetingMembers(meetingId);
        
        // 广播给所有参会者
        messageHandler.sendMessage(message);
    }
    
    /**
     * 处理退出会议（通过 WebSocket 消息触发）
     * 复用 Service 层逻辑，确保完整处理
     */
    private void handleExitMeeting(ChannelHandlerContext ctx, MessageSendDto<?> message) {
        String meetingId = message.getMeetingId();
        String userId = message.getSendUserId();
        log.info("处理退出会议(WebSocket): meetingId={}, userId={}", meetingId, userId);
        
        // 从 Channel 获取用户信息
        TokenUserInfoDto tokenUserInfo = channelContextUtils.getUserInfo(ctx.channel());
        if (tokenUserInfo == null) {
            log.warn("无法获取用户信息，userId={}", userId);
            // 降级处理：直接从 Redis 移除
            redisComponent.removeMeetingMember(meetingId, userId);
            messageHandler.sendMessage(message);
            return;
        }
        
        // 复用 Service 层的退出逻辑（包含完整的清理操作）
        meetingInfoService.exitMeetingRoom(tokenUserInfo, MeetingMemberStatusEnum.EXIT_MEETING);
    }
}

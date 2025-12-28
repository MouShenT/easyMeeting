package com.easymeeting.websocket.netty;

import com.alibaba.fastjson.JSON;
import com.easymeeting.dto.MessageSendDto;
import com.easymeeting.dto.TokenUserInfoDto;
import com.easymeeting.enums.MessageSendToTypeEnum;
import com.easymeeting.enums.MessageTypeEnum;
import com.easymeeting.websocket.ChannelContextUtils;
import com.easymeeting.websocket.handler.MessageDispatcher;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
@Slf4j
@RequiredArgsConstructor
public class HandlerWebSocket extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    
    private final ChannelContextUtils channelContextUtils;
    private final MessageDispatcher messageDispatcher;
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("有新的连接加入，ChannelId: {}", ctx.channel().id().asShortText());
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        // 监听 WebSocket 握手完成事件
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            log.info("WebSocket 握手完成，ChannelId: {}", ctx.channel().id().asShortText());
            
            // 获取用户信息（在 HandlerTokenValidation 中已设置）
            TokenUserInfoDto userInfo = channelContextUtils.getUserInfo(ctx.channel());
            if (userInfo != null && userInfo.getCurrentMeetingId() != null) {
                String meetingId = userInfo.getCurrentMeetingId();
                String userId = userInfo.getUserId();
                String nickName = userInfo.getNickName();
                
                log.info("用户 {} 在会议 {} 中，发送成员列表", nickName, meetingId);
                
                // 发送成员列表给房间内所有用户（包括新加入的用户）
                channelContextUtils.sendMeetingMemberUpdate(meetingId, userId, nickName);
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 获取用户信息，用于发送离开通知
        TokenUserInfoDto userInfo = channelContextUtils.getUserInfo(ctx.channel());
        String userId = channelContextUtils.getUserId(ctx.channel());
        String meetingId = userInfo != null ? userInfo.getCurrentMeetingId() : null;
        String nickName = userInfo != null ? userInfo.getNickName() : null;
        
        // 清理连接
        channelContextUtils.removeByChannel(ctx.channel());
        log.info("连接已断开，用户: {}, ChannelId: {}", userId, ctx.channel().id().asShortText());
        
        // 如果用户在会议中，通知其他成员
        if (meetingId != null && userId != null) {
            MessageSendDto<Object> exitMessage = new MessageSendDto<>();
            exitMessage.setMessageType(MessageTypeEnum.EXIT_MEETING_ROOM.getType());
            exitMessage.setMeetingId(meetingId);
            exitMessage.setMessageSendToType(MessageSendToTypeEnum.GROUP.getType());
            exitMessage.setSendUserId(userId);
            exitMessage.setSendUserNickName(nickName);
            channelContextUtils.sendMessage(exitMessage);
            log.info("已发送用户 {} 离开会议 {} 的通知", nickName, meetingId);
        }
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame textWebSocketFrame) throws Exception {
        String message = textWebSocketFrame.text();
        log.info("收到消息：{}", message);
        
        // 心跳消息直接响应，不经过分发器
        if ("ping".equals(message)) {
            ctx.channel().writeAndFlush(new TextWebSocketFrame("pong"));
            return;
        }
        
        try {
            // 解析消息
            MessageSendDto<?> messageSendDto = JSON.parseObject(message, MessageSendDto.class);
            if (messageSendDto == null) {
                log.warn("无法解析消息: {}", message);
                return;
            }
            
            // 使用消息分发器处理（分发器会设置发送者信息并路由到对应处理器）
            messageDispatcher.dispatch(ctx, messageSendDto);
            
        } catch (Exception e) {
            log.error("处理消息失败: {}", message, e);
        }
    }
}

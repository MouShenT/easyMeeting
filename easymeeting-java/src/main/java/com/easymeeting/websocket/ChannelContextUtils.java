package com.easymeeting.websocket;

import com.easymeeting.dto.MessageSendDto;
import com.easymeeting.dto.TokenUserInfoDto;
import com.easymeeting.enums.MessageSendToTypeEnum;
import com.easymeeting.redis.RedisComponent;
import com.easymeeting.utils.StringUtils;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChannelContextUtils {

    private final RedisComponent redisComponent;

    // Channel 上存储 userId 的 AttributeKey（Channel → UserId）
    public static final AttributeKey<String> USER_ID_KEY = AttributeKey.valueOf("userId");

    // Channel 上存储用户完整信息的 AttributeKey（Channel → TokenUserInfoDto）
    public static final AttributeKey<TokenUserInfoDto> TOKEN_USER_INFO_KEY = AttributeKey.valueOf("tokenUserInfo");

    // userId 和 channel 的关系（UserId → Channel）
    public static final ConcurrentHashMap<String, Channel> USER_CONTEXT_MAP = new ConcurrentHashMap<>();

    // channelGroup 和会议房间的关系（meetingId → ChannelGroup）
    public static final ConcurrentHashMap<String, ChannelGroup> MEETING_ROOM_CONTEXT_MAP = new ConcurrentHashMap<>();

    /**
     * 添加用户连接（三向映射）
     * - Channel.attr(USER_ID_KEY) → userId
     * - Channel.attr(TOKEN_USER_INFO_KEY) → TokenUserInfoDto
     * - USER_CONTEXT_MAP: userId → Channel
     * 
     * 如果 userInfo 为 null，会从 Redis 获取
     * 如果用户有 currentMeetingId，会自动加入会议房间
     */
    public void addContext(String userId, Channel channel, TokenUserInfoDto userInfo) {
        // 如果用户已有旧连接，先关闭（单设备登录）
        Channel oldChannel = USER_CONTEXT_MAP.get(userId);
        if (oldChannel != null && oldChannel != channel) {
            // 先获取旧连接的用户信息，用于从会议房间移除
            TokenUserInfoDto oldUserInfo = oldChannel.attr(TOKEN_USER_INFO_KEY).get();
            if (oldUserInfo != null && oldUserInfo.getCurrentMeetingId() != null) {
                leaveMeetingRoom(oldUserInfo.getCurrentMeetingId(), oldChannel);
            }
            // 清除旧连接的属性，防止 channelInactive 发送退出消息
            // 因为这是同一用户的重连，不是真正的退出
            oldChannel.attr(USER_ID_KEY).set(null);
            oldChannel.attr(TOKEN_USER_INFO_KEY).set(null);
            oldChannel.close();
        }

        // 如果 userInfo 为 null，从 Redis 获取
        if (userInfo == null) {
            String token = redisComponent.getTokenByUserId(userId);
            if (token != null) {
                userInfo = redisComponent.getTokenUserInfo(token);
            }
        }

        // 三向映射
        channel.attr(USER_ID_KEY).set(userId);              // Channel → UserId
        channel.attr(TOKEN_USER_INFO_KEY).set(userInfo);    // Channel → TokenUserInfoDto
        USER_CONTEXT_MAP.put(userId, channel);              // UserId → Channel

        log.info("用户 {} 已添加到在线列表，ChannelId: {}", userId, channel.id().asShortText());

        // 如果用户有当前会议ID，自动加入会议房间
        // 注意：成员列表消息在 WebSocket 握手完成后由 HandlerWebSocket.userEventTriggered 发送
        if (userInfo != null && userInfo.getCurrentMeetingId() != null) {
            String meetingId = userInfo.getCurrentMeetingId();
            joinMeetingRoom(meetingId, channel);
            log.info("用户 {} 自动加入会议房间: {}", userId, meetingId);
        }
    }

    /**
     * 添加用户连接（简化版，只传 userId，会自动从 Redis 获取 userInfo）
     */
    public void addContext(String userId, Channel channel) {
        addContext(userId, channel, null);
    }

    /**
     * 根据 userId 移除连接
     */
    public void removeContext(String userId) {
        Channel channel = USER_CONTEXT_MAP.remove(userId);
        if (channel != null) {
            // 从会议房间移除
            TokenUserInfoDto userInfo = channel.attr(TOKEN_USER_INFO_KEY).get();
            if (userInfo != null && userInfo.getCurrentMeetingId() != null) {
                leaveMeetingRoom(userInfo.getCurrentMeetingId(), channel);
            }
            channel.attr(USER_ID_KEY).set(null);
            channel.attr(TOKEN_USER_INFO_KEY).set(null);
        }
        log.info("用户 {} 已从在线列表移除", userId);
    }

    /**
     * 根据 Channel 移除连接（连接断开时使用）
     * 注意：这里不更新 lastOffTime，因为断开可能只是退出会议而非退出系统
     */
    public void removeByChannel(Channel channel) {
        String userId = channel.attr(USER_ID_KEY).get();
        if (userId == null) {
            // 已经被清理过，直接返回（幂等性保护）
            return;
        }
        
        // 先清除 Channel 属性，防止重复处理
        channel.attr(USER_ID_KEY).set(null);
        
        // 从会议房间移除
        TokenUserInfoDto userInfo = channel.attr(TOKEN_USER_INFO_KEY).get();
        if (userInfo != null && userInfo.getCurrentMeetingId() != null) {
            leaveMeetingRoom(userInfo.getCurrentMeetingId(), channel);
        }
        channel.attr(TOKEN_USER_INFO_KEY).set(null);
        
        // 只有当前 Channel 匹配时才移除，防止误删新连接
        USER_CONTEXT_MAP.remove(userId, channel);
        
        log.info("用户 {} 连接已断开，ChannelId: {}", userId, channel.id().asShortText());
    }

    /**
     * 根据 userId 获取 Channel
     */
    public Channel getChannel(String userId) {
        return USER_CONTEXT_MAP.get(userId);
    }

    /**
     * 根据 Channel 获取 userId
     */
    public String getUserId(Channel channel) {
        return channel.attr(USER_ID_KEY).get();
    }

    /**
     * 根据 Channel 获取用户完整信息
     */
    public TokenUserInfoDto getUserInfo(Channel channel) {
        return channel.attr(TOKEN_USER_INFO_KEY).get();
    }

    /**
     * 根据 userId 获取用户完整信息
     */
    public TokenUserInfoDto getUserInfoByUserId(String userId) {
        Channel channel = USER_CONTEXT_MAP.get(userId);
        return channel != null ? channel.attr(TOKEN_USER_INFO_KEY).get() : null;
    }

    /**
     * 获取在线用户数
     */
    public int getOnlineCount() {
        return USER_CONTEXT_MAP.size();
    }

    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(String userId) {
        Channel channel = USER_CONTEXT_MAP.get(userId);
        return channel != null && channel.isActive();
    }

    /**
     * 批量检查用户在线状态
     * @param userIds 用户ID列表
     * @return Map<userId, isOnline>
     */
    public java.util.Map<String, Boolean> checkUsersOnline(java.util.List<String> userIds) {
        java.util.Map<String, Boolean> result = new java.util.HashMap<>();
        for (String userId : userIds) {
            result.put(userId, isUserOnline(userId));
        }
        return result;
    }

    /**
     * 获取所有在线用户ID
     */
    public java.util.Set<String> getOnlineUserIds() {
        return new java.util.HashSet<>(USER_CONTEXT_MAP.keySet());
    }

    // ==================== 会议房间相关 ====================

    /**
     * 加入会议房间
     * 注意：channel 可能为 null（用户通过 HTTP API 加入会议但尚未建立 WebSocket 连接）
     * 此时跳过 WebSocket 房间加入，用户建立 WebSocket 连接后会通过 addContext() 自动加入
     */
    public void joinMeetingRoom(String meetingId, Channel channel) {
        if (channel == null) {
            log.warn("尝试加入会议房间 {} 但 channel 为 null，跳过 WebSocket 房间加入（用户将在建立 WebSocket 连接后自动加入）", meetingId);
            return;
        }
        ChannelGroup channelGroup = MEETING_ROOM_CONTEXT_MAP.computeIfAbsent(
                meetingId,
                k -> new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
        );
        channelGroup.add(channel);
        log.info("Channel {} 加入会议房间 {}", channel.id().asShortText(), meetingId);
    }

    /**
     * 离开会议房间
     */
    public void leaveMeetingRoom(String meetingId, Channel channel) {
        ChannelGroup channelGroup = MEETING_ROOM_CONTEXT_MAP.get(meetingId);
        if (channelGroup != null) {
            channelGroup.remove(channel);
            log.info("Channel {} 离开会议房间 {}", channel.id().asShortText(), meetingId);
            // 如果房间为空，移除房间
            if (channelGroup.isEmpty()) {
                MEETING_ROOM_CONTEXT_MAP.remove(meetingId);
                log.info("会议房间 {} 已清空并移除", meetingId);
            }
        }
    }

    /**
     * 获取会议房间的 ChannelGroup
     */
    public ChannelGroup getMeetingRoom(String meetingId) {
        return MEETING_ROOM_CONTEXT_MAP.get(meetingId);
    }


    /**
     * 获取会议房间人数
     */
    public int getMeetingRoomCount(String meetingId) {
        ChannelGroup channelGroup = MEETING_ROOM_CONTEXT_MAP.get(meetingId);
        return channelGroup != null ? channelGroup.size() : 0;
    }
    // =================消息发送====================

    /**
     * 发送消息（根据类型路由到群组或个人）
     */
    public void sendMessage(MessageSendDto<?> messageSendDto) {
        if (messageSendDto == null) {
            return;
        }
        Integer sendToType = messageSendDto.getMessageSendToType();
        if (MessageSendToTypeEnum.GROUP.getType().equals(sendToType)) {
            sendMsgToGroup(messageSendDto);
        } else if (MessageSendToTypeEnum.USER.getType().equals(sendToType)) {
            sendMsgToUser(messageSendDto);
        }
    }

    /**
     * 发送消息到群组（会议房间）
     */
    private void sendMsgToGroup(MessageSendDto<?> messageSendDto) {
        String meetingId = messageSendDto.getMeetingId();
        if (StringUtils.isEmpty(meetingId)) {
            log.warn("发送群组消息失败：meetingId 为空");
            return;
        }
        ChannelGroup channelGroup = MEETING_ROOM_CONTEXT_MAP.get(meetingId);
        if (channelGroup == null || channelGroup.isEmpty()) {
            log.warn("发送群组消息失败：会议房间 {} 不存在或为空", meetingId);
            return;
        }
        // 序列化消息并发送给房间内所有用户
        String messageJson = com.alibaba.fastjson.JSON.toJSONString(messageSendDto);
        channelGroup.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(messageJson));
        log.info("消息已发送到会议房间 {}，在线人数: {}", meetingId, channelGroup.size());
    }

    /**
     * 发送消息到指定用户
     */
    private void sendMsgToUser(MessageSendDto<?> messageSendDto) {
        String receiveUserId = messageSendDto.getReceiveUserId();
        if (StringUtils.isEmpty(receiveUserId)) {
            log.warn("发送个人消息失败：receiveUserId 为空");
            return;
        }
        Channel channel = USER_CONTEXT_MAP.get(receiveUserId);
        if (channel == null || !channel.isActive()) {
            log.warn("发送个人消息失败：用户 {} 不在线", receiveUserId);
            return;
        }
        // 序列化消息并发送
        String messageJson = com.alibaba.fastjson.JSON.toJSONString(messageSendDto);
        channel.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(messageJson));
        log.info("消息已发送给用户 {}", receiveUserId);
    }

    /**
     * 发送会议成员更新消息
     * 当用户通过 WebSocket 连接并自动加入会议房间时调用
     * 发送成员列表给房间内所有用户
     */
    public void sendMeetingMemberUpdate(String meetingId, String newUserId, String newUserNickName) {
        // 获取会议成员列表
        com.easymeeting.dto.MeetingMemberDto newMember = redisComponent.getMeetingMember(meetingId, newUserId);
        java.util.List<com.easymeeting.dto.MeetingMemberDto> memberList = redisComponent.getMeetingMemberList(meetingId);
        
        // 构建消息内容
        com.easymeeting.dto.MeetingJoinDto meetingJoinDto = new com.easymeeting.dto.MeetingJoinDto();
        meetingJoinDto.setNewMember(newMember);
        meetingJoinDto.setMeetingMemberList(memberList);
        
        // 构建消息
        MessageSendDto<com.easymeeting.dto.MeetingJoinDto> messageSendDto = new MessageSendDto<>();
        messageSendDto.setMessageType(com.easymeeting.enums.MessageTypeEnum.ADD_MEETING_ROOM.getType());
        messageSendDto.setMeetingId(meetingId);
        messageSendDto.setMessageSendToType(MessageSendToTypeEnum.GROUP.getType());
        messageSendDto.setMessageContent(meetingJoinDto);
        messageSendDto.setSendUserId(newUserId);
        messageSendDto.setSendUserNickName(newUserNickName);
        
        // 发送给房间内所有用户
        sendMessage(messageSendDto);
        log.info("已发送会议成员更新消息到房间 {}，新成员: {}", meetingId, newUserNickName);
    }

    /**
     * 发送退出会议消息并清理 Channel
     * 先发送消息（确保用户还在房间内能收到），再从房间移除 Channel
     * 
     * @param messageSendDto 退出消息
     * @param userId 退出的用户ID
     */
    public void sendExitMessageAndCleanup(MessageSendDto<?> messageSendDto, String userId) {
        String meetingId = messageSendDto.getMeetingId();
        if (StringUtils.isEmpty(meetingId) || StringUtils.isEmpty(userId)) {
            log.warn("发送退出消息失败：meetingId 或 userId 为空");
            return;
        }
        
        // 1. 先发送消息（此时用户还在房间内）
        sendMessage(messageSendDto);
        
        // 2. 再从 WebSocket 房间移除 Channel
        Channel channel = USER_CONTEXT_MAP.get(userId);
        if (channel != null) {
            leaveMeetingRoom(meetingId, channel);
            // 更新 Channel 上的用户信息
            TokenUserInfoDto channelUserInfo = channel.attr(TOKEN_USER_INFO_KEY).get();
            if (channelUserInfo != null) {
                channelUserInfo.setCurrentMeetingId(null);
            }
            log.info("用户 {} 已从会议房间 {} 移除", userId, meetingId);
        }
    }

    /**
     * 关闭用户连接（主动踢人时使用）
     * 注意：这里不更新 lastOffTime，单设备登录通过 Redis token 机制实现
     */
    public void closeContext(String userId) {
        if (StringUtils.isEmpty(userId)) {
            return;
        }
        Channel channel = USER_CONTEXT_MAP.get(userId);
        if (channel != null) {
            // 先从会议房间移除
            TokenUserInfoDto userInfo = channel.attr(TOKEN_USER_INFO_KEY).get();
            if (userInfo != null && userInfo.getCurrentMeetingId() != null) {
                leaveMeetingRoom(userInfo.getCurrentMeetingId(), channel);
            }
            // 清理映射
            channel.attr(USER_ID_KEY).set(null);
            channel.attr(TOKEN_USER_INFO_KEY).set(null);
            USER_CONTEXT_MAP.remove(userId);
            
            // 关闭连接
            channel.close();
            log.info("用户 {} 连接已关闭", userId);
        }
    }
}

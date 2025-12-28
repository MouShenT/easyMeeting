package com.easymeeting.redis;

import com.easymeeting.dto.MeetingMemberDto;
import com.easymeeting.dto.TokenUserInfoDto;
import com.easymeeting.entity.constants.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RedisComponent {
    
    private final RedisUtils redisUtils;
    
    // ==================== 验证码相关 ====================
    
    public String saveCheckCode(String code) {
        String checkCodeKey = UUID.randomUUID().toString();
        redisUtils.set(Constants.REDIS_KEY_CHECK_CODE + checkCodeKey, code, Constants.REDIS_EXPIRE_CHECK_CODE, TimeUnit.SECONDS);
        return checkCodeKey;
    }
    
    public String getCheckCode(String checkCodeKey) {
        Object value = redisUtils.get(Constants.REDIS_KEY_CHECK_CODE + checkCodeKey);
        return value == null ? null : value.toString();
    }
    
    public void clearCheckCode(String checkCodeKey) {
        redisUtils.delete(Constants.REDIS_KEY_CHECK_CODE + checkCodeKey);
    }
    
    // ==================== Token相关 ====================
    
    /**
     * 保存用户Token信息到Redis（双向映射）
     * token -> TokenUserInfoDto
     * userId -> token
     */
    public void saveTokenUserInfo(TokenUserInfoDto tokenUserInfo) {
        String token = tokenUserInfo.getToken();
        String userId = tokenUserInfo.getUserId();
        
        // 先删除该用户之前的token（实现单设备登录）
        String oldToken = getTokenByUserId(userId);
        if (oldToken != null) {
            removeToken(oldToken);
        }
        
        // 保存 token -> TokenUserInfoDto
        redisUtils.set(Constants.REDIS_KEY_TOKEN + token, tokenUserInfo, Constants.REDIS_EXPIRE_TOKEN, TimeUnit.SECONDS);
        // 保存 userId -> token
        redisUtils.set(Constants.REDIS_KEY_USER_TOKEN + userId, token, Constants.REDIS_EXPIRE_TOKEN, TimeUnit.SECONDS);
    }
    
    /**
     * 根据Token获取用户会话信息
     */
    public TokenUserInfoDto getTokenUserInfo(String token) {
        return (TokenUserInfoDto) redisUtils.get(Constants.REDIS_KEY_TOKEN + token);
    }
    
    /**
     * 根据userId获取token
     */
    public String getTokenByUserId(String userId) {
        Object value = redisUtils.get(Constants.REDIS_KEY_USER_TOKEN + userId);
        return value == null ? null : value.toString();
    }
    
    /**
     * 更新Token用户信息（如进入会议时更新currentMeetingId）
     */
    public void updateTokenUserInfo(TokenUserInfoDto tokenUserInfo) {
        String token = tokenUserInfo.getToken();
        // 获取剩余过期时间，保持原有过期时间
        Long ttl = redisUtils.getExpire(Constants.REDIS_KEY_TOKEN + token);
        if (ttl != null && ttl > 0) {
            redisUtils.set(Constants.REDIS_KEY_TOKEN + token, tokenUserInfo, ttl, TimeUnit.SECONDS);
        }
    }
    
    /**
     * 删除Token（退出登录/踢人下线）
     */
    public void removeToken(String token) {
        TokenUserInfoDto tokenUserInfo = getTokenUserInfo(token);
        if (tokenUserInfo != null) {
            // 删除 userId -> token
            redisUtils.delete(Constants.REDIS_KEY_USER_TOKEN + tokenUserInfo.getUserId());
        }
        // 删除 token -> TokenUserInfoDto
        redisUtils.delete(Constants.REDIS_KEY_TOKEN + token);
    }
    
    /**
     * 根据userId踢人下线
     */
    public void removeTokenByUserId(String userId) {
        String token = getTokenByUserId(userId);
        if (token != null) {
            redisUtils.delete(Constants.REDIS_KEY_TOKEN + token);
        }
        redisUtils.delete(Constants.REDIS_KEY_USER_TOKEN + userId);
    }
    
    /**
     * 验证Token是否存在
     */
    public boolean hasToken(String token) {
        return redisUtils.hasKey(Constants.REDIS_KEY_TOKEN + token);
    }
    public void addToMeeting(String meetingId, MeetingMemberDto meetingMemberDto) {
        redisUtils.hSet(Constants.REDIS_KEY_MEETING_ROOM+meetingId, meetingMemberDto.getUserId(), meetingMemberDto);
    }
    public List<MeetingMemberDto> getMeetingMemberList(String meetingId) {
        // 获取会议室所有成员
        Map<Object, Object> members = redisUtils.hGetAll(Constants.REDIS_KEY_MEETING_ROOM + meetingId);

        if (members == null || members.isEmpty()) {
            return new ArrayList<>();
        }

        // 转换为 List
        return members.values().stream()
                .map(obj -> (MeetingMemberDto) obj)
                .collect(Collectors.toList());
    }
    public MeetingMemberDto getMeetingMember(String meetingId, String userId) {
        return (MeetingMemberDto) redisUtils.hGet(Constants.REDIS_KEY_MEETING_ROOM + meetingId,userId);
    }
    
    /**
     * 从会议中移除单个成员
     */
    public void removeMeetingMember(String meetingId, String userId) {
        redisUtils.hDelete(Constants.REDIS_KEY_MEETING_ROOM + meetingId, userId);
    }
    
    /**
     * 清理整个会议的成员数据（结束会议时使用）
     */
    public void removeMeetingMembers(String meetingId) {
        redisUtils.delete(Constants.REDIS_KEY_MEETING_ROOM + meetingId);
    }
}

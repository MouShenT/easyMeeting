package com.easymeeting.interceptor;

import com.alibaba.fastjson.JSON;
import com.easymeeting.dto.TokenUserInfoDto;
import com.easymeeting.redis.RedisComponent;
import com.easymeeting.vo.ResponseVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
@RequiredArgsConstructor
public class TokenInterceptor implements HandlerInterceptor {

    private final RedisComponent redisComponent;
    
    // 存储当前请求的用户信息，供 Controller 使用
    public static final String CURRENT_USER = "currentUser";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从 Header 获取 token
        String token = request.getHeader("token");
        
        // token 为空
        if (token == null || token.isEmpty()) {
            writeUnauthorized(response, "请先登录");
            return false;
        }
        
        // 验证 token 是否存在于 Redis
        TokenUserInfoDto userInfo = redisComponent.getTokenUserInfo(token);
        if (userInfo == null) {
            // token 不存在，可能是被踢下线或已过期
            writeUnauthorized(response, "登录已失效，请重新登录");
            return false;
        }
        
        // 将用户信息存入 request，供后续使用
        request.setAttribute(CURRENT_USER, userInfo);
        
        return true;
    }
    
    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        ResponseVO<Object> result = ResponseVO.fail(HttpStatus.UNAUTHORIZED.value(), message);
        response.getWriter().write(JSON.toJSONString(result));
    }
}

package com.easymeeting.interceptor;

import com.alibaba.fastjson.JSON;
import com.easymeeting.dto.TokenUserInfoDto;
import com.easymeeting.vo.ResponseVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员权限拦截器
 * 用于拦截 /admin/** 路径，校验当前用户是否为管理员
 */
@Component
@Slf4j
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从 request 中获取 TokenInterceptor 存入的用户信息
        TokenUserInfoDto userInfo = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        
        // 用户信息不存在（理论上不会发生，因为 TokenInterceptor 先执行）
        if (userInfo == null) {
            writeForbidden(response, "请先登录");
            return false;
        }
        
        // 校验是否为管理员
        if (userInfo.getAdmin() == null || !userInfo.getAdmin()) {
            log.warn("非管理员用户 [{}] 尝试访问管理接口: {}", userInfo.getUserId(), request.getRequestURI());
            writeForbidden(response, "无权限访问，仅管理员可操作");
            return false;
        }
        
        return true;
    }
    
    private void writeForbidden(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        ResponseVO<Object> result = ResponseVO.fail(HttpStatus.FORBIDDEN.value(), message);
        response.getWriter().write(JSON.toJSONString(result));
    }
}

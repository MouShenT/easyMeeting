package com.easymeeting.config;

import com.easymeeting.interceptor.AdminInterceptor;
import com.easymeeting.interceptor.TokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TokenInterceptor tokenInterceptor;
    private final AdminInterceptor adminInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Token 拦截器 - 校验登录状态
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/**")  // 拦截所有请求
                .excludePathPatterns(
                        "/account/checkCode",   // 获取验证码
                        "/account/login",       // 登录
                        "/account/register",    // 注册
                        "/error"                // 错误页面
                )
                .order(1);  // 优先级高，先执行
        
        // Admin 拦截器 - 校验管理员权限
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/admin/**")  // 只拦截 /admin 路径
                .order(2);  // 在 Token 拦截器之后执行
    }
}

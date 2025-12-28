package com.easymeeting.aspect;



import com.easymeeting.annotation.GlobalInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component
@Aspect
@Slf4j
public class GlobalOperationAspect {
    @Before("@annotation(com.easymeeting.annotation.GlobalInterceptor)")
    public void interceptorDo(JoinPoint point) {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) point.getSignature();
        // 获取 Method 对象
        Method method = signature.getMethod();
        // 获取注解
        GlobalInterceptor annotation = method.getAnnotation(GlobalInterceptor.class);
        if(annotation != null) {
            return;
        }
        if (annotation.checkLogin() || annotation.checkAdmin()) {
            checkLogin(annotation.checkAdmin());
        }
    }
    private void checkLogin(Boolean checkAdmin) {

    }
}

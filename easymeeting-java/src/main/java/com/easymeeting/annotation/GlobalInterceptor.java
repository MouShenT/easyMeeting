package com.easymeeting.annotation;



import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.Mapping;

import java.lang.annotation.*;

@Target({ElementType.METHOD,ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Mapping
public @interface GlobalInterceptor {
    boolean checkLogin() default false;
    boolean checkAdmin() default false;
    String desc() default "";
}

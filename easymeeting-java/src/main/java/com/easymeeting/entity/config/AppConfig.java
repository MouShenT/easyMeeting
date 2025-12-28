package com.easymeeting.entity.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Data
@Component
public class AppConfig {
    
    @Value("${admin.emails:}")
    private String adminEmails;
    @Value("${ws.port}")
    private Integer wsPort;
    
    /**
     * 判断邮箱是否为管理员
     */
    public boolean isAdmin(String email) {
        if (adminEmails == null || adminEmails.isEmpty() || email == null) {
            return false;
        }
        List<String> adminList = Arrays.asList(adminEmails.split(","));
        return adminList.stream()
                .map(String::trim)
                .anyMatch(admin -> admin.equalsIgnoreCase(email));
    }
}

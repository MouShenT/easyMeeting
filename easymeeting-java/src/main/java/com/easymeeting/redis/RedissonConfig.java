package com.easymeeting.redis;

import com.easymeeting.entity.constants.Constants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置类
 * 当 messaging.handle.channel=redis 时启用
 * 
 * Redisson 提供的功能：
 * - 分布式锁
 * - 发布/订阅（Pub/Sub）
 * - 分布式集合
 * - 异步操作
 */
@Configuration
@ConditionalOnProperty(name = Constants.MESSAGE_HANDLE_CHANNEL, havingValue = Constants.MESSAGE_CHANNEL_REDIS)
@Slf4j
public class RedissonConfig {

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    /**
     * 创建 RedissonClient Bean
     * 用于消息发布/订阅、分布式锁等功能
     */
    @Bean(name="redissonClient",destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        
        // 构建 Redis 地址
        String redisAddress = String.format("redis://%s:%d", redisHost, redisPort);
        
        // 单节点模式配置
        config.useSingleServer()
                .setAddress(redisAddress)
                .setDatabase(redisDatabase)
                .setConnectionMinimumIdleSize(5)      // 最小空闲连接数
                .setConnectionPoolSize(20)            // 连接池大小
                .setIdleConnectionTimeout(10000)      // 空闲连接超时（毫秒）
                .setConnectTimeout(10000)             // 连接超时（毫秒）
                .setTimeout(3000)                     // 命令等待超时（毫秒）
                .setRetryAttempts(3)                  // 命令重试次数
                .setRetryInterval(1500);              // 命令重试间隔（毫秒）
        
        // 设置密码（如果有）
        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.useSingleServer().setPassword(redisPassword);
        }
        
        RedissonClient redissonClient = Redisson.create(config);
        log.info("Redisson 客户端初始化成功，连接地址: {}, 数据库: {}", redisAddress, redisDatabase);
        
        return redissonClient;
    }
}

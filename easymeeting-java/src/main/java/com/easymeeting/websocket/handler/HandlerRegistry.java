package com.easymeeting.websocket.handler;

import com.easymeeting.enums.MessageTypeEnum;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 处理器注册表
 * 自动发现并注册所有 MessageTypeHandler 实现
 */
@Component
@Slf4j
public class HandlerRegistry {
    
    private final List<MessageTypeHandler> handlers;
    private final Map<Integer, MessageTypeHandler> handlerMap = new ConcurrentHashMap<>();
    private MessageTypeHandler defaultHandler;
    
    public HandlerRegistry(List<MessageTypeHandler> handlers) {
        this.handlers = handlers;
    }
    
    @PostConstruct
    public void init() {
        for (MessageTypeHandler handler : handlers) {
            for (MessageTypeEnum type : handler.getSupportedTypes()) {
                MessageTypeHandler existing = handlerMap.put(type.getType(), handler);
                if (existing != null) {
                    log.warn("消息类型 {} 已有处理器 {}，被 {} 覆盖", 
                        type, existing.getClass().getSimpleName(), 
                        handler.getClass().getSimpleName());
                }
                log.info("注册消息处理器: {} -> {}", type, handler.getClass().getSimpleName());
            }
            
            // 检查是否是默认处理器
            if (handler instanceof DefaultForwardHandler) {
                this.defaultHandler = handler;
            }
        }
        log.info("消息处理器注册完成，共 {} 个处理器，{} 种消息类型", 
            handlers.size(), handlerMap.size());
    }
    
    /**
     * 根据消息类型获取处理器
     */
    public Optional<MessageTypeHandler> getHandler(Integer messageType) {
        return Optional.ofNullable(handlerMap.get(messageType));
    }
    
    /**
     * 获取默认处理器
     */
    public MessageTypeHandler getDefaultHandler() {
        return defaultHandler;
    }
    
    /**
     * 获取已注册的处理器数量
     */
    public int getHandlerCount() {
        return handlerMap.size();
    }
}

package com.easymeeting.websocket;

import com.easymeeting.websocket.message.MessageHandler;
import com.easymeeting.websocket.netty.NettyWebSocketStarter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InitRun implements ApplicationRunner{
    private final NettyWebSocketStarter nettyWebSocketStarter;
    @Override
    public void run(ApplicationArguments args) throws Exception {
        new Thread(nettyWebSocketStarter).start();
    }
}

package com.easymeeting.websocket.netty;

import com.easymeeting.websocket.ChannelContextUtils;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class HandlerHeartBeat extends ChannelDuplexHandler {

    private final ChannelContextUtils channelContextUtils;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;

            switch (event.state()) {
                case READER_IDLE:
                    String userId = ctx.channel().attr(ChannelContextUtils.USER_ID_KEY).get();
                    log.info("用户 {} 没有发送心跳，断开连接", userId);
                    ctx.close();
                    break;
                case WRITER_IDLE:
                    ctx.writeAndFlush("heart");
                    break;
                case ALL_IDLE:
                    break;
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("有连接已经断开");
        // 调用 ChannelContextUtils 处理断开逻辑
        channelContextUtils.removeByChannel(ctx.channel());
        super.channelInactive(ctx);
    }
}

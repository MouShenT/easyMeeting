package com.easymeeting.websocket.netty;

import com.easymeeting.dto.TokenUserInfoDto;
import com.easymeeting.redis.RedisComponent;
import com.easymeeting.utils.JwtUtils;
import com.easymeeting.websocket.ChannelContextUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@ChannelHandler.Sharable
@Slf4j
@RequiredArgsConstructor
public class HandlerTokenValidation extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final RedisComponent redisComponent;
    private final ChannelContextUtils channelContextUtils;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        String uri = request.uri();
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri);
        List<String> tokenList = queryStringDecoder.parameters().get("token");

        if (tokenList == null || tokenList.isEmpty()) {
            log.warn("WebSocket连接被拒绝：缺少token");
            sendErrorResponse(ctx, "缺少token");
            return;
        }

        String token = tokenList.get(0);

        // 校验token格式
        if (!JwtUtils.validateToken(token)) {
            log.warn("WebSocket连接被拒绝：无效的JWT token");
            sendErrorResponse(ctx, "无效的token");
            return;
        }

        // 检查token是否存在redis
        TokenUserInfoDto userInfo = redisComponent.getTokenUserInfo(token);
        if (userInfo == null) {
            log.warn("WebSocket连接被拒绝：会话中未找到token");
            sendErrorResponse(ctx, "token已过期或无效");
            return;
        }

        // 添加三向映射：userId ↔ Channel ↔ TokenUserInfoDto
        channelContextUtils.addContext(userInfo.getUserId(), ctx.channel(), userInfo);

        log.info("WebSocket token验证成功，用户：{}", userInfo.getUserId());

        // 将请求传递给下一个Handler（WebSocket握手）
        ctx.fireChannelRead(request.retain());
    }

    private void sendErrorResponse(ChannelHandlerContext ctx, String message) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.UNAUTHORIZED,
                Unpooled.copiedBuffer(message, StandardCharsets.UTF_8)
        );
        response.headers().set("Content-Type", "text/plain; charset=UTF-8");
        response.headers().set("Content-Length", response.content().readableBytes());
        ctx.writeAndFlush(response).addListener(future -> ctx.close());
    }
}

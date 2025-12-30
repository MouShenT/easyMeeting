package com.easymeeting.websocket.netty;

import com.easymeeting.entity.config.AppConfig;
import com.easymeeting.websocket.ChannelContextUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

//异步线程
@Component
@Slf4j
@RequiredArgsConstructor
public class NettyWebSocketStarter implements Runnable {
    //父线程组，boss线程组，用于处理连接
    //子线程组，work线程组，用于处理消息
    private EventLoopGroup bossGroup=new NioEventLoopGroup();
    private EventLoopGroup workerGroup=new NioEventLoopGroup();
    private final HandlerTokenValidation handlerTokenValidation;
    private final HandlerWebSocket handlerWebSocket;
    private final AppConfig appConfig;
    private final ChannelContextUtils channelContextUtils;

    @Override
    public void run() {
        try {
            ServerBootstrap serverBootstrap=new ServerBootstrap();
            serverBootstrap.group(bossGroup,workerGroup);
            serverBootstrap.channel(NioServerSocketChannel.class).handler(new LoggingHandler(LogLevel.DEBUG))
                    .childHandler(new ChannelInitializer<Channel>() {

                        @Override
                        protected void initChannel(Channel channel) throws Exception {
                            ChannelPipeline pipeline=channel.pipeline();
                            //消息编码器，解码器
                            pipeline.addLast(new HttpServerCodec());
                            //消息聚合器
                            pipeline.addLast(new HttpObjectAggregator(64*1024));
                            //心跳检测 - 读空闲超时设为120秒，防止浏览器后台节流导致心跳延迟
                            pipeline.addLast(new IdleStateHandler(120,0,0));
                            //自定义的心跳处理器，可以提取当前连接断开的userid
                            pipeline.addLast(new HandlerHeartBeat(channelContextUtils));
                            //token校验，拦截channelread事件，
                            pipeline.addLast(handlerTokenValidation);
                            //WebSocket协议处理器
                            pipeline.addLast(new WebSocketServerProtocolHandler("/ws",null,true,65535,true,true));
                            //自定义业务处理器
                            pipeline.addLast(handlerWebSocket);

                        }
                    });
            Channel channel = serverBootstrap.bind(appConfig.getWsPort()).sync().channel();
            log.info("netty启动成功,端口{}",appConfig.getWsPort());
            // 等待服务器通道关闭，保持服务器运行
            channel.closeFuture().sync();
        }catch (Exception e){
            log.error("netty启动失败",e);
        }finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    /**
     * 程序关闭之前也要销毁这两个线程组
     */
    @PreDestroy
    public void close(){
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}

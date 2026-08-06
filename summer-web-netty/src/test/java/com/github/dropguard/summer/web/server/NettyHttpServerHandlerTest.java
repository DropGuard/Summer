package com.github.dropguard.summer.web.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class NettyHttpServerHandlerTest {

    @Mock private NettyHttpServer server;
    @Mock private WebServerDependencies deps;
    @Mock private ChannelHandlerContext ctx;
    @Mock private FullHttpRequest request;
    @Mock private ChannelFuture channelFuture;

    private NettyHttpServerHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(server.getActiveConnections()).thenReturn(new AtomicInteger(0));
        when(ctx.writeAndFlush(any())).thenReturn(channelFuture);

        HttpHeaders headers = new DefaultHttpHeaders();
        when(request.headers()).thenReturn(headers);
        when(request.protocolVersion()).thenReturn(HttpVersion.HTTP_1_1);
        when(request.method()).thenReturn(HttpMethod.GET);
        when(request.uri()).thenReturn("/test");

        handler = new NettyHttpServerHandler(server, null, deps);
    }

    @Test
    void shouldHandleExceptionCaught() throws Exception {
        Throwable cause = new RuntimeException("Test exception");
        when(ctx.close()).thenReturn(channelFuture);

        handler.exceptionCaught(ctx, cause);

        verify(ctx, times(1)).close();
    }

    @Test
    void shouldNotPropagateCloseFailure() {
        Throwable cause = new RuntimeException("Test exception");
        when(ctx.close()).thenThrow(new RuntimeException("Close failed"));

        // Best-effort cleanup: a close failure is logged, never propagated out of
        // exceptionCaught (a throwing callback would re-enter Netty's exception path).
        assertDoesNotThrow(() -> handler.exceptionCaught(ctx, cause));
    }
}

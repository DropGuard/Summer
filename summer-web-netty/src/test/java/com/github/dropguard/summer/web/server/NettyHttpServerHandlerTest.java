package com.github.dropguard.summer.web.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.JsonBodyConverter;
import com.github.dropguard.summer.web.http.RadixTreeHttpRouter;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

    // ── response disposition: matched-silent handler vs unmatched route ──────────

    @Test
    void matchedHandlerWithoutResponseGets500InsteadOf404() throws Exception {
        RadixTreeHttpRouter router =
                new RadixTreeHttpRouter(
                        List.of(
                                new com.github.dropguard.summer.web.HttpRouter.Builder.Route(
                                        com.github.dropguard.summer.web.HttpMethod.GET,
                                        "/silent",
                                        c -> {
                                            // deferred-write contract violation: matched but no
                                            // status/body written
                                        })));
        handler = new NettyHttpServerHandler(server, null, deps(router));

        HttpResponseStatus status = dispatch("/silent");

        assertEquals(
                HttpResponseStatus.INTERNAL_SERVER_ERROR,
                status,
                "a matched handler that wrote no response must surface as 500, not 404");
    }

    @Test
    void unmatchedRouteStillGets404() throws Exception {
        RadixTreeHttpRouter router =
                new RadixTreeHttpRouter(
                        List.of(
                                new com.github.dropguard.summer.web.HttpRouter.Builder.Route(
                                        com.github.dropguard.summer.web.HttpMethod.GET,
                                        "/hello",
                                        c -> c.text(HttpStatus.OK, "world"))));
        handler = new NettyHttpServerHandler(server, null, deps(router));

        HttpResponseStatus status = dispatch("/missing");

        assertEquals(HttpResponseStatus.NOT_FOUND, status);
    }

    private HttpResponseStatus dispatch(String uri) throws Exception {
        FullHttpRequest req =
                new DefaultFullHttpRequest(
                        HttpVersion.HTTP_1_1,
                        HttpMethod.GET,
                        uri,
                        Unpooled.EMPTY_BUFFER,
                        new DefaultHttpHeaders(),
                        new DefaultHttpHeaders());
        handler.channelRead0(ctx, req);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(ctx, timeout(2000)).writeAndFlush(captor.capture());
        FullHttpResponse response = (FullHttpResponse) captor.getValue();
        try {
            return response.status();
        } finally {
            response.release();
        }
    }

    private static WebServerDependencies deps(RadixTreeHttpRouter router) {
        WebSocketUpgradeHandler upgrade = mock(WebSocketUpgradeHandler.class);
        when(upgrade.isWebSocketUpgrade(any())).thenReturn(false);
        return new WebServerDependencies(
                router, null, List.of(), new JsonBodyConverter(), null, List.of(), upgrade);
    }
}

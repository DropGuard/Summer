package com.github.dropguard.summer.web.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.BindException;
import org.junit.jupiter.api.Test;

/**
 * A port conflict gets the actionable server.port=0 hint in both transport shapes (NIO's
 * BindException, epoll's native IOException carrying the errno text); other failures keep the plain
 * message.
 */
class NettyBindFailureMessageTest {

    private static final String PLAIN = "Failed to bind HTTP server on port 8080";

    @Test
    void nioBindExceptionGetsTheHint() {
        String message =
                NettyHttpServer.bindFailureMessage(8080, new BindException("Address in use"));
        assertTrue(message.startsWith(PLAIN + ": address already in use."), message);
        assertTrue(message.contains("server.port=0"), message);
    }

    @Test
    void epollNativeMessageGetsTheHint() {
        Throwable cause =
                new IllegalStateException(
                        "wrapper", new IOException("bind(..) failed: Address already in use"));
        String message = NettyHttpServer.bindFailureMessage(8080, cause);
        assertTrue(message.contains("server.port=0"), message);
    }

    @Test
    void otherFailuresKeepThePlainMessage() {
        assertEquals(
                PLAIN,
                NettyHttpServer.bindFailureMessage(8080, new IOException("Permission denied")));
        assertEquals(PLAIN, NettyHttpServer.bindFailureMessage(8080, null));
    }
}

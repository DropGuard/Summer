package com.github.dropguard.summer.web.server;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.web.HttpStatus;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NettyChunkedResponseTest {

    private EmbeddedChannel channel;
    private NettyChunkedResponse chunkedResponse;

    @BeforeEach
    void setUp() {
        channel = new EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter());
        chunkedResponse = new NettyChunkedResponse(channel.pipeline().firstContext(), true);
    }

    @Test
    void shouldSendHeadersAndChunksAndTerminalContent() {
        chunkedResponse.contentType("text/plain");
        chunkedResponse.status(HttpStatus.OK);
        chunkedResponse.header("X-Custom", "Val");

        chunkedResponse.write("Chunk 1");
        chunkedResponse.write("Chunk 2");
        chunkedResponse.close();

        // 1. Initial HttpResponse
        HttpResponse response = channel.readOutbound();
        assertNotNull(response);
        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals("text/plain", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
        assertEquals(
                HttpHeaderValues.CHUNKED.toString(),
                response.headers().get(HttpHeaderNames.TRANSFER_ENCODING));
        assertEquals(
                HttpHeaderValues.KEEP_ALIVE.toString(),
                response.headers().get(HttpHeaderNames.CONNECTION));
        assertEquals("Val", response.headers().get("X-Custom"));

        // 2. Chunk 1
        HttpContent content1 = channel.readOutbound();
        assertNotNull(content1);
        assertEquals("Chunk 1", content1.content().toString(StandardCharsets.UTF_8));
        content1.release();

        // 3. Chunk 2
        HttpContent content2 = channel.readOutbound();
        assertNotNull(content2);
        assertEquals("Chunk 2", content2.content().toString(StandardCharsets.UTF_8));
        content2.release();

        // 4. LastHttpContent
        LastHttpContent lastContent = channel.readOutbound();
        assertNotNull(lastContent);
        lastContent.release();

        assertTrue(chunkedResponse.isClosed());
    }

    @Test
    void shouldThrowIfMutatingHeadersAfterStreamingStarted() {
        chunkedResponse.write("Hello");

        assertThrows(IllegalStateException.class, () -> chunkedResponse.header("X-Test", "123"));
        assertThrows(
                IllegalStateException.class, () -> chunkedResponse.contentType("application/json"));
        assertThrows(
                IllegalStateException.class, () -> chunkedResponse.status(HttpStatus.BAD_REQUEST));
    }
}

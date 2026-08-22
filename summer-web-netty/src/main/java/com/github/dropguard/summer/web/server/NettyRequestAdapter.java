package com.github.dropguard.summer.web.server;

import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.Request;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import java.util.Map;

class NettyRequestAdapter {

    private NettyRequestAdapter() {}

    public static Request adapt(FullHttpRequest nettyReq) {
        String uri = nettyReq.uri();
        String path = uri;
        String query = "";

        int questionMarkIndex = uri.indexOf('?');
        if (questionMarkIndex != -1) {
            path = uri.substring(0, questionMarkIndex);
            query = uri.substring(questionMarkIndex + 1);
        }

        String method = nettyReq.method().name();

        // Zero-copy: delegate header lookups to Netty's case-insensitive HttpHeaders
        // instead of eagerly copying every header into a new HashMap per request.
        Map<String, String> headers = new NettyHeadersMap(nettyReq.headers());

        String contentType = nettyReq.headers().get(HttpHeaderNames.CONTENT_TYPE);

        // Lazy body: defer the byte[] copy until getBody() is actually called.
        // GET and DELETE requests never read the body, so this avoids a wasted allocation
        // for ~50% of requests in a typical CRUD workload.
        java.util.function.Supplier<byte[]> lazyBody = null;
        if (nettyReq.content().isReadable()) {
            ByteBuf content = nettyReq.content();
            lazyBody =
                    () -> {
                        byte[] bytes = new byte[content.readableBytes()];
                        content.getBytes(content.readerIndex(), bytes);
                        return bytes;
                    };
        }

        HttpMethod httpMethod;
        try {
            httpMethod = HttpMethod.valueOf(method);
        } catch (IllegalArgumentException e) {
            httpMethod = HttpMethod.UNKNOWN;
        }

        return new Request(httpMethod, path, query, contentType, lazyBody, headers);
    }
}

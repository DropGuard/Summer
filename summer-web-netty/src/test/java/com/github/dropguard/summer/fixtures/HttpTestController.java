package com.github.dropguard.summer.fixtures;

import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.Post;
import com.github.dropguard.summer.web.annotation.RestController;

@RestController("/test")
public class HttpTestController {
    @Get("/hello")
    public void hello(HttpContext ctx) {
        ctx.text(HttpStatus.OK, "world");
    }

    @Post("/openai/chat")
    public void openaiChat(HttpContext ctx, com.github.dropguard.summer.web.SseStream sse) {
        String[] tokens = {"Hello", " world", " from", " Netty", " SSE"};
        for (String token : tokens) {
            sse.send(String.format("{\"choices\":[{\"delta\":{\"content\":\"%s\"}}]}", token));
        }
        sse.send("[DONE]");
        sse.close();
    }

    @Get("/stream/chunked")
    public void streamChunked(
            HttpContext ctx, com.github.dropguard.summer.web.ChunkedResponse chunked) {
        chunked.contentType("text/csv");
        chunked.write("id,name\n");
        chunked.write("1,Alice\n");
        chunked.write("2,Bob\n");
        chunked.close();
    }
}

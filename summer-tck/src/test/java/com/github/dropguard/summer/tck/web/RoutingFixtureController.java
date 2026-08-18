package com.github.dropguard.summer.tck.web;

import com.github.dropguard.summer.web.DefaultPageRequest;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.Delete;
import com.github.dropguard.summer.web.annotation.ExceptionHandler;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.PathParam;
import com.github.dropguard.summer.web.annotation.Post;
import com.github.dropguard.summer.web.annotation.Put;
import com.github.dropguard.summer.web.annotation.QueryParam;
import com.github.dropguard.summer.web.annotation.RestController;

/**
 * TCK-level routing fixture.
 *
 * <p>Deliberately mechanism-focused and business-agnostic: it exists only so the web routing TCK
 * ({@code AbstractWebRouteTCK}) can assert dispatch, path-param binding, request-body parsing and
 * exception propagation without depending on any application's controllers. The route table here is
 * the contract that {@code routeTestCases()} asserts against — keep the two in sync.
 */
@RestController("/rt")
public class RoutingFixtureController {

    public record NameBody(String name) {}

    @Get("/users/{id}")
    public void getUser(HttpContext ctx, @PathParam("id") String id) {
        ctx.text(HttpStatus.OK, "user:" + id);
    }

    @Post("/users")
    public void createUser(HttpContext ctx) {
        NameBody body = ctx.body(NameBody.class);
        ctx.text(HttpStatus.OK, "created:" + body.name());
    }

    @Put("/users/{id}")
    public void updateUser(HttpContext ctx, @PathParam("id") String id) {
        NameBody body = ctx.body(NameBody.class);
        ctx.text(HttpStatus.OK, "updated:" + id + ":" + body.name());
    }

    @Delete("/users/{id}")
    public void deleteUser(HttpContext ctx, @PathParam("id") String id) {
        ctx.text(HttpStatus.OK, "deleted:" + id);
    }

    @Get("/items")
    public void listItems(HttpContext ctx, DefaultPageRequest page) {
        ctx.text(HttpStatus.OK, "page:" + page.page() + ":size:" + page.size());
    }

    @Get("/color/{name}")
    public void color(HttpContext ctx, @PathParam("name") FixtureColor color) {
        ctx.text(HttpStatus.OK, "color:" + color.name());
    }

    @Get("/typed")
    public void typed(
            HttpContext ctx,
            @QueryParam("ratio") float ratio,
            @QueryParam("size") short size,
            @QueryParam("flag") boolean flag,
            @QueryParam("letter") char letter,
            @QueryParam("hue") FixtureColor hue) {
        ctx.text(
                HttpStatus.OK,
                "typed:" + ratio + ":" + size + ":" + flag + ":" + letter + ":" + hue.name());
    }

    @Get("/secured")
    public void secured(HttpContext ctx) {
        ctx.text(HttpStatus.OK, "secret");
    }

    @Get("/error")
    public void error(HttpContext ctx) {
        throw new IllegalArgumentException("invalid id");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public void onIllegalArgument(IllegalArgumentException ex, HttpContext ctx) {
        ctx.text(HttpStatus.BAD_REQUEST, "error_caught:" + ex.getMessage());
    }

    /**
     * Checked-exception route: Handler.handle declares throws Exception (the Gin panic-recovery
     * model in Java), so a checked exception propagates to its @ExceptionHandler.
     */
    @Get("/checked-error")
    public void checkedError(HttpContext ctx) throws java.io.IOException {
        throw new java.io.IOException("io failure");
    }

    @ExceptionHandler(java.io.IOException.class)
    public void onIo(java.io.IOException ex, HttpContext ctx) {
        ctx.text(HttpStatus.BAD_REQUEST, "io_caught:" + ex.getMessage());
    }

    @Get("/chat/stream")
    public void chatStream(HttpContext ctx, com.github.dropguard.summer.web.SseStream sse) {
        sse.send("token:1");
        sse.send("delta", "token:2");
        sse.send("[DONE]");
        sse.close();
    }

    @Get("/export/chunked")
    public void exportChunked(
            HttpContext ctx, com.github.dropguard.summer.web.ChunkedResponse chunked) {
        chunked.contentType("text/plain");
        chunked.write("chunk-A\n");
        chunked.write("chunk-B\n");
        chunked.close();
    }
}

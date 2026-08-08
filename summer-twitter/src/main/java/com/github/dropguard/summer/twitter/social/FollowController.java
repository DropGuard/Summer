package com.github.dropguard.summer.twitter.social;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.twitter.common.IllegalOperationException;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.RequestAttributes;
import com.github.dropguard.summer.web.annotation.Delete;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.Post;
import com.github.dropguard.summer.web.annotation.RestController;
import java.util.List;

@Component
@RestController
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @Post("/api/users/:username/follow")
    public void follow(HttpContext ctx) {
        Long currentUserId = ctx.request().getAttribute(RequestAttributes.USER_ID);
        String username = ctx.request().pathParam("username");
        followService.follow(currentUserId, username);
        ctx.ok("OK");
    }

    @Delete("/api/users/:username/follow")
    public void unfollow(HttpContext ctx) {
        Long currentUserId = ctx.request().getAttribute(RequestAttributes.USER_ID);
        String username = ctx.request().pathParam("username");
        followService.unfollow(currentUserId, username);
        ctx.ok("OK");
    }

    @Get("/api/users/:username/followers")
    public void getFollowers(HttpContext ctx) {
        String username = ctx.request().pathParam("username");
        FollowPage page = parsePage(ctx);
        List<Follow> followers = followService.getFollowers(username, page.cursor(), page.limit());
        ctx.ok(followers);
    }

    @Get("/api/users/:username/following")
    public void getFollowing(HttpContext ctx) {
        String username = ctx.request().pathParam("username");
        FollowPage page = parsePage(ctx);
        List<Follow> following = followService.getFollowing(username, page.cursor(), page.limit());
        ctx.ok(following);
    }

    private static final int MAX_LIMIT = 100;

    private FollowPage parsePage(HttpContext ctx) {
        String cursorStr = ctx.request().queryParam("cursor");
        Long cursor = cursorStr != null ? parseLong(cursorStr, "cursor") : null;
        String limitStr = ctx.request().queryParam("limit");
        int limit = limitStr != null ? parseInt(limitStr, "limit") : 20;
        if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
        return new FollowPage(cursor, limit);
    }

    private static Long parseLong(String s, String name) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalOperationException("Invalid " + name + ": " + s);
        }
    }

    private static int parseInt(String s, String name) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalOperationException("Invalid " + name + ": " + s);
        }
    }

    private record FollowPage(Long cursor, int limit) {}
}

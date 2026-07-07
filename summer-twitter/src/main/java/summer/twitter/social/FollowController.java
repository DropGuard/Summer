package summer.twitter.social;

import summer.core.Component;
import summer.web.annotation.RestController;
import summer.web.annotation.Delete;
import summer.web.annotation.Get;
import summer.web.annotation.Post;
import summer.web.HttpContext;
import summer.web.RequestAttributes;
import summer.web.HttpStatus;

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
        
        try {
            followService.follow(currentUserId, username);
            ctx.ok("OK");
        } catch (IllegalArgumentException e) {
            ctx.json(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @Delete("/api/users/:username/follow")
    public void unfollow(HttpContext ctx) {
        Long currentUserId = ctx.request().getAttribute(RequestAttributes.USER_ID);
        String username = ctx.request().pathParam("username");
        
        try {
            followService.unfollow(currentUserId, username);
            ctx.ok("OK");
        } catch (IllegalArgumentException e) {
            ctx.json(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @Get("/api/users/:username/followers")
    public void getFollowers(HttpContext ctx) {
        String username = ctx.request().pathParam("username");
        String cursorStr = ctx.request().queryParam("cursor");
        Long cursor = cursorStr != null ? Long.parseLong(cursorStr) : null;
        String limitStr = ctx.request().queryParam("limit");
        int limit = limitStr != null ? Integer.parseInt(limitStr) : 20;

        try {
            List<Follow> followers = followService.getFollowers(username, cursor, limit);
            ctx.ok(followers);
        } catch (IllegalArgumentException e) {
            ctx.json(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @Get("/api/users/:username/following")
    public void getFollowing(HttpContext ctx) {
        String username = ctx.request().pathParam("username");
        String cursorStr = ctx.request().queryParam("cursor");
        Long cursor = cursorStr != null ? Long.parseLong(cursorStr) : null;
        String limitStr = ctx.request().queryParam("limit");
        int limit = limitStr != null ? Integer.parseInt(limitStr) : 20;

        try {
            List<Follow> following = followService.getFollowing(username, cursor, limit);
            ctx.ok(following);
        } catch (IllegalArgumentException e) {
            ctx.json(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}

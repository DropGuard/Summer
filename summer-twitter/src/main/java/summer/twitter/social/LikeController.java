package summer.twitter.social;

import summer.core.Component;
import summer.web.annotation.RestController;
import summer.web.annotation.Delete;
import summer.web.annotation.Post;
import summer.web.HttpContext;
import summer.web.RequestAttributes;
import summer.web.HttpStatus;

@Component
@RestController
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @Post("/api/tweets/:id/like")
    public void like(HttpContext ctx) {
        Long currentUserId = ctx.request().getAttribute(RequestAttributes.USER_ID);
        String idStr = ctx.request().pathParam("id");
        Long tweetId = Long.parseLong(idStr);

        try {
            likeService.like(currentUserId, tweetId);
            ctx.ok("OK");
        } catch (Exception e) {
            ctx.json(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @Delete("/api/tweets/:id/like")
    public void unlike(HttpContext ctx) {
        Long currentUserId = ctx.request().getAttribute(RequestAttributes.USER_ID);
        String idStr = ctx.request().pathParam("id");
        Long tweetId = Long.parseLong(idStr);

        try {
            likeService.unlike(currentUserId, tweetId);
            ctx.ok("OK");
        } catch (Exception e) {
            ctx.json(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}

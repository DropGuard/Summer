package summer.twitter.social;

import summer.core.Component;
import summer.twitter.common.IllegalOperationException;
import summer.web.annotation.RestController;
import summer.web.annotation.Delete;
import summer.web.annotation.Post;
import summer.web.HttpContext;
import summer.web.RequestAttributes;

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
		Long tweetId = parseId(ctx.request().pathParam("id"));
		likeService.like(currentUserId, tweetId);
		ctx.ok("OK");
	}

	@Delete("/api/tweets/:id/like")
	public void unlike(HttpContext ctx) {
		Long currentUserId = ctx.request().getAttribute(RequestAttributes.USER_ID);
		Long tweetId = parseId(ctx.request().pathParam("id"));
		likeService.unlike(currentUserId, tweetId);
		ctx.ok("OK");
	}

	private static Long parseId(String id) {
		try {
			return Long.parseLong(id);
		} catch (NumberFormatException e) {
			throw new IllegalOperationException("Invalid tweet id: " + id);
		}
	}
}

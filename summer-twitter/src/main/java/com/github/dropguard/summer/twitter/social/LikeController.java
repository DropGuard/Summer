package com.github.dropguard.summer.twitter.social;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.twitter.common.IllegalOperationException;
import com.github.dropguard.summer.web.annotation.RestController;
import com.github.dropguard.summer.web.annotation.Delete;
import com.github.dropguard.summer.web.annotation.Post;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.RequestAttributes;

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

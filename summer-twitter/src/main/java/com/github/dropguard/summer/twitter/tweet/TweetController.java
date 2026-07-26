package com.github.dropguard.summer.twitter.tweet;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.RequestAttributes;
import com.github.dropguard.summer.web.annotation.Delete;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.PathParam;
import com.github.dropguard.summer.web.annotation.Post;
import com.github.dropguard.summer.web.annotation.QueryParam;
import com.github.dropguard.summer.web.annotation.RestController;

import com.github.dropguard.summer.web.CursorPageable;
import java.util.List;

@RestController
@Component
public class TweetController {

    private final TweetService tweetService;

    public TweetController(TweetService tweetService) {
        this.tweetService = tweetService;
    }

    public record CreateTweetRequest(String content, Long parentId) {}

    @Post("/api/tweets")
    public void createTweet(HttpContext ctx) {
        Long authorId = ctx.request().getAttribute(RequestAttributes.USER_ID);
        if (authorId == null) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }

        CreateTweetRequest req = ctx.body(CreateTweetRequest.class);
        Tweet tweet = tweetService.createTweet(authorId, req.content(), req.parentId());
        ctx.json(HttpStatus.CREATED, tweet);
    }

    @Get("/api/tweets/:id")
    public void getTweet(HttpContext ctx, @PathParam("id") Long id) {
        Tweet tweet = tweetService.getTweet(id);
        if (tweet == null) {
            ctx.status(HttpStatus.NOT_FOUND);
        } else {
            ctx.ok(tweet);
        }
    }

    @Delete("/api/tweets/:id")
    public void deleteTweet(HttpContext ctx, @PathParam("id") Long id) {
        Long requesterId = ctx.request().getAttribute(RequestAttributes.USER_ID);
        if (requesterId == null) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }
        tweetService.deleteTweet(id, requesterId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @Get("/api/tweets/:id/replies")
    public void getReplies(HttpContext ctx, @PathParam("id") Long id, CursorPageable pageable) {
        List<Tweet> replies = tweetService.getReplies(id, pageable.cursor(), pageable.limit());
        ctx.ok(replies);
    }

    @Post("/api/tweets/:id/retweet")
    public void retweet(HttpContext ctx, @PathParam("id") Long id) {
        Long userId = ctx.request().getAttribute(RequestAttributes.USER_ID);
        if (userId == null) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }
        Tweet tweet = tweetService.retweet(id, userId);
        ctx.json(HttpStatus.CREATED, tweet);
    }

    public record QuoteTweetRequest(String content) {}

    @Post("/api/tweets/:id/quote")
    public void quoteTweet(HttpContext ctx, @PathParam("id") Long id) {
        Long userId = ctx.request().getAttribute(RequestAttributes.USER_ID);
        if (userId == null) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }
        QuoteTweetRequest req = ctx.body(QuoteTweetRequest.class);
        Tweet tweet = tweetService.quoteTweet(id, userId, req.content());
        ctx.json(HttpStatus.CREATED, tweet);
    }
}

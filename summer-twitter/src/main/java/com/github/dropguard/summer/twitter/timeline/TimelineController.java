package com.github.dropguard.summer.twitter.timeline;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.twitter.tweet.Tweet;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.RequestAttributes;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.QueryParam;
import com.github.dropguard.summer.web.annotation.RestController;
import java.util.List;

@RestController
@Component
public class TimelineController {

    private final TimelineService timelineService;

    public TimelineController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @Get("/api/timeline")
    public void getTimeline(
            HttpContext ctx,
            @QueryParam("cursor") Long cursor,
            @QueryParam("limit") Integer limit) {
        Long userId = ctx.request().getAttribute(RequestAttributes.USER_ID);
        if (userId == null) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }

        int actualLimit = (limit != null && limit > 0 && limit <= 100) ? limit : 20;
        List<Tweet> timeline = timelineService.getTimeline(userId, cursor, actualLimit);
        ctx.ok(timeline);
    }
}

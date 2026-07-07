package summer.twitter.timeline;

import summer.core.Component;
import summer.web.HttpContext;
import summer.web.HttpStatus;
import summer.web.RequestAttributes;
import summer.web.annotation.Get;
import summer.web.annotation.QueryParam;
import summer.web.annotation.RestController;
import summer.twitter.tweet.Tweet;

import java.util.List;

@RestController
@Component
public class TimelineController {

    private final TimelineService timelineService;

    public TimelineController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @Get("/api/timeline")
    public void getTimeline(HttpContext ctx, @QueryParam("cursor") Long cursor, @QueryParam("limit") Integer limit) {
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

package summer.twitter.timeline;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import summer.data.redis.SummerRedisTemplate;
import summer.twitter.ws.EventPublisher;
import summer.twitter.social.Follow;
import summer.twitter.social.FollowRepository;
import summer.twitter.tweet.Tweet;
import summer.twitter.tweet.TweetRepository;

import io.lettuce.core.api.sync.RedisCommands;

/**
 * Plain unit test for {@link TimelineService}.
 *
 * <p>
 * Behavioral unit test (not {@code @SummerTest}): the only external seam here is
 * Redis, mocked at the {@link SummerRedisTemplate} boundary (a pinned
 * {@link RedisCommands} instance so chained {@code zadd}/{@code zrevrange}
 * calls are verifiable). The application-internal collaborators
 * ({@code TweetRepository}, {@code FollowRepository}) are also mocked. No
 * framework container and no {@code DataSource} — the Redis/JDBC wiring is
 * covered by the integration test against a real Postgres.
 * </p>
 *
 * <p>
 * {@code fanOut} spawns a virtual thread for the follower fan-out when the author
 * has fewer than 5000 followers, which makes assertions on the fan-out
 * non-deterministic from the test thread. Those cases are left to integration
 * coverage; here we exercise the synchronous author-timeline write (follower
 * count ≥ 5000, no virtual thread) and the fully synchronous {@code getTimeline}
 * merge-and-score path.
 * </p>
 */
class TimelineServiceTest {

	private TimelineService timelineService;
	private SummerRedisTemplate mockRedis;
	private RedisCommands<String, Object> mockCommands;
	private TweetRepository mockTweetRepo;
	private FollowRepository mockFollowRepo;
	private EventPublisher mockEventPublisher;

	@BeforeEach
	void setUp() {
		mockRedis = mock(SummerRedisTemplate.class);
		mockTweetRepo = mock(TweetRepository.class);
		mockFollowRepo = mock(FollowRepository.class);
		mockEventPublisher = mock(EventPublisher.class);
		timelineService = new TimelineService(mockRedis, mockTweetRepo, mockFollowRepo, mockEventPublisher);
		// Pin a single RedisCommands mock so getCommands() returns a stable instance
		// across calls (Mockito would otherwise hand back a fresh mock per invocation,
		// breaking verify(...) on the chained zadd/zrevrange).
		mockCommands = mock(RedisCommands.class);
		when(mockRedis.getCommands()).thenReturn(mockCommands);
	}

	@Test
	void fanOutWritesAuthorTimeline() {
		Tweet tweet = new Tweet(10L, 1L, "hi", "POST", null, 0, 0, 0, OffsetDateTime.now());
		// 5000+ followers => synchronous path, no virtual thread
		timelineService.fanOut(tweet, 5000);
		verify(mockCommands).zadd(eq("user:1:tweets"), anyDouble(), eq("10"));
	}

	@Test
	void getTimelineReturnsEmptyWhenNoIds() {
		when(mockCommands.zrevrange("timeline:7", 0, 200)).thenReturn(List.of());
		when(mockFollowRepo.findBigVFollowing(7L, 5000)).thenReturn(List.of());
		List<Tweet> result = timelineService.getTimeline(7L, null, 20);
		assertTrue(result.isEmpty());
	}

	@Test
	void getTimelineMergesAndScoresTweets() {
		when(mockCommands.zrevrange("timeline:7", 0, 200)).thenReturn(List.of("\"10\"", "\"11\""));
		when(mockFollowRepo.findBigVFollowing(7L, 5000)).thenReturn(List.of());
		Tweet t10 = new Tweet(10L, 1L, "a", "POST", null, 5, 0, 0, OffsetDateTime.now().minusMinutes(10));
		Tweet t11 = new Tweet(11L, 2L, "b", "POST", null, 50, 1, 2, OffsetDateTime.now().minusMinutes(1));
		when(mockTweetRepo.findByIds(anyList())).thenReturn(List.of(t10, t11));

		List<Tweet> result = timelineService.getTimeline(7L, null, 20);

		// Higher score (t11) must rank first; ties broken by larger id.
		assertEquals(List.of(t11, t10), result);
		verify(mockTweetRepo).findByIds(anyList());
	}

	@Test
	void getTimelineFallsBackWhenCursorGone() {
		// The cursor is the id of the last tweet on the previous page. It goes stale
		// whenever that tweet is deleted (the common case for an infinite-scroll
		// feed) — so a missing cursor must degrade to the first page, never swallow
		// the whole result set. Here 99L was the prior cursor but is no longer in the
		// merged set; the correct contract returns the head of the feed, not empty.
		when(mockCommands.zrevrange("timeline:7", 0, 200)).thenReturn(List.of("\"10\"", "\"11\""));
		when(mockFollowRepo.findBigVFollowing(7L, 5000)).thenReturn(List.of());
		Tweet t10 = new Tweet(10L, 1L, "a", "POST", null, 5, 0, 0, OffsetDateTime.now().minusMinutes(10));
		Tweet t11 = new Tweet(11L, 2L, "b", "POST", null, 50, 1, 2, OffsetDateTime.now().minusMinutes(1));
		when(mockTweetRepo.findByIds(anyList())).thenReturn(List.of(t10, t11));

		List<Tweet> result = timelineService.getTimeline(7L, 99L, 20);

		// Cursor 99L is absent -> fall back to head of feed (both tweets visible),
		// NOT an empty page.
		assertEquals(2, result.size());
	}
}

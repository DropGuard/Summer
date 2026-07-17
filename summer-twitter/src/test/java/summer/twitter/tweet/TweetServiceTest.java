package summer.twitter.tweet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.test.annotation.Mock;
import summer.test.annotation.SummerTest;
import summer.twitter.infra.SnowflakeIdGenerator;
import summer.twitter.user.User;
import summer.twitter.user.UserRepository;

/**
 * Unit test for {@link TweetService}.
 *
 * <p>
 * Runs under the Quarkus-aligned wide test universe: the full application plus
 * test beans are discovered automatically. Isolation comes from {@code @Mock} on
 * the constructor parameters — each declared mock is created by the framework and
 * replaces the real bean of the same type. No manual bean registration is needed.
 * </p>
 */
@SummerTest
class TweetServiceTest {

	private final TweetService tweetService;
	private final TweetRepository mockTweetRepo;
	private final UserRepository mockUserRepo;

	TweetServiceTest(TweetService tweetService, @Mock TweetRepository mockTweetRepo,
			@Mock UserRepository mockUserRepo, @Mock summer.twitter.timeline.TimelineService timelineService,
			@Mock summer.data.redis.SummerRedisTemplate redisTemplate,
			@Mock summer.twitter.social.FollowRepository followRepository,
			@Mock summer.data.jdbc.JdbcTemplate jdbcTemplate, @Mock javax.sql.DataSource dataSource) {
		this.tweetService = tweetService;
		this.mockTweetRepo = mockTweetRepo;
		this.mockUserRepo = mockUserRepo;
	}

	@Test
	void createTweetReturnsNewId() {
		Tweet result = tweetService.createTweet(1L, "Hello!", null);
		assertNotNull(result);
		assertEquals(1L, result.authorId());
		assertEquals("POST", result.type());
		verify(mockTweetRepo).insert(any());
	}

	@Test
	void createReplyUpdatesParentReplyCount() {
		tweetService.createTweet(1L, "Reply!", 42L);
		verify(mockTweetRepo).updateReplyCount(42L, 1);
	}

	@Test
	void extractsMentions() {
		User alice = new User(100L, "alice", "Alice", "alice@test.com", "hash", "bio", null, null, null);
		when(mockUserRepo.findByUsername("alice")).thenReturn(Optional.of(alice));
		tweetService.createTweet(1L, "Hey @alice", null);
		verify(mockUserRepo).findByUsername("alice");
	}

	@Test
	void getTweetDelegates() {
		Tweet expected = new Tweet(1L, 1L, "test", "POST", null, 0, 0, 0, null);
		when(mockTweetRepo.findById(1L)).thenReturn(expected);
		assertSame(expected, tweetService.getTweet(1L));
	}

	@Test
	void retweetThrowsWhenNotFound() {
		assertThrows(IllegalArgumentException.class, () -> tweetService.retweet(99L, 1L));
	}
}

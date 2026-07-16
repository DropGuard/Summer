package summer.twitter.tweet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.test.Testing;
import summer.twitter.infra.SnowflakeIdGenerator;
import summer.twitter.user.User;
import summer.twitter.user.UserRepository;

class TweetServiceTest {

	private static BeanContainer container;
	private static TweetRepository mockTweetRepo;
	private static UserRepository mockUserRepo;

	private final TweetService tweetService;

	TweetServiceTest() {
		this.tweetService = container.getBean(TweetService.class);
	}

	@BeforeAll
	static void setUp() {
		mockTweetRepo = mock(TweetRepository.class);
		mockUserRepo = mock(UserRepository.class);

		// Full test universe. Mocks are registered as external beans first, so real
		// beans peek() and skip them. The universe is the full test universe
		// (Quarkus-aligned), and isolation comes from the mocks — not from seeds.
		container = Testing.buildWithExternal(
				mockTweetRepo, mockUserRepo,
				mock(summer.twitter.timeline.TimelineService.class),
				mock(summer.data.redis.SummerRedisTemplate.class),
				mock(summer.twitter.social.FollowRepository.class),
				mock(summer.data.jdbc.JdbcTemplate.class),
				mock(javax.sql.DataSource.class));
	}

	@AfterAll
	static void tearDown() throws Exception {
		if (container != null) container.close();
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

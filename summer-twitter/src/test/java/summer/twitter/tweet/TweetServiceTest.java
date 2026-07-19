package summer.twitter.tweet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import summer.twitter.infra.SnowflakeIdGenerator;
import summer.twitter.timeline.TimelineService;
import summer.twitter.user.User;
import summer.twitter.user.UserRepository;

/**
 * Plain unit test for {@link TweetService}.
 *
 * <p>
 * Behavioral unit test (not {@code @SummerTest}): the service's direct
 * collaborators ({@code TweetRepository}, {@code UserRepository},
 * {@code TimelineService}) are mocked and {@link SnowflakeIdGenerator} stays
 * real. No framework container, no {@code DataSource} — the JDBC layer is
 * covered by the integration test against a real Postgres.
 * </p>
 */
class TweetServiceTest {

	private TweetRepository mockTweetRepo;
	private UserRepository mockUserRepo;
	private TimelineService mockTimelineService;
	private TweetService tweetService;

	@BeforeEach
	void setUp() {
		mockTweetRepo = mock(TweetRepository.class);
		mockUserRepo = mock(UserRepository.class);
		mockTimelineService = mock(TimelineService.class);
		tweetService = new TweetService(mockTweetRepo, mockUserRepo, new SnowflakeIdGenerator(), mockTimelineService);
	}

	@Test
	void createTweetReturnsNewId() {
		when(mockUserRepo.findById(1L)).thenReturn(Optional.of(new User(1L, "alice", "Alice", "a@x.com", "h", "bio", 0, 0, null)));
		Tweet result = tweetService.createTweet(1L, "Hello!", null);
		assertNotNull(result);
		assertEquals(1L, result.authorId());
		assertEquals("POST", result.type());
		verify(mockTweetRepo).insert(any());
	}

	@Test
	void createTweetFansOutToTimelines() {
		User author = new User(1L, "alice", "Alice", "a@x.com", "h", "bio", 42, 0, null);
		when(mockUserRepo.findById(1L)).thenReturn(Optional.of(author));
		tweetService.createTweet(1L, "Hello!", null);
		verify(mockTimelineService).fanOut(any(Tweet.class), eq(42));
	}

	@Test
	void createReplyUpdatesParentReplyCount() {
		when(mockUserRepo.findById(1L)).thenReturn(Optional.of(new User(1L, "alice", "Alice", "a@x.com", "h", "bio", 0, 0, null)));
		tweetService.createTweet(1L, "Reply!", 42L);
		verify(mockTweetRepo).updateReplyCount(42L, 1);
	}

	@Test
	void extractsMentions() {
		when(mockUserRepo.findById(1L)).thenReturn(Optional.of(new User(1L, "alice", "Alice", "a@x.com", "h", "bio", 0, 0, null)));
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

	@Test
	void retweetTriggersFanOut() {
		Tweet original = new Tweet(5L, 1L, "orig", "POST", null, 0, 0, 0, null);
		when(mockTweetRepo.findById(5L)).thenReturn(original);
		User retweeter = new User(2L, "bob", "Bob", "b@x.com", "h", "bio", 7, 0, null);
		when(mockUserRepo.findById(2L)).thenReturn(Optional.of(retweeter));

		Tweet result = tweetService.retweet(5L, 2L);

		assertEquals("RETWEET", result.type());
		verify(mockTweetRepo).incrementRetweetCount(5L);
		verify(mockTimelineService).fanOut(any(Tweet.class), eq(7));
	}
}

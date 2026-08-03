package com.github.dropguard.summer.twitter.tweet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.dropguard.summer.twitter.common.BusinessException;
import com.github.dropguard.summer.twitter.common.ResourceNotFoundException;
import com.github.dropguard.summer.twitter.common.UserNotFoundException;
import com.github.dropguard.summer.twitter.infra.SnowflakeIdGenerator;
import com.github.dropguard.summer.twitter.social.LikeRepository;
import com.github.dropguard.summer.twitter.timeline.TimelineService;
import com.github.dropguard.summer.twitter.user.User;
import com.github.dropguard.summer.twitter.user.UserRepository;

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
	private LikeRepository mockLikeRepo;
	private TimelineService mockTimelineService;
	private TweetService tweetService;

	@BeforeEach
	void setUp() {
		mockTweetRepo = mock(TweetRepository.class);
		mockUserRepo = mock(UserRepository.class);
		mockLikeRepo = mock(LikeRepository.class);
		mockTimelineService = mock(TimelineService.class);
		tweetService = new TweetService(mockTweetRepo, mockUserRepo, mockLikeRepo, new SnowflakeIdGenerator(), mockTimelineService);
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
		assertThrows(ResourceNotFoundException.class, () -> tweetService.retweet(99L, 1L));
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

	// --- deleteTweet tests (T2 + T5) ---

	@Test
	void deleteTweetReturns403WhenNotAuthor() {
		Tweet tweet = new Tweet(1L, 2L /* owner */, "x", "POST", null, 0, 0, 0, null);
		when(mockTweetRepo.findById(1L)).thenReturn(tweet);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> tweetService.deleteTweet(1L, 99L /* attacker */));
		assertEquals("forbidden", ex.code());
	}

	@Test
	void deleteTweetThrowsNotFoundWhenMissing() {
		assertThrows(ResourceNotFoundException.class,
				() -> tweetService.deleteTweet(999L, 1L));
	}

	@Test
	void deleteTweetCleansUpLikes() {
		Tweet tweet = new Tweet(1L, 1L, "x", "POST", null, 0, 0, 0, null);
		when(mockTweetRepo.findById(1L)).thenReturn(tweet);
		when(mockTweetRepo.findByParentId(1L)).thenReturn(java.util.List.of());

		tweetService.deleteTweet(1L, 1L);

		verify(mockLikeRepo).deleteByTweetId(1L);
		verify(mockTweetRepo).delete(1L);
	}

	@Test
	void deleteTweetCascadesToReplies() {
		Tweet parent = new Tweet(1L, 1L, "orig", "POST", null, 0, 0, 0, null);
		Tweet reply = new Tweet(2L, 2L, "reply", "POST", 1L, 0, 0, 0, null);
		when(mockTweetRepo.findById(1L)).thenReturn(parent);
		when(mockTweetRepo.findByParentId(1L)).thenReturn(java.util.List.of(reply));

		tweetService.deleteTweet(1L, 1L);

		verify(mockLikeRepo).deleteByTweetId(2L); // reply's likes cleaned
		verify(mockTweetRepo).delete(2L);          // reply deleted
		verify(mockTweetRepo).delete(1L);          // parent deleted
	}

	@Test
	void deleteReplyDecrementsParentReplyCount() {
		Tweet reply = new Tweet(2L, 2L, "reply", "POST", 1L, 0, 0, 0, null);
		when(mockTweetRepo.findById(2L)).thenReturn(reply);
		when(mockTweetRepo.findByParentId(2L)).thenReturn(java.util.List.of());

		tweetService.deleteTweet(2L, 2L);

		verify(mockTweetRepo).updateReplyCount(1L, -1);
	}
}

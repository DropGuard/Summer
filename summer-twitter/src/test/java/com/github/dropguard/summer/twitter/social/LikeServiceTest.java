package com.github.dropguard.summer.twitter.social;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.dropguard.summer.twitter.infra.SnowflakeIdGenerator;
import com.github.dropguard.summer.twitter.tweet.TweetRepository;

/**
 * Plain unit test for {@link LikeService}.
 *
 * <p>
 * This is a behavioral unit test, not a {@code @SummerTest}: the service's only
 * real dependency here is {@link SnowflakeIdGenerator} (a pure in-memory id
 * source with no external coupling), while its application-internal
 * collaborators ({@code LikeRepository}, {@code TweetRepository}) are mocked.
 * There is no framework container, no universe build, and therefore no
 * {@code DataSource} — exactly the shape Quarkus would write as standard JUnit 5
 * + Mockito. The JDBC layer is exercised only by the integration test
 * ({@code TwitterApplicationIT}), which runs against a real Postgres.
 * </p>
 */
class LikeServiceTest {

	private LikeRepository mockLikeRepo;
	private TweetRepository mockTweetRepo;
	private LikeService likeService;

	@BeforeEach
	void setUp() {
		mockLikeRepo = mock(LikeRepository.class);
		mockTweetRepo = mock(TweetRepository.class);
		likeService = new LikeService(mockLikeRepo, mockTweetRepo, new SnowflakeIdGenerator());
	}

	@Test
	void likeInsertsAndIncrementsCount() {
		likeService.like(1L, 100L);
		verify(mockLikeRepo).insert(any(Like.class));
		verify(mockTweetRepo).updateLikeCount(100L, 1);
	}

	@Test
	void likeIsIdempotent() {
		when(mockLikeRepo.exists(1L, 100L)).thenReturn(true);
		likeService.like(1L, 100L);
		verify(mockLikeRepo, never()).insert(any());
		verify(mockTweetRepo, never()).updateLikeCount(anyLong(), anyInt());
	}

	@Test
	void unlikeDeletesAndDecrementsCount() {
		when(mockLikeRepo.exists(1L, 100L)).thenReturn(true);
		likeService.unlike(1L, 100L);
		verify(mockLikeRepo).delete(1L, 100L);
		verify(mockTweetRepo).updateLikeCount(100L, -1);
	}

	@Test
	void unlikeNoopWhenNotLiked() {
		when(mockLikeRepo.exists(1L, 100L)).thenReturn(false);
		likeService.unlike(1L, 100L);
		verify(mockLikeRepo, never()).delete(anyLong(), anyLong());
		verify(mockTweetRepo, never()).updateLikeCount(anyLong(), anyInt());
	}
}

package com.github.dropguard.summer.twitter.social;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.dropguard.summer.twitter.infra.SnowflakeIdGenerator;
import com.github.dropguard.summer.twitter.tweet.TweetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plain unit test for {@link LikeService}.
 *
 * <p>This is a behavioral unit test, not a {@code @SummerTest}: the service's only real dependency
 * here is {@link SnowflakeIdGenerator} (a pure in-memory id source with no external coupling),
 * while its application-internal collaborators ({@code LikeRepository}, {@code TweetRepository})
 * are mocked. There is no framework container, no universe build, and therefore no {@code
 * DataSource} — exactly the shape Quarkus would write as standard JUnit 5 + Mockito. The JDBC layer
 * is exercised only by the integration test ({@code TwitterApplicationIT}), which runs against a
 * real Postgres.
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
        when(mockLikeRepo.insertIfAbsent(any(Like.class))).thenReturn(true);
        likeService.like(1L, 100L);
        verify(mockLikeRepo).insertIfAbsent(any(Like.class));
        verify(mockTweetRepo).updateLikeCount(100L, 1);
    }

    @Test
    void likeIsIdempotent() {
        when(mockLikeRepo.insertIfAbsent(any(Like.class))).thenReturn(false);
        likeService.like(1L, 100L);
        verify(mockLikeRepo).insertIfAbsent(any(Like.class));
        verify(mockTweetRepo, never()).updateLikeCount(anyLong(), anyInt());
    }

    @Test
    void unlikeDeletesAndDecrementsCount() {
        when(mockLikeRepo.deleteByUserAndTweet(1L, 100L)).thenReturn(1);
        likeService.unlike(1L, 100L);
        verify(mockLikeRepo).deleteByUserAndTweet(1L, 100L);
        verify(mockTweetRepo).updateLikeCount(100L, -1);
    }

    @Test
    void unlikeNoopWhenNotLiked() {
        when(mockLikeRepo.deleteByUserAndTweet(1L, 100L)).thenReturn(0);
        likeService.unlike(1L, 100L);
        verify(mockLikeRepo).deleteByUserAndTweet(1L, 100L);
        verify(mockTweetRepo, never()).updateLikeCount(anyLong(), anyInt());
    }
}

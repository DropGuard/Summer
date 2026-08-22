package com.github.dropguard.summer.realworld.user;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.realworld.TestSeeds;
import com.github.dropguard.summer.realworld.article.ArticleRepository;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.test.annotation.TestResource;
import com.github.dropguard.summer.test.db.PostgresTestResource;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for {@link FollowRepository} against real Postgres (Testcontainers) — covers the
 * {@code follows} table behavior (FK constraints, {@code ON CONFLICT DO NOTHING}, ordering) that
 * the Mockito-only {@link FollowServiceTest} does not.
 *
 * <p>Seeds the baseline via {@link com.github.dropguard.summer.realworld.TestSeeds#seedBaseline}
 * (user 1).
 */
@SummerTest
@TestResource(PostgresTestResource.class)
class FollowRepositoryDbTest {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final ArticleRepository articleRepository;
    private final FollowRepository followRepository;

    FollowRepositoryDbTest(
            JdbcTemplate jdbcTemplate,
            UserRepository userRepository,
            ArticleRepository articleRepository,
            FollowRepository followRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.articleRepository = articleRepository;
        this.followRepository = followRepository;
    }

    /** Seed baseline user 1 + article 1, then add a user 2 to be followed. */
    @BeforeEach
    void seedBaselineAndUserTwo() {
        TestSeeds.seedBaseline(jdbcTemplate, userRepository, articleRepository);
        LocalDateTime now = LocalDateTime.now();
        User followee =
                new User(
                        null,
                        "followee",
                        "followee@example.com",
                        "password123",
                        null,
                        null,
                        now,
                        now);
        userRepository.save(followee); // id 2
    }

    private static final Long FOLLOWER = 1L;
    private static final Long FOLLOWEE = 2L;

    @Test
    void shouldFollowAndCheckStatus() {
        followRepository.follow(FOLLOWER, FOLLOWEE);

        assertTrue(followRepository.isFollowing(FOLLOWER, FOLLOWEE));
        assertFalse(followRepository.isFollowing(FOLLOWEE, FOLLOWER)); // not symmetric
    }

    @Test
    void followIsIdempotentOnConflict() {
        followRepository.follow(FOLLOWER, FOLLOWEE);
        followRepository.follow(FOLLOWER, FOLLOWEE); // second insert hits ON CONFLICT DO NOTHING

        assertTrue(followRepository.isFollowing(FOLLOWER, FOLLOWEE));
        assertEquals(1, followRepository.getFollowing(FOLLOWER).size());
    }

    @Test
    void shouldGetFollowing() {
        followRepository.follow(FOLLOWER, FOLLOWEE);

        var following = followRepository.getFollowing(FOLLOWER);

        assertEquals(1, following.size());
        assertTrue(following.contains(FOLLOWEE));
        assertEquals(0, followRepository.getFollowing(FOLLOWEE).size());
    }

    @Test
    void shouldUnfollow() {
        followRepository.follow(FOLLOWER, FOLLOWEE);
        assertTrue(followRepository.isFollowing(FOLLOWER, FOLLOWEE));

        followRepository.unfollow(FOLLOWER, FOLLOWEE);

        assertFalse(followRepository.isFollowing(FOLLOWER, FOLLOWEE));
        assertEquals(0, followRepository.getFollowing(FOLLOWER).size());
    }
}

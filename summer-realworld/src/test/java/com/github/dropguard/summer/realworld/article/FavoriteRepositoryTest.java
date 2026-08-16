package com.github.dropguard.summer.realworld.article;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.realworld.user.UserRepository;
import com.github.dropguard.summer.realworld.TestSeeds;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.test.annotation.TestResource;
import com.github.dropguard.summer.test.db.PostgresTestResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for {@link FavoriteRepository} against real Postgres (Testcontainers).
 *
 * <p>Seeds the baseline via {@link com.github.dropguard.summer.realworld.TestSeeds#seedBaseline} (user 1 /
 * article 1) and declares its own {@code favoriteRepository}.
 */
@SummerTest
@TestResource(PostgresTestResource.class)
class FavoriteRepositoryTest {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final ArticleRepository articleRepository;
    private final FavoriteRepository favoriteRepository;

    FavoriteRepositoryTest(
            JdbcTemplate jdbcTemplate,
            UserRepository userRepository,
            ArticleRepository articleRepository,
            FavoriteRepository favoriteRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.articleRepository = articleRepository;
        this.favoriteRepository = favoriteRepository;
    }

    @BeforeEach
    void seedBaseline() {
        TestSeeds.seedBaseline(jdbcTemplate, userRepository, articleRepository);
    }

    private static final Long USER_1 = 1L;
    private static final Long ARTICLE_1 = 1L;

    @Test
    void shouldFavoriteAndCheckStatus() {
        favoriteRepository.favorite(USER_1, ARTICLE_1);

        assertTrue(favoriteRepository.isFavorited(USER_1, ARTICLE_1));
        assertFalse(favoriteRepository.isFavorited(USER_1, 999L));
    }

    @Test
    void shouldUnfavorite() {
        favoriteRepository.favorite(USER_1, ARTICLE_1);
        assertTrue(favoriteRepository.isFavorited(USER_1, ARTICLE_1));

        favoriteRepository.unfavorite(USER_1, ARTICLE_1);

        assertFalse(favoriteRepository.isFavorited(USER_1, ARTICLE_1));
    }

    @Test
    void shouldCountByArticleId() {
        favoriteRepository.favorite(USER_1, ARTICLE_1);

        assertEquals(1, favoriteRepository.countByArticleId(ARTICLE_1));
        assertEquals(0, favoriteRepository.countByArticleId(999L));
    }

    @Test
    void shouldGetArticleIdsFavoritedByUser() {
        favoriteRepository.favorite(USER_1, ARTICLE_1);

        var ids = favoriteRepository.getArticleIdsFavoritedBy(USER_1);

        assertEquals(1, ids.size());
        assertTrue(ids.contains(ARTICLE_1));
    }

    @Test
    void deleteByArticleIdShouldRemoveAllFavoritesForArticle() {
        favoriteRepository.favorite(USER_1, ARTICLE_1);

        favoriteRepository.deleteByArticleId(ARTICLE_1);

        assertFalse(favoriteRepository.isFavorited(USER_1, ARTICLE_1));
        assertEquals(0, favoriteRepository.countByArticleId(ARTICLE_1));
    }
}

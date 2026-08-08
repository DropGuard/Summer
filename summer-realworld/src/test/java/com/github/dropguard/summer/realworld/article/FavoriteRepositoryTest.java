package com.github.dropguard.summer.realworld.article;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FavoriteRepositoryTest {

    private FavoriteRepository repo;

    @BeforeEach
    void setUp() {
        repo = new FavoriteRepository();
    }

    @Test
    void shouldFavoriteAndCheckStatus() {
        repo.favorite(1L, 10L);

        assertTrue(repo.isFavorited(1L, 10L));
        assertFalse(repo.isFavorited(2L, 10L));
    }

    @Test
    void shouldUnfavorite() {
        repo.favorite(1L, 10L);
        assertTrue(repo.isFavorited(1L, 10L));

        repo.unfavorite(1L, 10L);

        assertFalse(repo.isFavorited(1L, 10L));
    }

    @Test
    void shouldCountByArticleId() {
        repo.favorite(1L, 10L);
        repo.favorite(2L, 10L);
        repo.favorite(3L, 20L);

        assertEquals(2, repo.countByArticleId(10L));
        assertEquals(1, repo.countByArticleId(20L));
        assertEquals(0, repo.countByArticleId(99L));
    }

    @Test
    void shouldGetArticleIdsFavoritedByUser() {
        repo.favorite(1L, 10L);
        repo.favorite(1L, 20L);
        repo.favorite(2L, 10L);

        var ids = repo.getArticleIdsFavoritedBy(1L);

        assertEquals(2, ids.size());
        assertTrue(ids.contains(10L));
        assertTrue(ids.contains(20L));
    }

    @Test
    void deleteByArticleIdShouldRemoveAllFavoritesForArticle() {
        repo.favorite(1L, 10L);
        repo.favorite(2L, 10L);
        repo.favorite(1L, 20L);

        repo.deleteByArticleId(10L);

        assertFalse(repo.isFavorited(1L, 10L));
        assertFalse(repo.isFavorited(2L, 10L));
        assertTrue(repo.isFavorited(1L, 20L)); // survives
        assertEquals(0, repo.countByArticleId(10L));
        assertEquals(1, repo.countByArticleId(20L));
    }
}

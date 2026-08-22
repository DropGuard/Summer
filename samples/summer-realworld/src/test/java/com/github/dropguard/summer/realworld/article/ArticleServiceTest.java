package com.github.dropguard.summer.realworld.article;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.realworld.TestSeeds;
import com.github.dropguard.summer.realworld.comment.Comment;
import com.github.dropguard.summer.realworld.comment.CommentRepository;
import com.github.dropguard.summer.realworld.user.UserRepository;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.test.annotation.TestResource;
import com.github.dropguard.summer.test.db.PostgresTestResource;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for {@link ArticleService} against real Postgres (Testcontainers).
 *
 * <p>Seeds the baseline via {@link com.github.dropguard.summer.realworld.TestSeeds#seedBaseline}
 * (user 1 / article 1). The baseline article belongs to author 1, so count assertions account for
 * it.
 */
@SummerTest
@TestResource(PostgresTestResource.class)
class ArticleServiceTest {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final ArticleRepository articleRepository;
    private final ArticleService articleService;
    private final FavoriteRepository favoriteRepository;
    private final CommentRepository commentRepository;

    ArticleServiceTest(
            JdbcTemplate jdbcTemplate,
            UserRepository userRepository,
            ArticleRepository articleRepository,
            ArticleService articleService,
            FavoriteRepository favoriteRepository,
            CommentRepository commentRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.articleRepository = articleRepository;
        this.articleService = articleService;
        this.favoriteRepository = favoriteRepository;
        this.commentRepository = commentRepository;
    }

    @BeforeEach
    void seedBaseline() {
        TestSeeds.seedBaseline(jdbcTemplate, userRepository, articleRepository);
    }

    private static final Long AUTHOR_1 = 1L;

    @Test
    void shouldCreateArticle() {
        Article article =
                articleService.create(
                        "How to train your dragon",
                        "Ever wonder how?",
                        "You have to believe",
                        List.of("dragons", "training"),
                        AUTHOR_1);

        assertNotNull(article);
        assertEquals("How to train your dragon", article.title());
        assertEquals("Ever wonder how?", article.description());
        assertEquals("You have to believe", article.body());
        assertEquals(AUTHOR_1, article.authorId());
        assertNotNull(article.slug());
        assertNotNull(article.createdAt());
        assertNotNull(article.updatedAt());

        // Re-query the DB to prove the article (and its tags) were actually persisted.
        var persisted = articleService.findBySlug(article.slug());
        assertTrue(persisted.isPresent());
        assertEquals("How to train your dragon", persisted.get().title());
        assertEquals(AUTHOR_1, persisted.get().authorId());
        assertEquals(
                List.of("dragons", "training"), articleRepository.findTags(persisted.get().id()));
    }

    @Test
    void shouldGenerateSlugFromTitle() {
        Article article =
                articleService.create(
                        "How to Train Your Dragon!", "Description", "Body", List.of(), AUTHOR_1);

        assertEquals("how-to-train-your-dragon", article.slug());
    }

    @Test
    void shouldFindBySlug() {
        articleService.create("Test Article", "Desc", "Body", List.of(), AUTHOR_1);

        var found = articleService.findBySlug("test-article");

        assertTrue(found.isPresent());
        assertEquals("Test Article", found.get().title());
    }

    @Test
    void shouldFindByAuthorId() {
        articleService.create("Article 1", "Desc", "Body", List.of(), AUTHOR_1);
        articleService.create("Article 2", "Desc", "Body", List.of(), AUTHOR_1);
        articleService.create("Article 3", "Desc", "Body", List.of(), AUTHOR_1);

        // 3 created + 1 baseline seed article (author 1)
        List<Article> articles = articleService.findByAuthorId(AUTHOR_1);

        assertEquals(4, articles.size());
        assertEquals(0, articleService.findByAuthorId(999L).size());
    }

    @Test
    void shouldFindByTag() {
        articleService.create("Article 1", "Desc", "Body", List.of("java"), AUTHOR_1);
        articleService.create("Article 2", "Desc", "Body", List.of("python"), AUTHOR_1);
        articleService.create("Article 3", "Desc", "Body", List.of("java", "python"), AUTHOR_1);

        List<Article> javaArticles = articleService.findByTag("java");
        List<Article> pythonArticles = articleService.findByTag("python");

        assertEquals(2, javaArticles.size());
        assertEquals(2, pythonArticles.size());
    }

    @Test
    void shouldDeleteArticle() {
        Article article = articleService.create("Test", "Desc", "Body", List.of(), AUTHOR_1);
        // 1 created + 1 baseline seed article
        assertEquals(2, articleService.findAll().size());

        articleService.delete(article.id());

        assertEquals(1, articleService.findAll().size());
    }

    @Test
    void shouldUpdateArticle() {
        Article article = articleService.create("Old Title", "Desc", "Body", List.of(), AUTHOR_1);

        Article updated = articleService.update(article, "New Title", null, null, null);

        assertEquals("New Title", updated.title());
        assertEquals("new-title", updated.slug());
    }

    @Test
    void deleteArticleShouldCascadeToFavoritesAndComments() {
        Article article = articleService.create("Test", "Desc", "Body", List.of(), AUTHOR_1);

        // Add favorites and comments tied to this article (user 1 is the FK-valid author).
        favoriteRepository.favorite(AUTHOR_1, article.id());
        commentRepository.save(
                new Comment(
                        null,
                        "c1",
                        article.id(),
                        AUTHOR_1,
                        LocalDateTime.now(),
                        LocalDateTime.now()));

        articleService.delete(article.id());

        // Article gone
        assertTrue(articleRepository.findById(article.id()).isEmpty());
        // Favorites for this article gone
        assertEquals(0, favoriteRepository.countByArticleId(article.id()));
        // Comments for this article gone
        assertEquals(0, commentRepository.findByArticleId(article.id()).size());
    }
}

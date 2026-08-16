package com.github.dropguard.summer.realworld.comment;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.realworld.article.ArticleRepository;
import com.github.dropguard.summer.realworld.TestSeeds;
import com.github.dropguard.summer.realworld.user.UserRepository;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.test.annotation.TestResource;
import com.github.dropguard.summer.test.db.PostgresTestResource;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for {@link CommentRepository} against real Postgres (Testcontainers).
 *
 * <p>Seeds the baseline via {@link com.github.dropguard.summer.realworld.TestSeeds#seedBaseline} (user 1 /
 * article 1) and declares its own {@code commentRepository}.
 */
@SummerTest
@TestResource(PostgresTestResource.class)
class CommentRepositoryTest {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final ArticleRepository articleRepository;
    private final CommentRepository commentRepository;

    CommentRepositoryTest(
            JdbcTemplate jdbcTemplate,
            UserRepository userRepository,
            ArticleRepository articleRepository,
            CommentRepository commentRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.articleRepository = articleRepository;
        this.commentRepository = commentRepository;
    }

    @BeforeEach
    void seedBaseline() {
        TestSeeds.seedBaseline(jdbcTemplate, userRepository, articleRepository);
    }

    private static final Long ARTICLE_1 = 1L;
    private static final Long AUTHOR_1 = 1L;

    private static Comment comment(String body) {
        LocalDateTime now = LocalDateTime.now();
        return new Comment(null, body, ARTICLE_1, AUTHOR_1, now, now);
    }

    @Test
    void shouldSaveAndFindById() {
        Comment saved = commentRepository.save(comment("body"));

        assertNotNull(saved.id());
        var found = commentRepository.findById(saved.id());
        assertTrue(found.isPresent());
        assertEquals("body", found.get().body());
    }

    @Test
    void shouldFindByArticleId() {
        commentRepository.save(comment("c1"));
        commentRepository.save(comment("c2"));
        commentRepository.save(comment("c3"));

        assertEquals(3, commentRepository.findByArticleId(ARTICLE_1).size());
        assertEquals(0, commentRepository.findByArticleId(999L).size());
    }

    @Test
    void shouldDeleteById() {
        Comment saved = commentRepository.save(comment("x"));
        assertEquals(1, commentRepository.findAll().size());

        commentRepository.deleteById(saved.id());

        assertEquals(0, commentRepository.findAll().size());
    }

    @Test
    void deleteByArticleIdShouldRemoveOnlyMatchingComments() {
        commentRepository.save(comment("c1"));
        commentRepository.save(comment("c2"));
        commentRepository.save(comment("c3"));

        commentRepository.deleteByArticleId(ARTICLE_1);

        assertEquals(0, commentRepository.findByArticleId(ARTICLE_1).size());
        assertEquals(0, commentRepository.findAll().size());
    }

    @Test
    void deleteByArticleIdShouldBeNoopWhenArticleHasNoComments() {
        commentRepository.save(comment("c"));

        commentRepository.deleteByArticleId(999L); // no comments for this article

        assertEquals(1, commentRepository.findAll().size());
    }
}

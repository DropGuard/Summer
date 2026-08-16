package com.github.dropguard.summer.realworld.comment;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.realworld.TestSeeds;
import com.github.dropguard.summer.realworld.article.ArticleRepository;
import com.github.dropguard.summer.realworld.user.UserRepository;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.test.annotation.TestResource;
import com.github.dropguard.summer.test.db.PostgresTestResource;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for {@link CommentService} against real Postgres (Testcontainers).
 *
 * <p>Seeds the baseline via {@link com.github.dropguard.summer.realworld.TestSeeds#seedBaseline}
 * (user 1 / article 1) and declares its own {@code commentService} + {@code commentRepository}.
 */
@SummerTest
@TestResource(PostgresTestResource.class)
class CommentServiceTest {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final ArticleRepository articleRepository;
    private final CommentService commentService;
    private final CommentRepository commentRepository;

    CommentServiceTest(
            JdbcTemplate jdbcTemplate,
            UserRepository userRepository,
            ArticleRepository articleRepository,
            CommentService commentService,
            CommentRepository commentRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.articleRepository = articleRepository;
        this.commentService = commentService;
        this.commentRepository = commentRepository;
    }

    @BeforeEach
    void seedBaseline() {
        TestSeeds.seedBaseline(jdbcTemplate, userRepository, articleRepository);
    }

    private static final Long ARTICLE_1 = 1L;
    private static final Long AUTHOR_1 = 1L;

    @Test
    void shouldCreateComment() {
        Comment comment = commentService.create("Great article!", ARTICLE_1, AUTHOR_1);

        assertNotNull(comment);
        assertEquals("Great article!", comment.body());
        assertEquals(ARTICLE_1, comment.articleId());
        assertEquals(AUTHOR_1, comment.authorId());
        assertNotNull(comment.createdAt());
        assertNotNull(comment.updatedAt());

        // Re-query the DB to prove the comment was actually persisted.
        var persisted = commentService.findByArticleId(ARTICLE_1);
        assertEquals(1, persisted.size());
        assertEquals("Great article!", persisted.get(0).body());
        assertEquals(comment.id(), persisted.get(0).id());
    }

    @Test
    void shouldFindByArticleId() {
        commentService.create("Comment 1", ARTICLE_1, AUTHOR_1);
        commentService.create("Comment 2", ARTICLE_1, AUTHOR_1);
        commentService.create("Comment 3", ARTICLE_1, AUTHOR_1);

        List<Comment> comments = commentService.findByArticleId(ARTICLE_1);

        assertEquals(3, comments.size());
        assertEquals(0, commentService.findByArticleId(999L).size());
    }

    @Test
    void shouldDeleteComment() {
        Comment comment = commentService.create("Test comment", ARTICLE_1, AUTHOR_1);
        assertEquals(1, commentRepository.findAll().size());

        commentService.delete(comment.id());

        assertEquals(0, commentRepository.findAll().size());
    }
}

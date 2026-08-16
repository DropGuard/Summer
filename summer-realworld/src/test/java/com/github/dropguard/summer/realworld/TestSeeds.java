package com.github.dropguard.summer.realworld;

import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.realworld.article.Article;
import com.github.dropguard.summer.realworld.article.ArticleRepository;
import com.github.dropguard.summer.realworld.user.User;
import com.github.dropguard.summer.realworld.user.UserRepository;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Shared seed logic for the RealWorld DB integration tests. A plain composition helper (not
 * inheritance): each test declares {@code @SummerTest} + {@code @TestResource(Postgres)} and calls
 * {@link #seedBaseline} from its own {@code @BeforeEach} — no abstract base class, no super().
 *
 * <p>Baseline: clears all tables and seeds {@code user 1} + {@code article 1}, the rows the
 * foreign-keyed repositories (comments/favorites/follows) depend on.
 */
public final class TestSeeds {

    private TestSeeds() {}

    /**
     * Clears all tables and seeds baseline user 1 + article 1 (author 1).
     *
     * @return the seeded author (id 1), for tests that need the baseline user's id.
     */
    public static User seedBaseline(
            JdbcTemplate jdbcTemplate,
            UserRepository userRepository,
            ArticleRepository articleRepository) {
        jdbcTemplate.update(
                "TRUNCATE TABLE comments, favorites, follows, articles, users RESTART IDENTITY"
                        + " CASCADE");

        LocalDateTime now = LocalDateTime.now();
        User author =
                new User(null, "author", "author@example.com", "password123", null, null, now, now);
        User savedAuthor = userRepository.save(author); // id 1

        Article article =
                new Article(
                        null,
                        "first-article",
                        "First Article",
                        "A description",
                        "The body",
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        savedAuthor.id());
        articleRepository.save(article, List.of()); // id 1
        return savedAuthor;
    }
}

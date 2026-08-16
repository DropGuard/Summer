package com.github.dropguard.summer.realworld.article;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ArticleRepository {
    private final JdbcTemplate jdbcTemplate;

    private static final String COLUMNS =
            "id, slug, title, description, body, created_at, updated_at, author_id";

    public ArticleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Article save(Article article, List<String> tags) {
        if (article.id() == null) {
            Long id =
                    jdbcTemplate.queryForObject(
                            "INSERT INTO articles (slug, title, description, body, created_at,"
                                    + " updated_at, author_id) VALUES (?, ?, ?, ?, ?, ?, ?)"
                                    + " RETURNING id",
                            Long.class,
                            article.slug(),
                            article.title(),
                            article.description(),
                            article.body(),
                            article.createdAt(),
                            article.updatedAt(),
                            article.authorId());
            article =
                    new Article(
                            id,
                            article.slug(),
                            article.title(),
                            article.description(),
                            article.body(),
                            article.createdAt(),
                            article.updatedAt(),
                            article.authorId());
        } else {
            jdbcTemplate.update(
                    "UPDATE articles SET slug = ?, title = ?, description = ?, body = ?,"
                            + " created_at = ?, updated_at = ?, author_id = ? WHERE id = ?",
                    article.slug(),
                    article.title(),
                    article.description(),
                    article.body(),
                    article.createdAt(),
                    article.updatedAt(),
                    article.authorId(),
                    article.id());
        }
        syncTags(article.id(), tags);
        return article;
    }

    public Optional<Article> findById(Long id) {
        return Optional.ofNullable(
                jdbcTemplate.queryForObject(
                        "SELECT " + COLUMNS + " FROM articles WHERE id = ?", Article.class, id));
    }

    public Optional<Article> findBySlug(String slug) {
        return Optional.ofNullable(
                jdbcTemplate.queryForObject(
                        "SELECT " + COLUMNS + " FROM articles WHERE slug = ?",
                        Article.class,
                        slug));
    }

    public List<Article> findAll() {
        return jdbcTemplate.queryForList(
                "SELECT " + COLUMNS + " FROM articles ORDER BY id", Article.class);
    }

    public List<Article> findByAuthorId(Long authorId) {
        return jdbcTemplate.queryForList(
                "SELECT " + COLUMNS + " FROM articles WHERE author_id = ? ORDER BY id",
                Article.class,
                authorId);
    }

    public List<Article> findByTag(String tag) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT a.id, a.slug, a.title, a.description, a.body,"
                        + " a.created_at, a.updated_at, a.author_id"
                        + " FROM articles a JOIN article_tags at ON at.article_id = a.id"
                        + " JOIN tags t ON t.id = at.tag_id WHERE t.name = ?"
                        + " ORDER BY a.created_at DESC",
                Article.class,
                tag);
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM articles WHERE id = ?", id);
    }

    /** The ordered tag names for an article (from {@code article_tags} + {@code tags}). */
    public List<String> findTags(Long articleId) {
        List<String> tags =
                jdbcTemplate.queryForList(
                        "SELECT t.name FROM tags t JOIN article_tags at ON at.tag_id = t.id"
                                + " WHERE at.article_id = ? ORDER BY at.position",
                        String.class,
                        articleId);
        return tags == null ? new ArrayList<>() : tags;
    }

    private void syncTags(Long articleId, List<String> tags) {
        jdbcTemplate.update("DELETE FROM article_tags WHERE article_id = ?", articleId);
        if (tags == null || tags.isEmpty()) {
            return;
        }
        int position = 0;
        for (String tag : tags) {
            Long tagId =
                    jdbcTemplate.queryForObject(
                            "INSERT INTO tags (name) VALUES (?) ON CONFLICT (name) DO UPDATE SET"
                                    + " name = EXCLUDED.name RETURNING id",
                            Long.class,
                            tag);
            jdbcTemplate.update(
                    "INSERT INTO article_tags (article_id, tag_id, position) VALUES (?, ?, ?)"
                            + " ON CONFLICT DO NOTHING",
                    articleId,
                    tagId,
                    position++);
        }
    }
}

package com.github.dropguard.summer.realworld.article;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.realworld.comment.CommentRepository;
import com.github.dropguard.summer.realworld.common.ValidationException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class ArticleService {
    private final ArticleRepository articleRepository;
    private final FavoriteRepository favoriteRepository;
    private final CommentRepository commentRepository;

    public ArticleService(
            ArticleRepository articleRepository,
            FavoriteRepository favoriteRepository,
            CommentRepository commentRepository) {
        this.articleRepository = articleRepository;
        this.favoriteRepository = favoriteRepository;
        this.commentRepository = commentRepository;
    }

    public Article create(
            String title, String description, String body, List<String> tagList, Long authorId) {
        if (title == null || title.isBlank()) {
            throw new ValidationException("title", "can't be blank");
        }
        if (description == null || description.isBlank()) {
            throw new ValidationException("description", "can't be blank");
        }
        if (body == null || body.isBlank()) {
            throw new ValidationException("body", "can't be blank");
        }

        String slug = generateUniqueSlug(title);
        LocalDateTime now = LocalDateTime.now();
        Article article = new Article(null, slug, title, description, body, now, now, authorId);
        return articleRepository.save(article, tagList != null ? tagList : List.of());
    }

    public Optional<Article> findBySlug(String slug) {
        return articleRepository.findBySlug(slug);
    }

    public Optional<Article> findById(Long id) {
        return articleRepository.findById(id);
    }

    public List<Article> findAll() {
        return articleRepository.findAll();
    }

    public List<Article> findByAuthorId(Long authorId) {
        return articleRepository.findByAuthorId(authorId);
    }

    public List<Article> findByTag(String tag) {
        return articleRepository.findByTag(tag);
    }

    public Article update(
            Article article, String title, String description, String body, List<String> tagList) {
        String newTitle = article.title();
        String newSlug = article.slug();
        String newDescription = article.description();
        String newBody = article.body();
        List<String> newTagList = tagList;

        if (title != null) {
            if (title.isBlank()) {
                throw new ValidationException("title", "can't be blank");
            }
            newTitle = title;
            newSlug = generateUniqueSlug(title);
        }
        if (description != null) {
            if (description.isBlank()) {
                throw new ValidationException("description", "can't be blank");
            }
            newDescription = description;
        }
        if (body != null) {
            if (body.isBlank()) {
                throw new ValidationException("body", "can't be blank");
            }
            newBody = body;
        }

        Article updated =
                new Article(
                        article.id(),
                        newSlug,
                        newTitle,
                        newDescription,
                        newBody,
                        article.createdAt(),
                        LocalDateTime.now(),
                        article.authorId());
        return articleRepository.save(updated, newTagList);
    }

    public void delete(Long id) {
        favoriteRepository.deleteByArticleId(id);
        commentRepository.deleteByArticleId(id);
        articleRepository.deleteById(id);
    }

    private String generateUniqueSlug(String title) {
        String baseSlug = slugify(title);
        String slug = baseSlug;
        int counter = 1;
        while (articleRepository.findBySlug(slug).isPresent()) {
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    private String slugify(String title) {
        return title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}

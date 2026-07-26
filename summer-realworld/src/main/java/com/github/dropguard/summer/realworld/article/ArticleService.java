package com.github.dropguard.summer.realworld.article;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import com.github.dropguard.summer.realworld.article.*;
import com.github.dropguard.summer.realworld.common.ValidationException;
import com.github.dropguard.summer.realworld.common.ConflictException;
import com.github.dropguard.summer.realworld.article.*;

public class ArticleService {
	private final ArticleRepository articleRepository;
	private final AtomicLong slugCounter = new AtomicLong(1);

	public ArticleService(ArticleRepository articleRepository) {
		this.articleRepository = articleRepository;
	}

	public Article create(String title, String description, String body, List<String> tagList, Long authorId) {
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
		Article article = new Article();
		article.setSlug(slug);
		article.setTitle(title);
		article.setDescription(description);
		article.setBody(body);
		article.setTagList(tagList != null ? tagList : List.of());
		article.setAuthorId(authorId);
		article.setCreatedAt(LocalDateTime.now());
		article.setUpdatedAt(LocalDateTime.now());
		article.setFavoritesCount(0);
		return articleRepository.save(article);
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

	public Article update(Article article, String title, String description, String body, List<String> tagList) {
		if (title != null) {
			if (title.isBlank()) {
				throw new ValidationException("title", "can't be blank");
			}
			article.setTitle(title);
			article.setSlug(generateUniqueSlug(title));
		}
		if (description != null) {
			if (description.isBlank()) {
				throw new ValidationException("description", "can't be blank");
			}
			article.setDescription(description);
		}
		if (body != null) {
			if (body.isBlank()) {
				throw new ValidationException("body", "can't be blank");
			}
			article.setBody(body);
		}
		if (tagList != null) {
			article.setTagList(tagList);
		}
		article.setUpdatedAt(LocalDateTime.now());
		return articleRepository.save(article);
	}

	public void delete(Long id) {
		articleRepository.deleteById(id);
	}

	private String generateUniqueSlug(String title) {
		String baseSlug = slugify(title);
		String slug = baseSlug;
		while (articleRepository.findBySlug(slug).isPresent()) {
			slug = baseSlug + "-" + slugCounter.getAndIncrement();
		}
		return slug;
	}

	private String slugify(String title) {
		return title.toLowerCase().replaceAll("[^a-z0-9\\s-]", "").replaceAll("\\s+", "-").replaceAll("-+", "-")
				.replaceAll("^-|-$", "");
	}

}

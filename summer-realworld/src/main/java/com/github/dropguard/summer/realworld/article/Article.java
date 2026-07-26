package com.github.dropguard.summer.realworld.article;

import java.time.LocalDateTime;
import java.util.List;

public class Article {
	private Long id;
	private String slug;
	private String title;
	private String description;
	private String body;
	private List<String> tagList;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
	private Long authorId;
	private int favoritesCount;

	public Article() {
	}

	public Article(Long id, String slug, String title, String description, String body, List<String> tagList,
			LocalDateTime createdAt, LocalDateTime updatedAt, Long authorId, int favoritesCount) {
		this.id = id;
		this.slug = slug;
		this.title = title;
		this.description = description;
		this.body = body;
		this.tagList = tagList;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.authorId = authorId;
		this.favoritesCount = favoritesCount;
	}

	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}

	public String getSlug() {
		return slug;
	}
	public void setSlug(String slug) {
		this.slug = slug;
	}

	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}

	public String getBody() {
		return body;
	}
	public void setBody(String body) {
		this.body = body;
	}

	public List<String> getTagList() {
		return tagList;
	}
	public void setTagList(List<String> tagList) {
		this.tagList = tagList;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Long getAuthorId() {
		return authorId;
	}
	public void setAuthorId(Long authorId) {
		this.authorId = authorId;
	}

	public int getFavoritesCount() {
		return favoritesCount;
	}
	public void setFavoritesCount(int favoritesCount) {
		this.favoritesCount = favoritesCount;
	}
}

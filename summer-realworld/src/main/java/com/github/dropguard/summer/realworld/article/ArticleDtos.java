package com.github.dropguard.summer.realworld.article;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public class ArticleDtos {

	public record CreateArticleRequest(Article article) {
		public record Article(
				@jakarta.validation.constraints.NotBlank String title,
				@jakarta.validation.constraints.NotBlank String description,
				@jakarta.validation.constraints.NotBlank String body,
				java.util.List<String> tagList) {
		}
	}

	// title/description are optional here → null means "don't update"
	public record UpdateArticleRequest(Article article) {
		public record Article(String title, String description,
				@jakarta.validation.constraints.NotBlank String body,
				java.util.List<String> tagList) {
		}
	}

	// Shared article data type
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ArticleData(String slug, String title, String description, String body,
			List<String> tagList, String createdAt, String updatedAt,
			boolean favorited, int favoritesCount, Author author) {
	}

	public record Author(String username, String bio, String image, boolean following) {
	}

	// Single article response
	public record ArticleResponse(ArticleData article) {
	}

	// Multiple articles response
	public record ArticlesResponse(List<ArticleData> articles, int articlesCount) {
	}
}

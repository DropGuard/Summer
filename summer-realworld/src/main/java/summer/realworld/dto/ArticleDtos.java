package summer.realworld.dto;

import java.util.List;

public class ArticleDtos {

	public record CreateArticleRequest(Article article) {
		public record Article(String title, String description, String body, List<String> tagList) {
		}
	}

	public record UpdateArticleRequest(Article article) {
		public record Article(String title, String description, String body, List<String> tagList) {
		}
	}

	// Shared article data type
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

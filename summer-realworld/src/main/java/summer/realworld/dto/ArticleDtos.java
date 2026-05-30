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
}

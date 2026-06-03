package summer.realworld.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import summer.realworld.model.Article;
import summer.realworld.service.ArticleService;
import summer.web.HttpContext;
import summer.web.HttpStatus;
import summer.web.annotation.Get;
import summer.web.annotation.RestController;

@RestController("/api")
public class TagController {
	private final ArticleService articleService;

	public TagController(ArticleService articleService) {
		this.articleService = articleService;
	}

	@Get("/tags")
	public void getTags(HttpContext ctx) {
		List<Article> articles = articleService.findAll();
		Set<String> tags = articles.stream().filter(article -> article.getTagList() != null)
				.flatMap(article -> article.getTagList().stream()).collect(Collectors.toSet());

		ctx.json(HttpStatus.OK, Map.of("tags", tags));
	}
}

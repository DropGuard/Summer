package com.github.dropguard.summer.realworld.tag;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.dropguard.summer.realworld.article.Article;
import com.github.dropguard.summer.realworld.article.ArticleService;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.RestController;

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

		ctx.json(HttpStatus.OK, new TagsResponse(tags));
	}

	private record TagsResponse(Set<String> tags) {
	}
}

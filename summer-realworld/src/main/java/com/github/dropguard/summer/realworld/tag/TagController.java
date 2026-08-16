package com.github.dropguard.summer.realworld.tag;

import com.github.dropguard.summer.realworld.article.Article;
import com.github.dropguard.summer.realworld.article.ArticleRepository;
import com.github.dropguard.summer.realworld.article.ArticleService;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.RestController;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController("/api")
public class TagController {
    private final ArticleService articleService;
    private final ArticleRepository articleRepository;

    public TagController(ArticleService articleService, ArticleRepository articleRepository) {
        this.articleService = articleService;
        this.articleRepository = articleRepository;
    }

    @Get("/tags")
    public void getTags(HttpContext ctx) {
        List<Article> articles = articleService.findAll();
        Set<String> tags =
                articles.stream()
                        .filter(article -> !articleRepository.findTags(article.id()).isEmpty())
                        .flatMap(article -> articleRepository.findTags(article.id()).stream())
                        .collect(Collectors.toSet());

        ctx.json(HttpStatus.OK, new TagsResponse(tags));
    }

    private record TagsResponse(Set<String> tags) {}
}

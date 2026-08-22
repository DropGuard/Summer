package com.github.dropguard.summer.realworld.tag;

import com.github.dropguard.summer.realworld.article.ArticleRepository;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.RestController;
import java.util.HashSet;
import java.util.Set;

@RestController("/api")
public class TagController {
    private final ArticleRepository articleRepository;

    public TagController(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    @Get("/tags")
    public void getTags(HttpContext ctx) {
        // Single query for all distinct tag names — no more findAll() + per-article
        // findTags() loop (N+1).
        Set<String> tags = new HashSet<>(articleRepository.findAllDistinctTagNames());

        ctx.json(HttpStatus.OK, new TagsResponse(tags));
    }

    private record TagsResponse(Set<String> tags) {}
}

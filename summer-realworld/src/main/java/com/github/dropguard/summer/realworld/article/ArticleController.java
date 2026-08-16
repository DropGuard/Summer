package com.github.dropguard.summer.realworld.article;

import com.github.dropguard.summer.core.data.LimitOffsetPageRequest;
import com.github.dropguard.summer.realworld.article.ArticleDtos.ArticleData;
import com.github.dropguard.summer.realworld.article.ArticleDtos.ArticleResponse;
import com.github.dropguard.summer.realworld.article.ArticleDtos.ArticlesResponse;
import com.github.dropguard.summer.realworld.article.ArticleDtos.Author;
import com.github.dropguard.summer.realworld.auth.AuthUtils;
import com.github.dropguard.summer.realworld.auth.JwtUtil;
import com.github.dropguard.summer.realworld.common.Errors;
import com.github.dropguard.summer.realworld.user.FollowRepository;
import com.github.dropguard.summer.realworld.user.User;
import com.github.dropguard.summer.realworld.user.UserService;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.Delete;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.PathParam;
import com.github.dropguard.summer.web.annotation.Post;
import com.github.dropguard.summer.web.annotation.Put;
import com.github.dropguard.summer.web.annotation.RestController;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController("/api")
public class ArticleController {
    private final ArticleService articleService;
    private final UserService userService;
    private final FavoriteRepository favoriteRepository;
    private final FollowRepository followRepository;
    private final ArticleRepository articleRepository;
    private final JwtUtil jwtUtil;

    public ArticleController(
            ArticleService articleService,
            UserService userService,
            FavoriteRepository favoriteRepository,
            FollowRepository followRepository,
            ArticleRepository articleRepository,
            JwtUtil jwtUtil) {
        this.articleService = articleService;
        this.userService = userService;
        this.favoriteRepository = favoriteRepository;
        this.followRepository = followRepository;
        this.articleRepository = articleRepository;
        this.jwtUtil = jwtUtil;
    }

    @Get("/articles")
    public void listArticles(HttpContext ctx, LimitOffsetPageRequest pageable) {
        String tag = ctx.queryParam("tag");
        String author = ctx.queryParam("author");
        String favorited = ctx.queryParam("favorited");

        List<Article> articles;
        if (tag != null) {
            articles = articleService.findByTag(tag);
        } else if (author != null) {
            Optional<User> authorOpt = userService.findByUsername(author);
            articles =
                    authorOpt
                            .map(user -> articleService.findByAuthorId(user.id()))
                            .orElse(List.of());
        } else if (favorited != null) {
            Optional<User> favUserOpt = userService.findByUsername(favorited);
            if (favUserOpt.isPresent()) {
                Set<Long> favArticleIds =
                        favoriteRepository.getArticleIdsFavoritedBy(favUserOpt.get().id());
                articles =
                        articleService.findAll().stream()
                                .filter(a -> favArticleIds.contains(a.id()))
                                .collect(Collectors.toList());
            } else {
                articles = List.of();
            }
        } else {
            articles = articleService.findAll();
        }

        articles = new ArrayList<>(articles);
        articles.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));

        Long currentUserId = tryGetCurrentUserId(ctx);
        int total = articles.size();
        int fromIndex = Math.min(pageable.offset(), articles.size());
        int toIndex = Math.min(pageable.offset() + pageable.limit(), articles.size());
        List<Article> paginatedArticles = articles.subList(fromIndex, toIndex);

        List<ArticleData> articleResponses =
                paginatedArticles.stream()
                        .map(a -> createArticleData(a, currentUserId, false))
                        .collect(Collectors.toList());

        ctx.json(HttpStatus.OK, new ArticlesResponse(articleResponses, total));
    }

    @Get("/articles/feed")
    public void feedArticles(HttpContext ctx, LimitOffsetPageRequest pageable) {
        Long currentUserId = getCurrentUserId(ctx);

        Set<Long> followingIds = followRepository.getFollowing(currentUserId);
        List<Article> articles =
                articleService.findAll().stream()
                        .filter(a -> followingIds.contains(a.authorId()))
                        .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                        .collect(Collectors.toList());

        int total = articles.size();
        int fromIndex = Math.min(pageable.offset(), articles.size());
        int toIndex = Math.min(pageable.offset() + pageable.limit(), articles.size());
        List<Article> paginatedArticles = articles.subList(fromIndex, toIndex);

        List<ArticleData> articleResponses =
                paginatedArticles.stream()
                        .map(a -> createArticleData(a, currentUserId, false))
                        .collect(Collectors.toList());

        ctx.json(HttpStatus.OK, new ArticlesResponse(articleResponses, total));
    }

    @Get("/articles/{slug}")
    public void getArticle(HttpContext ctx, @PathParam("slug") String slug) {
        Optional<Article> articleOpt = articleService.findBySlug(slug);
        if (articleOpt.isEmpty()) {
            ctx.json(HttpStatus.NOT_FOUND, Errors.articleNotFound());
            return;
        }

        Long currentUserId = tryGetCurrentUserId(ctx);
        ctx.json(
                HttpStatus.OK,
                new ArticleResponse(createArticleData(articleOpt.get(), currentUserId, true)));
    }

    @Post("/articles")
    public void createArticle(HttpContext ctx) {
        Long currentUserId = getCurrentUserId(ctx);

        ArticleDtos.CreateArticleRequest body =
                ctx.validatedBody(ArticleDtos.CreateArticleRequest.class);
        var a = body.article();

        Article article =
                articleService.create(
                        a.title(), a.description(), a.body(), a.tagList(), currentUserId);
        ctx.json(
                HttpStatus.CREATED,
                new ArticleResponse(createArticleData(article, currentUserId, true)));
    }

    @Put("/articles/{slug}")
    public void updateArticle(HttpContext ctx, @PathParam("slug") String slug) {
        Long currentUserId = getCurrentUserId(ctx);

        Optional<Article> articleOpt = articleService.findBySlug(slug);
        if (articleOpt.isEmpty()) {
            ctx.json(HttpStatus.NOT_FOUND, Errors.articleNotFound());
            return;
        }

        Article article = articleOpt.get();
        if (!article.authorId().equals(currentUserId)) {
            ctx.json(HttpStatus.FORBIDDEN, Errors.articleForbidden());
            return;
        }

        ArticleDtos.UpdateArticleRequest body =
                ctx.validatedBody(ArticleDtos.UpdateArticleRequest.class);
        var a = body.article();

        // Check if tagList is explicitly set to null (should be rejected)
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> rawBody = ctx.body(java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> rawArticle =
                (java.util.Map<String, Object>) rawBody.get("article");
        if (rawArticle != null
                && rawArticle.containsKey("tagList")
                && rawArticle.get("tagList") == null) {
            ctx.json(HttpStatus.UNPROCESSABLE_ENTITY, Errors.of("tagList", "can't be null"));
            return;
        }

        Article updatedArticle =
                articleService.update(article, a.title(), a.description(), a.body(), a.tagList());
        ctx.json(
                HttpStatus.OK,
                new ArticleResponse(createArticleData(updatedArticle, currentUserId, true)));
    }

    @Delete("/articles/{slug}")
    public void deleteArticle(HttpContext ctx, @PathParam("slug") String slug) {
        Long currentUserId = getCurrentUserId(ctx);

        Optional<Article> articleOpt = articleService.findBySlug(slug);
        if (articleOpt.isEmpty()) {
            ctx.json(HttpStatus.NOT_FOUND, Errors.articleNotFound());
            return;
        }

        Article article = articleOpt.get();
        if (!article.authorId().equals(currentUserId)) {
            ctx.json(HttpStatus.FORBIDDEN, Errors.articleForbidden());
            return;
        }

        articleService.delete(article.id());
        ctx.json(HttpStatus.NO_CONTENT, "");
    }

    @Post("/articles/{slug}/favorite")
    public void favoriteArticle(HttpContext ctx, @PathParam("slug") String slug) {
        Long currentUserId = getCurrentUserId(ctx);

        Optional<Article> articleOpt = articleService.findBySlug(slug);
        if (articleOpt.isEmpty()) {
            ctx.json(HttpStatus.NOT_FOUND, Errors.articleNotFound());
            return;
        }

        favoriteRepository.favorite(currentUserId, articleOpt.get().id());
        ctx.json(
                HttpStatus.OK,
                new ArticleResponse(createArticleData(articleOpt.get(), currentUserId, true)));
    }

    @Delete("/articles/{slug}/favorite")
    public void unfavoriteArticle(HttpContext ctx, @PathParam("slug") String slug) {
        Long currentUserId = getCurrentUserId(ctx);

        Optional<Article> articleOpt = articleService.findBySlug(slug);
        if (articleOpt.isEmpty()) {
            ctx.json(HttpStatus.NOT_FOUND, Errors.articleNotFound());
            return;
        }

        favoriteRepository.unfavorite(currentUserId, articleOpt.get().id());
        ctx.json(
                HttpStatus.OK,
                new ArticleResponse(createArticleData(articleOpt.get(), currentUserId, true)));
    }

    /**
     * @param includeBody true for single article (includes body field), false for list (excludes
     *     body)
     */
    private ArticleData createArticleData(
            Article article, Long currentUserId, boolean includeBody) {
        boolean favorited =
                currentUserId != null
                        && favoriteRepository.isFavorited(currentUserId, article.id());
        int favoritesCount = favoriteRepository.countByArticleId(article.id());
        Author author = createAuthorData(article.authorId(), currentUserId);

        return new ArticleData(
                article.slug(),
                article.title(),
                article.description(),
                includeBody ? article.body() : null,
                articleRepository.findTags(article.id()),
                article.createdAt().toString(),
                article.updatedAt().toString(),
                favorited,
                favoritesCount,
                author);
    }

    private Author createAuthorData(Long authorId, Long currentUserId) {
        Optional<User> authorOpt = userService.findById(authorId);
        if (authorOpt.isPresent()) {
            User author = authorOpt.get();
            boolean following =
                    currentUserId != null && followRepository.isFollowing(currentUserId, authorId);
            return new Author(author.username(), author.bio(), author.image(), following);
        }
        return new Author(null, null, null, false);
    }

    private Long getCurrentUserId(HttpContext ctx) {
        return AuthUtils.getCurrentUserId(ctx, jwtUtil);
    }

    private Long tryGetCurrentUserId(HttpContext ctx) {
        return AuthUtils.tryGetCurrentUserId(ctx, jwtUtil);
    }
}

package com.github.dropguard.summer.realworld.article;

import com.github.dropguard.summer.core.data.LimitOffsetPageRequest;
import com.github.dropguard.summer.realworld.article.ArticleDtos.ArticleData;

import com.github.dropguard.summer.realworld.common.ArticleNotFoundException;
import com.github.dropguard.summer.realworld.common.ArticleForbiddenException;
import com.github.dropguard.summer.realworld.common.TagListNullException;
import com.github.dropguard.summer.realworld.article.ArticleDtos.ArticleResponse;
import com.github.dropguard.summer.realworld.article.ArticleDtos.ArticlesResponse;
import com.github.dropguard.summer.realworld.article.ArticleDtos.Author;
import com.github.dropguard.summer.realworld.auth.AuthUtils;
import com.github.dropguard.summer.realworld.auth.JwtUtil;
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
import java.util.Map;
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
                createArticleDatas(paginatedArticles, currentUserId, false);

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
                createArticleDatas(paginatedArticles, currentUserId, false);

        ctx.json(HttpStatus.OK, new ArticlesResponse(articleResponses, total));
    }

    @Get("/articles/{slug}")
    public void getArticle(HttpContext ctx, @PathParam("slug") String slug) {
        Optional<Article> articleOpt = articleService.findBySlug(slug);
        if (articleOpt.isEmpty()) {
            throw new ArticleNotFoundException("Article not found");
        }

        Long currentUserId = tryGetCurrentUserId(ctx);
        Article article = articleOpt.get();
        Map<Long, User> authorsById = authorsById(List.of(article));
        ctx.json(
                HttpStatus.OK,
                new ArticleResponse(createArticleData(article, currentUserId, true, authorsById)));
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
        Map<Long, User> authorsById = authorsById(List.of(article));
        ctx.json(
                HttpStatus.CREATED,
                new ArticleResponse(createArticleData(article, currentUserId, true, authorsById)));
    }

    @Put("/articles/{slug}")
    public void updateArticle(HttpContext ctx, @PathParam("slug") String slug) {
        Long currentUserId = getCurrentUserId(ctx);

        Optional<Article> articleOpt = articleService.findBySlug(slug);
        if (articleOpt.isEmpty()) {
            throw new ArticleNotFoundException("Article not found");
        }

        Article article = articleOpt.get();
        if (!article.authorId().equals(currentUserId)) {
            throw new ArticleForbiddenException("Article forbidden");
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
            throw new TagListNullException("tagList: can't be null");
        }

        Article updatedArticle =
                articleService.update(article, a.title(), a.description(), a.body(), a.tagList());
        Map<Long, User> authorsById = authorsById(List.of(updatedArticle));
        ctx.json(
                HttpStatus.OK,
                new ArticleResponse(
                        createArticleData(updatedArticle, currentUserId, true, authorsById)));
    }

    @Delete("/articles/{slug}")
    public void deleteArticle(HttpContext ctx, @PathParam("slug") String slug) {
        Long currentUserId = getCurrentUserId(ctx);

        Optional<Article> articleOpt = articleService.findBySlug(slug);
        if (articleOpt.isEmpty()) {
            throw new ArticleNotFoundException("Article not found");
        }

        Article article = articleOpt.get();
        if (!article.authorId().equals(currentUserId)) {
            throw new ArticleForbiddenException("Article forbidden");
        }

        articleService.delete(article.id());
        ctx.json(HttpStatus.NO_CONTENT, "");
    }

    @Post("/articles/{slug}/favorite")
    public void favoriteArticle(HttpContext ctx, @PathParam("slug") String slug) {
        Long currentUserId = getCurrentUserId(ctx);

        Optional<Article> articleOpt = articleService.findBySlug(slug);
        if (articleOpt.isEmpty()) {
            throw new ArticleNotFoundException("Article not found");
        }

        favoriteRepository.favorite(currentUserId, articleOpt.get().id());
        Article article = articleOpt.get();
        Map<Long, User> authorsById = authorsById(List.of(article));
        ctx.json(
                HttpStatus.OK,
                new ArticleResponse(createArticleData(article, currentUserId, true, authorsById)));
    }

    @Delete("/articles/{slug}/favorite")
    public void unfavoriteArticle(HttpContext ctx, @PathParam("slug") String slug) {
        Long currentUserId = getCurrentUserId(ctx);

        Optional<Article> articleOpt = articleService.findBySlug(slug);
        if (articleOpt.isEmpty()) {
            throw new ArticleNotFoundException("Article not found");
        }

        favoriteRepository.unfavorite(currentUserId, articleOpt.get().id());
        Article article = articleOpt.get();
        Map<Long, User> authorsById = authorsById(List.of(article));
        ctx.json(
                HttpStatus.OK,
                new ArticleResponse(createArticleData(article, currentUserId, true, authorsById)));
    }

    /**
     * Batch-assembles {@link ArticleData} for a list of articles in a constant number of queries —
     * the anti-N+1 path for list responses. All authors, tags, favorite counts, and favorited
     * statuses are loaded once (a handful of batch queries) instead of once per article.
     */
    private List<ArticleData> createArticleDatas(
            List<Article> articles, Long currentUserId, boolean includeBody) {
        if (articles.isEmpty()) {
            return List.of();
        }
        List<Long> articleIds = articles.stream().map(Article::id).collect(Collectors.toList());
        Map<Long, User> authorsById = authorsById(articles);
        Map<Long, List<String>> tagsByArticleId =
                articleRepository.findTagsByArticleIds(articleIds);
        Map<Long, Integer> favoriteCounts = favoriteRepository.countByArticleIds(articleIds);
        Set<Long> favoritedIds =
                currentUserId != null
                        ? favoriteRepository.getFavoritedByUser(currentUserId, articleIds)
                        : Set.of();

        return articles.stream()
                .map(
                        a -> {
                            boolean favorited = favoritedIds.contains(a.id());
                            int favoritesCount = favoriteCounts.getOrDefault(a.id(), 0);
                            Author author =
                                    createAuthorData(a.authorId(), currentUserId, authorsById);
                            return new ArticleData(
                                    a.slug(),
                                    a.title(),
                                    a.description(),
                                    includeBody ? a.body() : null,
                                    tagsByArticleId.getOrDefault(a.id(), List.of()),
                                    a.createdAt().toString(),
                                    a.updatedAt().toString(),
                                    favorited,
                                    favoritesCount,
                                    author);
                        })
                .collect(Collectors.toList());
    }

    /**
     * @param includeBody true for single article (includes body field), false for list (excludes
     *     body)
     */
    private ArticleData createArticleData(
            Article article, Long currentUserId, boolean includeBody, Map<Long, User> authorsById) {
        boolean favorited =
                currentUserId != null
                        && favoriteRepository.isFavorited(currentUserId, article.id());
        int favoritesCount = favoriteRepository.countByArticleId(article.id());
        Author author = createAuthorData(article.authorId(), currentUserId, authorsById);

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

    private Author createAuthorData(
            Long authorId, Long currentUserId, Map<Long, User> authorsById) {
        User author = authorsById.get(authorId);
        if (author != null) {
            boolean following =
                    currentUserId != null && followRepository.isFollowing(currentUserId, authorId);
            return new Author(author.username(), author.bio(), author.image(), following);
        }
        return new Author(null, null, null, false);
    }

    /**
     * Batch-loads the authors of the given articles in a single IN query, keyed by user id — the
     * anti-N+1 lookup for a list response (one query instead of one per article).
     */
    private Map<Long, User> authorsById(List<Article> articles) {
        List<Long> authorIds =
                articles.stream().map(Article::authorId).distinct().collect(Collectors.toList());
        return userService.findByIds(authorIds).stream()
                .collect(Collectors.toMap(User::id, u -> u));
    }

    private Long getCurrentUserId(HttpContext ctx) {
        return AuthUtils.getCurrentUserId(ctx, jwtUtil);
    }

    private Long tryGetCurrentUserId(HttpContext ctx) {
        return AuthUtils.tryGetCurrentUserId(ctx, jwtUtil);
    }
}

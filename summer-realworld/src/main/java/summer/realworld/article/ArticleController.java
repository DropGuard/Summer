package summer.realworld.article;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import summer.realworld.article.ArticleDtos;
import summer.realworld.article.ArticleDtos.ArticleData;
import summer.realworld.article.ArticleDtos.ArticleResponse;
import summer.realworld.article.ArticleDtos.ArticlesResponse;
import summer.realworld.article.ArticleDtos.Author;
import summer.realworld.article.Article;
import summer.realworld.user.User;
import summer.realworld.article.FavoriteRepository;
import summer.realworld.user.FollowRepository;
import summer.realworld.article.ArticleService;
import summer.realworld.user.UserService;
import summer.realworld.auth.AuthUtils;
import summer.realworld.common.Errors;
import summer.realworld.auth.JwtUtil;
import summer.web.HttpContext;
import summer.web.HttpStatus;
import summer.realworld.common.LimitOffsetPageable;
import summer.web.annotation.Delete;
import summer.web.annotation.Get;
import summer.web.annotation.PathParam;
import summer.web.annotation.Post;
import summer.web.annotation.Put;
import summer.web.annotation.RestController;

@RestController("/api")
public class ArticleController {
	private final ArticleService articleService;
	private final UserService userService;
	private final FavoriteRepository favoriteRepository;
	private final FollowRepository followRepository;
	private final JwtUtil jwtUtil;

	public ArticleController(ArticleService articleService, UserService userService,
			FavoriteRepository favoriteRepository, FollowRepository followRepository, JwtUtil jwtUtil) {
		this.articleService = articleService;
		this.userService = userService;
		this.favoriteRepository = favoriteRepository;
		this.followRepository = followRepository;
		this.jwtUtil = jwtUtil;
	}

	@Get("/articles")
	public void listArticles(HttpContext ctx, LimitOffsetPageable pageable) {
		String tag = ctx.queryParam("tag");
		String author = ctx.queryParam("author");
		String favorited = ctx.queryParam("favorited");

		List<Article> articles;
		if (tag != null) {
			articles = articleService.findByTag(tag);
		} else if (author != null) {
			Optional<User> authorOpt = userService.findByUsername(author);
			articles = authorOpt.map(user -> articleService.findByAuthorId(user.getId())).orElse(List.of());
		} else if (favorited != null) {
			Optional<User> favUserOpt = userService.findByUsername(favorited);
			if (favUserOpt.isPresent()) {
				Set<Long> favArticleIds = favoriteRepository.getArticleIdsFavoritedBy(favUserOpt.get().getId());
				articles = articleService.findAll().stream().filter(a -> favArticleIds.contains(a.getId()))
						.collect(Collectors.toList());
			} else {
				articles = List.of();
			}
		} else {
			articles = articleService.findAll();
		}

		articles = new ArrayList<>(articles);
		articles.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

		Long currentUserId = getCurrentUserId(ctx);
		int total = articles.size();
		List<Article> paginatedArticles = pageable.paginate(articles);

		List<ArticleData> articleResponses = paginatedArticles.stream()
				.map(a -> createArticleData(a, currentUserId, false)).collect(Collectors.toList());

		ctx.json(HttpStatus.OK, new ArticlesResponse(articleResponses, total));
	}

	@Get("/articles/feed")
	public void feedArticles(HttpContext ctx, LimitOffsetPageable pageable) {
		Long currentUserId = getCurrentUserId(ctx);
		if (currentUserId == null) {
			ctx.json(HttpStatus.UNAUTHORIZED, Errors.tokenMissing());
			return;
		}

		Set<Long> followingIds = followRepository.getFollowing(currentUserId);
		List<Article> articles = articleService.findAll().stream().filter(a -> followingIds.contains(a.getAuthorId()))
				.sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt())).collect(Collectors.toList());

		int total = articles.size();
		List<Article> paginatedArticles = pageable.paginate(articles);

		List<ArticleData> articleResponses = paginatedArticles.stream()
				.map(a -> createArticleData(a, currentUserId, false)).collect(Collectors.toList());

		ctx.json(HttpStatus.OK, new ArticlesResponse(articleResponses, total));
	}

	@Get("/articles/{slug}")
	public void getArticle(HttpContext ctx, @PathParam("slug") String slug) {
		Optional<Article> articleOpt = articleService.findBySlug(slug);
		if (articleOpt.isEmpty()) {
			ctx.json(HttpStatus.NOT_FOUND, Errors.articleNotFound());
			return;
		}

		Long currentUserId = getCurrentUserId(ctx);
		ctx.json(HttpStatus.OK, new ArticleResponse(createArticleData(articleOpt.get(), currentUserId, true)));
	}

	@Post("/articles")
	public void createArticle(HttpContext ctx) {
		Long currentUserId = getCurrentUserId(ctx);
		if (currentUserId == null) {
			ctx.json(HttpStatus.UNAUTHORIZED, Errors.tokenMissing());
			return;
		}

		ArticleDtos.CreateArticleRequest body = ctx.body(ArticleDtos.CreateArticleRequest.class);
		var a = body.article();

		try {
			Article article = articleService.create(a.title(), a.description(), a.body(), a.tagList(), currentUserId);
			ctx.json(HttpStatus.CREATED, new ArticleResponse(createArticleData(article, currentUserId, true)));
		} catch (ArticleService.ValidationException e) {
			ctx.json(HttpStatus.UNPROCESSABLE_ENTITY, Errors.of(e.getField(), e.getMessage()));
		}
	}

	@Put("/articles/{slug}")
	public void updateArticle(HttpContext ctx, @PathParam("slug") String slug) {
		Long currentUserId = getCurrentUserId(ctx);
		if (currentUserId == null) {
			ctx.json(HttpStatus.UNAUTHORIZED, Errors.tokenMissing());
			return;
		}

		Optional<Article> articleOpt = articleService.findBySlug(slug);
		if (articleOpt.isEmpty()) {
			ctx.json(HttpStatus.NOT_FOUND, Errors.articleNotFound());
			return;
		}

		Article article = articleOpt.get();
		if (!article.getAuthorId().equals(currentUserId)) {
			ctx.json(HttpStatus.FORBIDDEN, Errors.articleForbidden());
			return;
		}

		ArticleDtos.UpdateArticleRequest body = ctx.body(ArticleDtos.UpdateArticleRequest.class);
		var a = body.article();

		// Check if tagList is explicitly set to null (should be rejected)
		@SuppressWarnings("unchecked")
		java.util.Map<String, Object> rawBody = ctx.body(java.util.Map.class);
		@SuppressWarnings("unchecked")
		java.util.Map<String, Object> rawArticle = (java.util.Map<String, Object>) rawBody.get("article");
		if (rawArticle != null && rawArticle.containsKey("tagList") && rawArticle.get("tagList") == null) {
			ctx.json(HttpStatus.UNPROCESSABLE_ENTITY, Errors.of("tagList", "can't be null"));
			return;
		}

		try {
			Article updatedArticle = articleService.update(article, a.title(), a.description(), a.body(), a.tagList());
			ctx.json(HttpStatus.OK, new ArticleResponse(createArticleData(updatedArticle, currentUserId, true)));
		} catch (ArticleService.ValidationException e) {
			ctx.json(HttpStatus.UNPROCESSABLE_ENTITY, Errors.of(e.getField(), e.getMessage()));
		}
	}

	@Delete("/articles/{slug}")
	public void deleteArticle(HttpContext ctx, @PathParam("slug") String slug) {
		Long currentUserId = getCurrentUserId(ctx);
		if (currentUserId == null) {
			ctx.json(HttpStatus.UNAUTHORIZED, Errors.tokenMissing());
			return;
		}

		Optional<Article> articleOpt = articleService.findBySlug(slug);
		if (articleOpt.isEmpty()) {
			ctx.json(HttpStatus.NOT_FOUND, Errors.articleNotFound());
			return;
		}

		Article article = articleOpt.get();
		if (!article.getAuthorId().equals(currentUserId)) {
			ctx.json(HttpStatus.FORBIDDEN, Errors.articleForbidden());
			return;
		}

		articleService.delete(article.getId());
		ctx.json(HttpStatus.NO_CONTENT, "");
	}

	@Post("/articles/{slug}/favorite")
	public void favoriteArticle(HttpContext ctx, @PathParam("slug") String slug) {
		Long currentUserId = getCurrentUserId(ctx);
		if (currentUserId == null) {
			ctx.json(HttpStatus.UNAUTHORIZED, Errors.tokenMissing());
			return;
		}

		Optional<Article> articleOpt = articleService.findBySlug(slug);
		if (articleOpt.isEmpty()) {
			ctx.json(HttpStatus.NOT_FOUND, Errors.articleNotFound());
			return;
		}

		favoriteRepository.favorite(currentUserId, articleOpt.get().getId());
		ctx.json(HttpStatus.OK, new ArticleResponse(createArticleData(articleOpt.get(), currentUserId, true)));
	}

	@Delete("/articles/{slug}/favorite")
	public void unfavoriteArticle(HttpContext ctx, @PathParam("slug") String slug) {
		Long currentUserId = getCurrentUserId(ctx);
		if (currentUserId == null) {
			ctx.json(HttpStatus.UNAUTHORIZED, Errors.tokenMissing());
			return;
		}

		Optional<Article> articleOpt = articleService.findBySlug(slug);
		if (articleOpt.isEmpty()) {
			ctx.json(HttpStatus.NOT_FOUND, Errors.articleNotFound());
			return;
		}

		favoriteRepository.unfavorite(currentUserId, articleOpt.get().getId());
		ctx.json(HttpStatus.OK, new ArticleResponse(createArticleData(articleOpt.get(), currentUserId, true)));
	}

	/**
	 * @param includeBody
	 *                    true for single article (includes body field), false for
	 *                    list
	 *                    (excludes body)
	 */
	private ArticleData createArticleData(Article article, Long currentUserId, boolean includeBody) {
		boolean favorited = currentUserId != null && favoriteRepository.isFavorited(currentUserId, article.getId());
		int favoritesCount = favoriteRepository.countByArticleId(article.getId());
		Author author = createAuthorData(article.getAuthorId(), currentUserId);

		return new ArticleData(
				article.getSlug(),
				article.getTitle(),
				article.getDescription(),
				includeBody ? article.getBody() : null,
				article.getTagList() != null ? article.getTagList() : List.of(),
				article.getCreatedAt().toString(),
				article.getUpdatedAt().toString(),
				favorited,
				favoritesCount,
				author);
	}

	private Author createAuthorData(Long authorId, Long currentUserId) {
		Optional<User> authorOpt = userService.findById(authorId);
		if (authorOpt.isPresent()) {
			User author = authorOpt.get();
			boolean following = currentUserId != null && followRepository.isFollowing(currentUserId, authorId);
			return new Author(author.getUsername(), author.getBio(), author.getImage(), following);
		}
		return new Author(null, null, null, false);
	}

	private Long getCurrentUserId(HttpContext ctx) {
		return AuthUtils.getCurrentUserId(ctx, jwtUtil);
	}

}

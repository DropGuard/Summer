package summer.realworld.comment;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import summer.realworld.comment.CommentDtos;
import summer.realworld.comment.CommentDtos.Author;
import summer.realworld.comment.CommentDtos.CommentData;
import summer.realworld.comment.CommentDtos.CommentResponse;
import summer.realworld.comment.CommentDtos.CommentsResponse;
import summer.realworld.article.Article;
import summer.realworld.comment.Comment;
import summer.realworld.user.User;
import summer.realworld.article.ArticleService;
import summer.realworld.comment.CommentService;
import summer.realworld.user.UserService;
import summer.realworld.auth.AuthUtils;
import summer.realworld.common.Errors;
import summer.realworld.auth.JwtUtil;
import summer.web.HttpContext;
import summer.web.HttpStatus;
import summer.web.annotation.Delete;
import summer.web.annotation.Get;
import summer.web.annotation.PathParam;
import summer.web.annotation.Post;
import summer.web.annotation.RestController;

@RestController("/api")
public class CommentController {
	private final CommentService commentService;
	private final ArticleService articleService;
	private final UserService userService;
	private final JwtUtil jwtUtil;

	public CommentController(CommentService commentService, ArticleService articleService, UserService userService, JwtUtil jwtUtil) {
		this.commentService = commentService;
		this.articleService = articleService;
		this.userService = userService;
		this.jwtUtil = jwtUtil;
	}

	@Post("/articles/{slug}/comments")
	public void addComment(HttpContext ctx, @PathParam("slug") String slug) {
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

		CommentDtos.CreateCommentRequest body = ctx.body(CommentDtos.CreateCommentRequest.class);
		String commentBody = body.comment().body();

		Comment comment = commentService.create(commentBody, articleOpt.get().getId(), currentUserId);
		ctx.json(HttpStatus.CREATED, new CommentResponse(createCommentData(comment)));
	}

	@Get("/articles/{slug}/comments")
	public void getComments(HttpContext ctx, @PathParam("slug") String slug) {
		Optional<Article> articleOpt = articleService.findBySlug(slug);
		if (articleOpt.isEmpty()) {
			ctx.json(HttpStatus.NOT_FOUND, Errors.articleNotFound());
			return;
		}

		List<Comment> comments = commentService.findByArticleId(articleOpt.get().getId());
		List<CommentData> commentResponses = comments.stream().map(this::createCommentData)
				.collect(Collectors.toList());

		ctx.json(HttpStatus.OK, new CommentsResponse(commentResponses));
	}

	@Delete("/articles/{slug}/comments/{id}")
	public void deleteComment(HttpContext ctx, @PathParam("slug") String slug, @PathParam("id") String commentIdStr) {
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

		Long commentId;
		try {
			commentId = Long.parseLong(commentIdStr);
		} catch (NumberFormatException e) {
			ctx.json(HttpStatus.NOT_FOUND, Errors.commentNotFound());
			return;
		}

		Optional<Comment> commentOpt = commentService.findById(commentId);
		if (commentOpt.isEmpty()) {
			ctx.json(HttpStatus.NOT_FOUND, Errors.commentNotFound());
			return;
		}

		Comment comment = commentOpt.get();
		if (!comment.getAuthorId().equals(currentUserId)) {
			ctx.json(HttpStatus.FORBIDDEN, Errors.commentForbidden());
			return;
		}

		commentService.delete(commentId);
		ctx.json(HttpStatus.NO_CONTENT, "");
	}

	private CommentData createCommentData(Comment comment) {
		Optional<User> authorOpt = userService.findById(comment.getAuthorId());
		Author author = authorOpt.map(u -> new Author(u.getUsername(), u.getBio(), u.getImage(), false))
				.orElse(new Author(null, null, null, false));

		return new CommentData(
				comment.getId(),
				comment.getCreatedAt().toString(),
				comment.getUpdatedAt().toString(),
				comment.getBody(),
				author);
	}

	private Long getCurrentUserId(HttpContext ctx) {
		return AuthUtils.getCurrentUserId(ctx, jwtUtil);
	}
}

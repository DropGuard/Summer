package summer.realworld.controller;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import summer.realworld.dto.CommentDtos;
import summer.realworld.model.Article;
import summer.realworld.model.Comment;
import summer.realworld.model.User;
import summer.realworld.service.ArticleService;
import summer.realworld.service.CommentService;
import summer.realworld.service.UserService;
import summer.realworld.util.Errors;
import summer.realworld.util.JwtUtil;
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

	public CommentController(CommentService commentService, ArticleService articleService, UserService userService) {
		this.commentService = commentService;
		this.articleService = articleService;
		this.userService = userService;
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

		try {
			Comment comment = commentService.create(commentBody, articleOpt.get().getId(), currentUserId);
			ctx.json(HttpStatus.CREATED, createCommentResponse(comment));
		} catch (CommentService.ValidationException e) {
			ctx.json(HttpStatus.UNPROCESSABLE_ENTITY, Errors.of(e.getField(), e.getMessage()));
		}
	}

	@Get("/articles/{slug}/comments")
	public void getComments(HttpContext ctx, @PathParam("slug") String slug) {
		Optional<Article> articleOpt = articleService.findBySlug(slug);
		if (articleOpt.isEmpty()) {
			ctx.json(HttpStatus.NOT_FOUND, Errors.articleNotFound());
			return;
		}

		List<Comment> comments = commentService.findByArticleId(articleOpt.get().getId());
		List<Map<String, Object>> commentResponses = comments.stream().map(this::createCommentData)
				.collect(Collectors.toList());

		ctx.json(HttpStatus.OK, Map.of("comments", commentResponses));
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

	private Map<String, Object> createCommentResponse(Comment comment) {
		return Map.of("comment", createCommentData(comment));
	}

	private Map<String, Object> createCommentData(Comment comment) {
		Optional<User> authorOpt = userService.findById(comment.getAuthorId());
		Map<String, Object> authorResponse = new HashMap<>();
		if (authorOpt.isPresent()) {
			User author = authorOpt.get();
			authorResponse.put("username", author.getUsername());
			authorResponse.put("bio", author.getBio());
			authorResponse.put("image", author.getImage());
			authorResponse.put("following", false);
		}

		Map<String, Object> commentData = new LinkedHashMap<>();
		commentData.put("id", comment.getId());
		commentData.put("createdAt", comment.getCreatedAt().toString());
		commentData.put("updatedAt", comment.getUpdatedAt().toString());
		commentData.put("body", comment.getBody());
		commentData.put("author", authorResponse);

		return commentData;
	}

	private Long getCurrentUserId(HttpContext ctx) {
		String authHeader = ctx.header("Authorization");
		if (authHeader != null && authHeader.startsWith("Token ")) {
			try {
				return JwtUtil.getUserIdFromToken(authHeader.substring(6));
			} catch (Exception e) {
				return null;
			}
		}
		return null;
	}
}

package com.github.dropguard.summer.realworld.comment;

import com.github.dropguard.summer.realworld.article.Article;
import com.github.dropguard.summer.realworld.article.ArticleService;
import com.github.dropguard.summer.realworld.auth.AuthUtils;
import com.github.dropguard.summer.realworld.auth.JwtUtil;
import com.github.dropguard.summer.realworld.comment.CommentDtos.Author;
import com.github.dropguard.summer.realworld.comment.CommentDtos.CommentData;
import com.github.dropguard.summer.realworld.comment.CommentDtos.CommentResponse;
import com.github.dropguard.summer.realworld.comment.CommentDtos.CommentsResponse;
import com.github.dropguard.summer.realworld.common.Errors;
import com.github.dropguard.summer.realworld.user.User;
import com.github.dropguard.summer.realworld.user.UserService;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.Delete;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.PathParam;
import com.github.dropguard.summer.web.annotation.Post;
import com.github.dropguard.summer.web.annotation.RestController;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController("/api")
public class CommentController {
    private final CommentService commentService;
    private final ArticleService articleService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    public CommentController(
            CommentService commentService,
            ArticleService articleService,
            UserService userService,
            JwtUtil jwtUtil) {
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

        CommentDtos.CreateCommentRequest body =
                ctx.validatedBody(CommentDtos.CreateCommentRequest.class);
        String commentBody = body.comment().body();

        Comment comment = commentService.create(commentBody, articleOpt.get().id(), currentUserId);
        // Resolve the single author via the batch path for consistency.
        Map<Long, User> authorById =
                userService.findByIds(List.of(comment.authorId())).stream()
                        .collect(Collectors.toMap(User::id, u -> u));
        ctx.json(HttpStatus.CREATED, new CommentResponse(createCommentData(comment, authorById)));
    }

    @Get("/articles/{slug}/comments")
    public void getComments(HttpContext ctx, @PathParam("slug") String slug) {
        Optional<Article> articleOpt = articleService.findBySlug(slug);
        if (articleOpt.isEmpty()) {
            ctx.json(HttpStatus.NOT_FOUND, Errors.articleNotFound());
            return;
        }

        List<Comment> comments = commentService.findByArticleId(articleOpt.get().id());
        // Resolve every comment's author in one batch IN query instead of looping
        // createCommentData's findById (N+1).
        Map<Long, User> authorsById =
                userService
                        .findByIds(
                                comments.stream()
                                        .map(Comment::authorId)
                                        .collect(Collectors.toList()))
                        .stream()
                        .collect(Collectors.toMap(User::id, u -> u));
        List<CommentData> commentResponses =
                comments.stream()
                        .map(c -> createCommentData(c, authorsById))
                        .collect(Collectors.toList());

        ctx.json(HttpStatus.OK, new CommentsResponse(commentResponses));
    }

    @Delete("/articles/{slug}/comments/{id}")
    public void deleteComment(
            HttpContext ctx, @PathParam("slug") String slug, @PathParam("id") String commentIdStr) {
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
        if (!comment.authorId().equals(currentUserId)) {
            ctx.json(HttpStatus.FORBIDDEN, Errors.commentForbidden());
            return;
        }

        commentService.delete(commentId);
        ctx.json(HttpStatus.NO_CONTENT, "");
    }

    private CommentData createCommentData(Comment comment, Map<Long, User> authorsById) {
        User author = authorsById.get(comment.authorId());
        Author authorData =
                author != null
                        ? new Author(author.username(), author.bio(), author.image(), false)
                        : new Author(null, null, null, false);

        return new CommentData(
                comment.id(),
                comment.createdAt().toString(),
                comment.updatedAt().toString(),
                comment.body(),
                authorData);
    }

    private Long getCurrentUserId(HttpContext ctx) {
        return AuthUtils.getCurrentUserId(ctx, jwtUtil);
    }
}

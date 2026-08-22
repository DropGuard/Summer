package com.github.dropguard.summer.realworld.comment;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.realworld.common.ValidationException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class CommentService {
    private final CommentRepository commentRepository;

    public CommentService(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    public Comment create(String body, Long articleId, Long authorId) {
        if (body == null || body.isBlank()) {
            throw new ValidationException("body", "can't be blank");
        }
        LocalDateTime now = LocalDateTime.now();
        Comment comment = new Comment(null, body, articleId, authorId, now, now);
        return commentRepository.save(comment);
    }

    public Optional<Comment> findById(Long id) {
        return commentRepository.findById(id);
    }

    public List<Comment> findByArticleId(Long articleId) {
        return commentRepository.findByArticleId(articleId);
    }

    public void delete(Long id) {
        commentRepository.deleteById(id);
    }
}

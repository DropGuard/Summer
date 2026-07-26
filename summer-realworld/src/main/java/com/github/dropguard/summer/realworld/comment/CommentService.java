package com.github.dropguard.summer.realworld.comment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.github.dropguard.summer.realworld.comment.*;
import com.github.dropguard.summer.realworld.common.ValidationException;
import com.github.dropguard.summer.realworld.common.ConflictException;
import com.github.dropguard.summer.realworld.comment.*;

public class CommentService {
	private final CommentRepository commentRepository;

	public CommentService(CommentRepository commentRepository) {
		this.commentRepository = commentRepository;
	}

	public Comment create(String body, Long articleId, Long authorId) {
		if (body == null || body.isBlank()) {
			throw new ValidationException("body", "can't be blank");
		}
		Comment comment = new Comment();
		comment.setBody(body);
		comment.setArticleId(articleId);
		comment.setAuthorId(authorId);
		comment.setCreatedAt(LocalDateTime.now());
		comment.setUpdatedAt(LocalDateTime.now());
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

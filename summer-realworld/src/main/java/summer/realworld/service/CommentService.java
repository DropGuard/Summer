package summer.realworld.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import summer.realworld.model.Comment;
import summer.realworld.repository.CommentRepository;

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

	public static class ValidationException extends RuntimeException {
		private final String field;
		private final String message;

		public ValidationException(String field, String message) {
			super(message);
			this.field = field;
			this.message = message;
		}

		public String getField() {
			return field;
		}
		public String getMessage() {
			return message;
		}
	}
}

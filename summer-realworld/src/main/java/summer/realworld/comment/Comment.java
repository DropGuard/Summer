package summer.realworld.comment;

import java.time.LocalDateTime;

public class Comment {
	private Long id;
	private String body;
	private Long articleId;
	private Long authorId;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	public Comment() {
	}

	public Comment(Long id, String body, Long articleId, Long authorId, LocalDateTime createdAt,
			LocalDateTime updatedAt) {
		this.id = id;
		this.body = body;
		this.articleId = articleId;
		this.authorId = authorId;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}

	public String getBody() {
		return body;
	}
	public void setBody(String body) {
		this.body = body;
	}

	public Long getArticleId() {
		return articleId;
	}
	public void setArticleId(Long articleId) {
		this.articleId = articleId;
	}

	public Long getAuthorId() {
		return authorId;
	}
	public void setAuthorId(Long authorId) {
		this.authorId = authorId;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
}

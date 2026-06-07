package summer.realworld.dto;

import java.util.List;

public class CommentDtos {

	public record CreateCommentRequest(Comment comment) {
		public record Comment(String body) {
		}
	}

	// Shared comment data type
	public record CommentData(long id, String createdAt, String updatedAt, String body, Author author) {
	}

	public record Author(String username, String bio, String image, boolean following) {
	}

	// Single comment response
	public record CommentResponse(CommentData comment) {
	}

	// Multiple comments response
	public record CommentsResponse(List<CommentData> comments) {
	}
}

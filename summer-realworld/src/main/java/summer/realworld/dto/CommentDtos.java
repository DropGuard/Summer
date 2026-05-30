package summer.realworld.dto;

public class CommentDtos {

	public record CreateCommentRequest(Comment comment) {
		public record Comment(String body) {
		}
	}
}

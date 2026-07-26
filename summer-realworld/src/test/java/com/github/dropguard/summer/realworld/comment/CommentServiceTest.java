package com.github.dropguard.summer.realworld.comment;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.dropguard.summer.realworld.comment.Comment;
import com.github.dropguard.summer.realworld.comment.CommentRepository;

class CommentServiceTest {

	private CommentService commentService;
	private CommentRepository commentRepository;

	@BeforeEach
	void setUp() {
		commentRepository = new CommentRepository();
		commentService = new CommentService(commentRepository);
	}

	@Test
	void shouldCreateComment() {
		Comment comment = commentService.create("Great article!", 1L, 1L);

		assertNotNull(comment);
		assertEquals("Great article!", comment.getBody());
		assertEquals(1L, comment.getArticleId());
		assertEquals(1L, comment.getAuthorId());
		assertNotNull(comment.getCreatedAt());
		assertNotNull(comment.getUpdatedAt());
	}

	@Test
	void shouldFindByArticleId() {
		commentService.create("Comment 1", 1L, 1L);
		commentService.create("Comment 2", 1L, 2L);
		commentService.create("Comment 3", 2L, 1L);

		List<Comment> comments = commentService.findByArticleId(1L);

		assertEquals(2, comments.size());
	}

	@Test
	void shouldDeleteComment() {
		Comment comment = commentService.create("Test comment", 1L, 1L);
		assertEquals(1, commentRepository.findAll().size());

		commentService.delete(comment.getId());

		assertEquals(0, commentRepository.findAll().size());
	}
}

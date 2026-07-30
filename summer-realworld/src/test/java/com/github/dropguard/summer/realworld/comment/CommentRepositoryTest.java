package com.github.dropguard.summer.realworld.comment;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommentRepositoryTest {

    private CommentRepository repo;

    @BeforeEach
    void setUp() {
        repo = new CommentRepository();
    }

    @Test
    void shouldSaveAndFindById() {
        Comment c = new Comment(null, "body", 1L, 10L, null, null);
        Comment saved = repo.save(c);

        assertNotNull(saved.getId());
        var found = repo.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("body", found.get().getBody());
    }

    @Test
    void shouldFindByArticleId() {
        repo.save(new Comment(null, "c1", 1L, 10L, null, null));
        repo.save(new Comment(null, "c2", 1L, 20L, null, null));
        repo.save(new Comment(null, "c3", 2L, 10L, null, null));

        assertEquals(2, repo.findByArticleId(1L).size());
        assertEquals(1, repo.findByArticleId(2L).size());
    }

    @Test
    void shouldDeleteById() {
        Comment saved = repo.save(new Comment(null, "x", 1L, 10L, null, null));
        assertEquals(1, repo.findAll().size());

        repo.deleteById(saved.getId());

        assertEquals(0, repo.findAll().size());
    }

    @Test
    void deleteByArticleIdShouldRemoveOnlyMatchingComments() {
        repo.save(new Comment(null, "c1", 1L, 10L, null, null));
        repo.save(new Comment(null, "c2", 1L, 20L, null, null));
        repo.save(new Comment(null, "c3", 2L, 10L, null, null));

        repo.deleteByArticleId(1L);

        assertEquals(0, repo.findByArticleId(1L).size());
        assertEquals(1, repo.findByArticleId(2L).size());
        assertEquals(1, repo.findAll().size());
    }

    @Test
    void deleteByArticleIdShouldBeNoopWhenArticleHasNoComments() {
        repo.save(new Comment(null, "c", 3L, 10L, null, null));

        repo.deleteByArticleId(99L); // no comments for this article

        assertEquals(1, repo.findAll().size());
    }
}

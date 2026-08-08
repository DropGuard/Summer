package com.github.dropguard.summer.realworld.comment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class CommentRepository {
    private final Map<Long, Comment> comments = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public Comment save(Comment comment) {
        if (comment.getId() == null) {
            comment.setId(idGenerator.getAndIncrement());
        }
        comments.put(comment.getId(), comment);
        return comment;
    }

    public Optional<Comment> findById(Long id) {
        return Optional.ofNullable(comments.get(id));
    }

    public List<Comment> findByArticleId(Long articleId) {
        return comments.values().stream()
                .filter(comment -> comment.getArticleId().equals(articleId))
                .collect(Collectors.toList());
    }

    public List<Comment> findAll() {
        return new ArrayList<>(comments.values());
    }

    public void deleteById(Long id) {
        comments.remove(id);
    }

    /** Remove all comments for an article — called when the article is deleted. */
    public void deleteByArticleId(Long articleId) {
        comments.values().removeIf(comment -> comment.getArticleId().equals(articleId));
    }
}

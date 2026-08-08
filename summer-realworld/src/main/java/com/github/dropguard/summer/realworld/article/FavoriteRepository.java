package com.github.dropguard.summer.realworld.article;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FavoriteRepository {
    // Key: "userId:articleId"
    private final Set<String> favorites = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Long, Integer> counts = new ConcurrentHashMap<>();

    public void favorite(Long userId, Long articleId) {
        if (favorites.add(userId + ":" + articleId)) {
            counts.merge(articleId, 1, Integer::sum);
        }
    }

    public void unfavorite(Long userId, Long articleId) {
        if (favorites.remove(userId + ":" + articleId)) {
            counts.computeIfPresent(articleId, (k, v) -> v > 1 ? v - 1 : null);
        }
    }

    /** Remove all favorites for an article — called when the article is deleted. */
    public void deleteByArticleId(Long articleId) {
        String suffix = ":" + articleId;
        favorites.removeIf(key -> key.endsWith(suffix));
        counts.remove(articleId);
    }

    public boolean isFavorited(Long userId, Long articleId) {
        return favorites.contains(userId + ":" + articleId);
    }

    public int countByArticleId(Long articleId) {
        return counts.getOrDefault(articleId, 0);
    }

    public Set<Long> getArticleIdsFavoritedBy(Long userId) {
        Set<Long> result = ConcurrentHashMap.newKeySet();
        String prefix = userId + ":";
        for (String key : favorites) {
            if (key.startsWith(prefix)) {
                result.add(Long.parseLong(key.substring(prefix.length())));
            }
        }
        return result;
    }
}

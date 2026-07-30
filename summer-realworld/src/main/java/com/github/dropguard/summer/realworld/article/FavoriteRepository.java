package com.github.dropguard.summer.realworld.article;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FavoriteRepository {
	// Key: "userId:articleId"
	private final Set<String> favorites = ConcurrentHashMap.newKeySet();

	public void favorite(Long userId, Long articleId) {
		favorites.add(userId + ":" + articleId);
	}

	public void unfavorite(Long userId, Long articleId) {
		favorites.remove(userId + ":" + articleId);
	}

	/** Remove all favorites for an article — called when the article is deleted. */
	public void deleteByArticleId(Long articleId) {
		String suffix = ":" + articleId;
		favorites.removeIf(key -> key.endsWith(suffix));
	}

	public boolean isFavorited(Long userId, Long articleId) {
		return favorites.contains(userId + ":" + articleId);
	}

	public int countByArticleId(Long articleId) {
		int count = 0;
		for (String key : favorites) {
			if (key.endsWith(":" + articleId)) {
				count++;
			}
		}
		return count;
	}

	public Set<Long> getArticleIdsFavoritedBy(Long userId) {
		Set<Long> result = ConcurrentHashMap.newKeySet();
		for (String key : favorites) {
			String[] parts = key.split(":");
			if (Long.parseLong(parts[0]) == userId) {
				result.add(Long.parseLong(parts[1]));
			}
		}
		return result;
	}
}

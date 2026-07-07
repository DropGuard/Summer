package summer.realworld.article;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import summer.realworld.article.Article;

public class ArticleRepository {
	private final Map<Long, Article> articles = new ConcurrentHashMap<>();
	private final Map<String, Article> articlesBySlug = new ConcurrentHashMap<>();
	private final AtomicLong idGenerator = new AtomicLong(1);

	public Article save(Article article) {
		if (article.getId() == null) {
			article.setId(idGenerator.getAndIncrement());
		}
		articles.put(article.getId(), article);
		articlesBySlug.put(article.getSlug(), article);
		return article;
	}

	public Optional<Article> findById(Long id) {
		return Optional.ofNullable(articles.get(id));
	}

	public Optional<Article> findBySlug(String slug) {
		return Optional.ofNullable(articlesBySlug.get(slug));
	}

	public List<Article> findAll() {
		return new ArrayList<>(articles.values());
	}

	public List<Article> findByAuthorId(Long authorId) {
		return articles.values().stream().filter(article -> article.getAuthorId().equals(authorId))
				.collect(Collectors.toList());
	}

	public List<Article> findByTag(String tag) {
		return articles.values().stream()
				.filter(article -> article.getTagList() != null && article.getTagList().contains(tag))
				.collect(Collectors.toList());
	}

	public void deleteById(Long id) {
		Article article = articles.remove(id);
		if (article != null) {
			articlesBySlug.remove(article.getSlug());
		}
	}
}

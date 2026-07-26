package com.github.dropguard.summer.realworld.article;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.dropguard.summer.realworld.article.Article;
import com.github.dropguard.summer.realworld.article.ArticleRepository;

class ArticleServiceTest {

	private ArticleService articleService;
	private ArticleRepository articleRepository;

	@BeforeEach
	void setUp() {
		articleRepository = new ArticleRepository();
		articleService = new ArticleService(articleRepository);
	}

	@Test
	void shouldCreateArticle() {
		Article article = articleService.create("How to train your dragon", "Ever wonder how?", "You have to believe",
				List.of("dragons", "training"), 1L);

		assertNotNull(article);
		assertEquals("How to train your dragon", article.getTitle());
		assertEquals("Ever wonder how?", article.getDescription());
		assertEquals("You have to believe", article.getBody());
		assertEquals(List.of("dragons", "training"), article.getTagList());
		assertEquals(1L, article.getAuthorId());
		assertNotNull(article.getSlug());
		assertNotNull(article.getCreatedAt());
		assertNotNull(article.getUpdatedAt());
		assertEquals(0, article.getFavoritesCount());
	}

	@Test
	void shouldGenerateSlugFromTitle() {
		Article article = articleService.create("How to Train Your Dragon!", "Description", "Body", List.of(), 1L);

		assertEquals("how-to-train-your-dragon", article.getSlug());
	}

	@Test
	void shouldFindBySlug() {
		articleService.create("Test Article", "Desc", "Body", List.of(), 1L);

		var found = articleService.findBySlug("test-article");

		assertTrue(found.isPresent());
		assertEquals("Test Article", found.get().getTitle());
	}

	@Test
	void shouldFindByAuthorId() {
		articleService.create("Article 1", "Desc", "Body", List.of(), 1L);
		articleService.create("Article 2", "Desc", "Body", List.of(), 1L);
		articleService.create("Article 3", "Desc", "Body", List.of(), 2L);

		List<Article> articles = articleService.findByAuthorId(1L);

		assertEquals(2, articles.size());
	}

	@Test
	void shouldFindByTag() {
		articleService.create("Article 1", "Desc", "Body", List.of("java"), 1L);
		articleService.create("Article 2", "Desc", "Body", List.of("python"), 1L);
		articleService.create("Article 3", "Desc", "Body", List.of("java", "python"), 1L);

		List<Article> javaArticles = articleService.findByTag("java");
		List<Article> pythonArticles = articleService.findByTag("python");

		assertEquals(2, javaArticles.size());
		assertEquals(2, pythonArticles.size());
	}

	@Test
	void shouldDeleteArticle() {
		Article article = articleService.create("Test", "Desc", "Body", List.of(), 1L);
		assertEquals(1, articleService.findAll().size());

		articleService.delete(article.getId());

		assertEquals(0, articleService.findAll().size());
	}

	@Test
	void shouldUpdateArticle() {
		Article article = articleService.create("Old Title", "Desc", "Body", List.of(), 1L);

		Article updated = articleService.update(article, "New Title", null, null, null);

		assertEquals("New Title", updated.getTitle());
		assertEquals("new-title", updated.getSlug());
	}
}

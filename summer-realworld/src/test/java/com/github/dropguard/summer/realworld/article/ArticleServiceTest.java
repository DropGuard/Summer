package com.github.dropguard.summer.realworld.article;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.dropguard.summer.realworld.article.Article;
import com.github.dropguard.summer.realworld.article.ArticleRepository;
import com.github.dropguard.summer.realworld.article.FavoriteRepository;
import com.github.dropguard.summer.realworld.comment.CommentRepository;
import com.github.dropguard.summer.realworld.comment.Comment;

class ArticleServiceTest {

	private ArticleService articleService;
	private ArticleRepository articleRepository;
	private FavoriteRepository favoriteRepository;
	private CommentRepository commentRepository;

	@BeforeEach
	void setUp() {
		articleRepository = new ArticleRepository();
		favoriteRepository = new FavoriteRepository();
		commentRepository = new CommentRepository();
		articleService = new ArticleService(articleRepository, favoriteRepository, commentRepository);
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

	@Test
	void deleteArticleShouldCascadeToFavoritesAndComments() {
		Article article = articleService.create("Test", "Desc", "Body", List.of(), 1L);

		// Add favorites and comments tied to this article
		favoriteRepository.favorite(10L, article.getId());
		favoriteRepository.favorite(20L, article.getId());
		favoriteRepository.favorite(10L, 999L); // unrelated — must survive
		commentRepository.save(new Comment(null, "c1", article.getId(), 10L, null, null));
		commentRepository.save(new Comment(null, "c2", article.getId(), 20L, null, null));
		commentRepository.save(new Comment(null, "c3", 999L, 10L, null, null)); // unrelated

		articleService.delete(article.getId());

		// Article gone
		assertTrue(articleRepository.findById(article.getId()).isEmpty());
		// Favorites for this article gone, unrelated survives
		assertEquals(0, favoriteRepository.countByArticleId(article.getId()));
		assertEquals(1, favoriteRepository.countByArticleId(999L));
		// Comments for this article gone, unrelated survives
		assertEquals(0, commentRepository.findByArticleId(article.getId()).size());
		assertEquals(1, commentRepository.findByArticleId(999L).size());
	}
}

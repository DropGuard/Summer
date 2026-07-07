package summer.realworld.common;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.realworld.article.ArticleRepository;
import summer.realworld.comment.CommentRepository;
import summer.realworld.article.FavoriteRepository;
import summer.realworld.user.FollowRepository;
import summer.realworld.user.UserRepository;
import summer.realworld.article.ArticleService;
import summer.realworld.comment.CommentService;
import summer.realworld.user.UserService;

@Configuration
public class AppConfig {

	@Bean
	public UserRepository userRepository() {
		return new UserRepository();
	}

	@Bean
	public ArticleRepository articleRepository() {
		return new ArticleRepository();
	}

	@Bean
	public CommentRepository commentRepository() {
		return new CommentRepository();
	}

	@Bean
	public FollowRepository followRepository() {
		return new FollowRepository();
	}

	@Bean
	public FavoriteRepository favoriteRepository() {
		return new FavoriteRepository();
	}

	@Bean
	public UserService userService(UserRepository userRepository) {
		return new UserService(userRepository);
	}

	@Bean
	public ArticleService articleService(ArticleRepository articleRepository) {
		return new ArticleService(articleRepository);
	}

	@Bean
	public CommentService commentService(CommentRepository commentRepository) {
		return new CommentService(commentRepository);
	}
}

package summer.realworld.config;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.realworld.repository.ArticleRepository;
import summer.realworld.repository.CommentRepository;
import summer.realworld.repository.FavoriteRepository;
import summer.realworld.repository.FollowRepository;
import summer.realworld.repository.UserRepository;
import summer.realworld.service.ArticleService;
import summer.realworld.service.CommentService;
import summer.realworld.service.UserService;

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

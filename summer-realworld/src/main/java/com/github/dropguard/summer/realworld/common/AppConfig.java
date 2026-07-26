package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.realworld.article.ArticleRepository;
import com.github.dropguard.summer.realworld.comment.CommentRepository;
import com.github.dropguard.summer.realworld.article.FavoriteRepository;
import com.github.dropguard.summer.realworld.user.FollowRepository;
import com.github.dropguard.summer.realworld.user.UserRepository;
import com.github.dropguard.summer.realworld.article.ArticleService;
import com.github.dropguard.summer.realworld.comment.CommentService;
import com.github.dropguard.summer.realworld.user.UserService;

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

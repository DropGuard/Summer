package com.github.dropguard.summer.twitter.social;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.twitter.tweet.TweetRepository;
import com.github.dropguard.summer.twitter.infra.SnowflakeIdGenerator;

import java.time.OffsetDateTime;

@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final TweetRepository tweetRepository;
    private final SnowflakeIdGenerator idGenerator;

    public LikeService(LikeRepository likeRepository, TweetRepository tweetRepository, SnowflakeIdGenerator idGenerator) {
        this.likeRepository = likeRepository;
        this.tweetRepository = tweetRepository;
        this.idGenerator = idGenerator;
    }

    public void like(Long currentUserId, Long tweetId) {
        if (likeRepository.exists(currentUserId, tweetId)) {
            return; // Already liked
        }

        Like like = new Like(idGenerator.nextId(), currentUserId, tweetId, OffsetDateTime.now());
        likeRepository.insert(like);

        tweetRepository.updateLikeCount(tweetId, 1);
    }

    public void unlike(Long currentUserId, Long tweetId) {
        if (!likeRepository.exists(currentUserId, tweetId)) {
            return; // Not liked
        }

        likeRepository.delete(currentUserId, tweetId);

        tweetRepository.updateLikeCount(tweetId, -1);
    }
}

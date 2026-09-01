package com.github.dropguard.summer.realworld.user;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.realworld.common.SelfFollowException;

@Component
public class FollowService {
    private final FollowRepository followRepository;

    public FollowService(FollowRepository followRepository) {
        this.followRepository = followRepository;
    }

    public void follow(Long currentUserId, Long targetId) {
        if (currentUserId.equals(targetId)) {
            throw new SelfFollowException("can't follow yourself");
        }
        followRepository.follow(currentUserId, targetId);
    }

    public void unfollow(Long currentUserId, Long targetId) {
        followRepository.unfollow(currentUserId, targetId);
    }

    public boolean isFollowing(Long currentUserId, Long targetId) {
        return currentUserId != null && followRepository.isFollowing(currentUserId, targetId);
    }
}

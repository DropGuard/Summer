package com.github.dropguard.summer.twitter.social;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.redis.SummerRedisTemplate;
import com.github.dropguard.summer.twitter.common.IllegalOperationException;
import com.github.dropguard.summer.twitter.common.UserNotFoundException;
import com.github.dropguard.summer.twitter.infra.SnowflakeIdGenerator;
import com.github.dropguard.summer.twitter.user.User;
import com.github.dropguard.summer.twitter.user.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;

@Component
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final SummerRedisTemplate redisTemplate;

    public FollowService(
            FollowRepository followRepository,
            UserRepository userRepository,
            SnowflakeIdGenerator idGenerator,
            SummerRedisTemplate redisTemplate) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.idGenerator = idGenerator;
        this.redisTemplate = redisTemplate;
    }

    public void follow(Long currentUserId, String targetUsername) {
        User targetUser =
                userRepository
                        .findByUsername(targetUsername)
                        .orElseThrow(() -> new UserNotFoundException("Target user not found"));

        if (currentUserId.equals(targetUser.id())) {
            throw new IllegalOperationException("Cannot follow yourself");
        }

        // Atomically insert — if the row already exists (concurrent follow or
        // replay) the DB constraint makes this a no-op and we skip the counter
        // bump so counts stay accurate.
        Follow follow =
                new Follow(
                        idGenerator.nextId(), currentUserId, targetUser.id(), OffsetDateTime.now());
        if (!followRepository.insertIfAbsent(follow)) {
            return; // Already following — idempotent
        }

        userRepository.updateCounts(targetUser.id(), 1, 0);
        userRepository.updateCounts(currentUserId, 0, 1);
    }

    public void unfollow(Long currentUserId, String targetUsername) {
        User targetUser =
                userRepository
                        .findByUsername(targetUsername)
                        .orElseThrow(() -> new UserNotFoundException("Target user not found"));

        // Delete the follow row — if it doesn't exist (already unfollowed,
        // concurrent unfollow, or never followed) this is a 0-row no-op.
        int deleted = followRepository.deleteByUsers(currentUserId, targetUser.id());
        if (deleted == 0) {
            return; // Not following — idempotent
        }

        // Clean up the unfollowed user's tweet IDs from the follower's timeline.
        // Best-effort: Redis failure must not block the unfollow.
        try {
            List<Object> authorTweetIds =
                    redisTemplate
                            .getCommands()
                            .zrange("user:" + targetUser.id() + ":tweets", 0, -1);
            if (authorTweetIds != null && !authorTweetIds.isEmpty()) {
                String[] ids = authorTweetIds.stream().map(Object::toString).toArray(String[]::new);
                redisTemplate.getCommands().zrem("timeline:" + currentUserId, ids);
            }
        } catch (Exception e) {
            // Redis cleanup is best-effort — timeline entries are capped by fanOut
            // as a backstop, so transient Redis failures during unfollow are not
            // data-loss events.
        }

        userRepository.updateCounts(targetUser.id(), -1, 0);
        userRepository.updateCounts(currentUserId, 0, -1);
    }

    public List<Follow> getFollowers(String username, Long cursor, int limit) {
        User targetUser =
                userRepository
                        .findByUsername(username)
                        .orElseThrow(() -> new UserNotFoundException("User not found"));
        return followRepository.findFollowers(targetUser.id(), cursor, limit);
    }

    public List<Follow> getFollowing(String username, Long cursor, int limit) {
        User targetUser =
                userRepository
                        .findByUsername(username)
                        .orElseThrow(() -> new UserNotFoundException("User not found"));
        return followRepository.findFollowing(targetUser.id(), cursor, limit);
    }
}

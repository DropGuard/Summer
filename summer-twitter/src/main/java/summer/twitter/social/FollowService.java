package summer.twitter.social;

import summer.core.Component;
import summer.twitter.common.IllegalOperationException;
import summer.twitter.common.UserNotFoundException;
import summer.twitter.user.User;
import summer.twitter.user.UserRepository;
import summer.twitter.infra.SnowflakeIdGenerator;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final SnowflakeIdGenerator idGenerator;

    public FollowService(FollowRepository followRepository, UserRepository userRepository, SnowflakeIdGenerator idGenerator) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.idGenerator = idGenerator;
    }

    public void follow(Long currentUserId, String targetUsername) {
        User targetUser = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new UserNotFoundException("Target user not found"));

        if (currentUserId.equals(targetUser.id())) {
            throw new IllegalOperationException("Cannot follow yourself");
        }

        if (followRepository.exists(currentUserId, targetUser.id())) {
            return; // Already following
        }

        Follow follow = new Follow(idGenerator.nextId(), currentUserId, targetUser.id(), OffsetDateTime.now());
        followRepository.insert(follow);

        userRepository.updateCounts(targetUser.id(), 1, 0);
        userRepository.updateCounts(currentUserId, 0, 1);
    }

    public void unfollow(Long currentUserId, String targetUsername) {
        User targetUser = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new UserNotFoundException("Target user not found"));

        if (!followRepository.exists(currentUserId, targetUser.id())) {
            return; // Not following
        }

        followRepository.delete(currentUserId, targetUser.id());

        userRepository.updateCounts(targetUser.id(), -1, 0);
        userRepository.updateCounts(currentUserId, 0, -1);
    }

    public List<Follow> getFollowers(String username, Long cursor, int limit) {
        User targetUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return followRepository.findFollowers(targetUser.id(), cursor, limit);
    }

    public List<Follow> getFollowing(String username, Long cursor, int limit) {
        User targetUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return followRepository.findFollowing(targetUser.id(), cursor, limit);
    }
}

package summer.realworld.user;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FollowRepository {
	// Key: "followerId:followeeId"
	private final Set<String> follows = ConcurrentHashMap.newKeySet();

	public void follow(Long followerId, Long followeeId) {
		follows.add(followerId + ":" + followeeId);
	}

	public void unfollow(Long followerId, Long followeeId) {
		follows.remove(followerId + ":" + followeeId);
	}

	public boolean isFollowing(Long followerId, Long followeeId) {
		return follows.contains(followerId + ":" + followeeId);
	}

	public Set<Long> getFollowing(Long followerId) {
		Set<Long> result = ConcurrentHashMap.newKeySet();
		for (String key : follows) {
			String[] parts = key.split(":");
			if (Long.parseLong(parts[0]) == followerId) {
				result.add(Long.parseLong(parts[1]));
			}
		}
		return result;
	}
}

package summer.twitter.social;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import summer.twitter.infra.SnowflakeIdGenerator;
import summer.twitter.user.User;
import summer.twitter.user.UserRepository;

/**
 * Plain unit test for {@link FollowService}.
 *
 * <p>
 * Behavioral unit test (not {@code @SummerTest}): the service's own
 * collaborators ({@code FollowRepository}, {@code UserRepository}) are mocked for
 * isolation and {@link SnowflakeIdGenerator} stays real. There is no framework
 * container and no {@code DataSource}; the JDBC layer is covered by the
 * integration test against a real Postgres.
 * </p>
 */
class FollowServiceTest {

	private FollowRepository mockFollowRepo;
	private UserRepository mockUserRepo;
	private FollowService followService;

	@BeforeEach
	void setUp() {
		mockFollowRepo = mock(FollowRepository.class);
		mockUserRepo = mock(UserRepository.class);
		followService = new FollowService(mockFollowRepo, mockUserRepo, new SnowflakeIdGenerator());
	}

	@Test
	void followInsertsAndUpdatesCounts() {
		User target = new User(100L, "bob", "Bob", "bob@x.com", "h", "bio", null, null, null);
		when(mockUserRepo.findByUsername("bob")).thenReturn(Optional.of(target));
		when(mockFollowRepo.exists(1L, 100L)).thenReturn(false);

		followService.follow(1L, "bob");

		verify(mockFollowRepo).insert(any(Follow.class));
		verify(mockUserRepo).updateCounts(100L, 1, 0);
		verify(mockUserRepo).updateCounts(1L, 0, 1);
	}

	@Test
	void followIsIdempotent() {
		User target = new User(100L, "bob", "Bob", "bob@x.com", "h", "bio", null, null, null);
		when(mockUserRepo.findByUsername("bob")).thenReturn(Optional.of(target));
		when(mockFollowRepo.exists(1L, 100L)).thenReturn(true);

		followService.follow(1L, "bob");

		verify(mockFollowRepo, never()).insert(any());
		verify(mockUserRepo, never()).updateCounts(anyLong(), anyInt(), anyInt());
	}

	@Test
	void followThrowsWhenTargetMissing() {
		when(mockUserRepo.findByUsername("ghost")).thenReturn(Optional.empty());
		assertThrows(IllegalArgumentException.class, () -> followService.follow(1L, "ghost"));
	}

	@Test
	void followThrowsWhenSelf() {
		User target = new User(1L, "me", "Me", "me@x.com", "h", "bio", null, null, null);
		when(mockUserRepo.findByUsername("me")).thenReturn(Optional.of(target));
		assertThrows(IllegalArgumentException.class, () -> followService.follow(1L, "me"));
	}

	@Test
	void unfollowDeletesAndDecrementsCounts() {
		User target = new User(100L, "bob", "Bob", "bob@x.com", "h", "bio", null, null, null);
		when(mockUserRepo.findByUsername("bob")).thenReturn(Optional.of(target));
		when(mockFollowRepo.exists(1L, 100L)).thenReturn(true);

		followService.unfollow(1L, "bob");

		verify(mockFollowRepo).delete(1L, 100L);
		verify(mockUserRepo).updateCounts(100L, -1, 0);
		verify(mockUserRepo).updateCounts(1L, 0, -1);
	}

	@Test
	void unfollowNoopWhenNotFollowing() {
		User target = new User(100L, "bob", "Bob", "bob@x.com", "h", "bio", null, null, null);
		when(mockUserRepo.findByUsername("bob")).thenReturn(Optional.of(target));
		when(mockFollowRepo.exists(1L, 100L)).thenReturn(false);

		followService.unfollow(1L, "bob");

		verify(mockFollowRepo, never()).delete(anyLong(), anyLong());
		verify(mockUserRepo, never()).updateCounts(anyLong(), anyInt(), anyInt());
	}

	@Test
	void getFollowersDelegatesToRepository() {
		User target = new User(100L, "bob", "Bob", "bob@x.com", "h", "bio", null, null, null);
		when(mockUserRepo.findByUsername("bob")).thenReturn(Optional.of(target));
		Follow f = new Follow(5L, 100L, 200L, null);
		when(mockFollowRepo.findFollowers(100L, null, 20)).thenReturn(List.of(f));

		List<Follow> result = followService.getFollowers("bob", null, 20);

		assertEquals(List.of(f), result);
		verify(mockFollowRepo).findFollowers(100L, null, 20);
	}
}

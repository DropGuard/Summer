package com.github.dropguard.summer.realworld.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.dropguard.summer.realworld.common.IllegalOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FollowServiceTest {

    private FollowRepository mockRepo;
    private FollowService service;

    @BeforeEach
    void setUp() {
        mockRepo = mock(FollowRepository.class);
        service = new FollowService(mockRepo);
    }

    @Test
    void followDelegatesToRepository() {
        service.follow(1L, 2L);

        verify(mockRepo).follow(1L, 2L);
    }

    @Test
    void followThrowsWhenSelf() {
        assertThrows(IllegalOperationException.class, () -> service.follow(5L, 5L));
        verifyNoInteractions(mockRepo);
    }

    @Test
    void unfollowDelegatesToRepository() {
        service.unfollow(1L, 2L);

        verify(mockRepo).unfollow(1L, 2L);
    }

    @Test
    void isFollowingReturnsTrueWhenFollowing() {
        when(mockRepo.isFollowing(1L, 2L)).thenReturn(true);

        assertTrue(service.isFollowing(1L, 2L));
    }

    @Test
    void isFollowingReturnsFalseWhenNotFollowing() {
        when(mockRepo.isFollowing(1L, 2L)).thenReturn(false);

        assertFalse(service.isFollowing(1L, 2L));
    }

    @Test
    void isFollowingReturnsFalseWhenCurrentUserNull() {
        assertFalse(service.isFollowing(null, 2L));
        verifyNoInteractions(mockRepo);
    }
}

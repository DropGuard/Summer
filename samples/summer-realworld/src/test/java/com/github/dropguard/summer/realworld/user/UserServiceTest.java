package com.github.dropguard.summer.realworld.user;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.test.annotation.TestResource;
import com.github.dropguard.summer.test.db.PostgresTestResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Integration test for {@link UserService} against real Postgres (Testcontainers). */
@SummerTest
@TestResource(PostgresTestResource.class)
class UserServiceTest {

    private final UserService userService;
    private final JdbcTemplate jdbcTemplate;

    public UserServiceTest(UserService userService, JdbcTemplate jdbcTemplate) {
        this.userService = userService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** The {@code @SummerTest} container + Postgres are shared across test methods; clear users. */
    @BeforeEach
    void clearUsers() {
        jdbcTemplate.update("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
    }

    @Test
    void shouldRegisterUser() {
        User user = userService.register("testuser", "test@example.com", "password123");

        assertNotNull(user);
        assertEquals("testuser", user.username());
        assertEquals("test@example.com", user.email());
        assertNotNull(user.createdAt());
        assertNotNull(user.updatedAt());

        // Re-query the DB to prove the user was actually persisted, not just returned in-memory.
        var persisted = userService.findByEmail("test@example.com");
        assertTrue(persisted.isPresent());
        assertEquals("testuser", persisted.get().username());
        assertEquals(user.id(), persisted.get().id());
    }

    @Test
    void shouldThrowWhenEmailExists() {
        userService.register("user1", "test@example.com", "password123");

        assertThrows(
                RuntimeException.class,
                () -> userService.register("user2", "test@example.com", "password456"));
    }

    @Test
    void shouldThrowWhenUsernameExists() {
        userService.register("testuser", "user1@example.com", "password123");

        assertThrows(
                RuntimeException.class,
                () -> userService.register("testuser", "user2@example.com", "password456"));
    }

    @Test
    void shouldFindByEmail() {
        userService.register("testuser", "test@example.com", "password123");

        var found = userService.findByEmail("test@example.com");

        assertTrue(found.isPresent());
        assertEquals("testuser", found.get().username());
    }

    @Test
    void shouldFindByUsername() {
        userService.register("testuser", "test@example.com", "password123");

        var found = userService.findByUsername("testuser");

        assertTrue(found.isPresent());
        assertEquals("test@example.com", found.get().email());
    }

    @Test
    void shouldUpdateUser() {
        User user = userService.register("testuser", "test@example.com", "password123");

        User updated =
                userService.update(
                        user, null, null, null, "New bio", "https://example.com/image.jpg");

        assertEquals("New bio", updated.bio());
        assertEquals("https://example.com/image.jpg", updated.image());
    }
}

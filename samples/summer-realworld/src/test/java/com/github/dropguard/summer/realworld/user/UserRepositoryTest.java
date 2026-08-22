package com.github.dropguard.summer.realworld.user;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.test.annotation.TestResource;
import com.github.dropguard.summer.test.db.PostgresTestResource;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for {@link UserRepository} against a real Postgres (Testcontainers). The
 * container boots the full app universe (DatabaseConfig → JdbcTemplate, SchemaInitializer →
 * schema).
 */
@SummerTest
@TestResource(PostgresTestResource.class)
class UserRepositoryTest {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    public UserRepositoryTest(UserRepository userRepository, JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** The {@code @SummerTest} container + Postgres are shared across test methods; clear users. */
    @BeforeEach
    void clearUsers() {
        jdbcTemplate.update("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
    }

    private static User newUser(String username, String email) {
        LocalDateTime now = LocalDateTime.now();
        return new User(null, username, email, "password123", null, null, now, now);
    }

    @Test
    void shouldSaveUser() {
        User saved = userRepository.save(newUser("testuser", "test@example.com"));

        assertNotNull(saved.id());
        assertEquals("testuser", saved.username());
    }

    @Test
    void shouldFindById() {
        User saved = userRepository.save(newUser("testuser", "test@example.com"));

        Optional<User> found = userRepository.findById(saved.id());

        assertTrue(found.isPresent());
        assertEquals("testuser", found.get().username());
    }

    @Test
    void shouldFindByEmail() {
        userRepository.save(newUser("testuser", "test@example.com"));

        Optional<User> found = userRepository.findByEmail("test@example.com");

        assertTrue(found.isPresent());
        assertEquals("testuser", found.get().username());
    }

    @Test
    void shouldFindByUsername() {
        userRepository.save(newUser("testuser", "test@example.com"));

        Optional<User> found = userRepository.findByUsername("testuser");

        assertTrue(found.isPresent());
        assertEquals("test@example.com", found.get().email());
    }

    @Test
    void shouldDeleteUser() {
        User saved = userRepository.save(newUser("testuser", "test@example.com"));

        userRepository.deleteById(saved.id());

        assertTrue(userRepository.findById(saved.id()).isEmpty());
        assertTrue(userRepository.findByEmail("test@example.com").isEmpty());
        assertTrue(userRepository.findByUsername("testuser").isEmpty());
    }
}

package com.github.dropguard.summer.realworld.user;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.realworld.common.DuplicateEmailException;
import com.github.dropguard.summer.realworld.common.DuplicateUsernameException;
import com.github.dropguard.summer.web.exception.ValidationException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.mindrot.jbcrypt.BCrypt;

@Component
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User register(String username, String email, String password) {
        if (username == null || username.isBlank()) {
            throw new ValidationException("username", "can't be blank");
        }
        if (email == null || email.isBlank()) {
            throw new ValidationException("email", "can't be blank");
        }
        if (password == null || password.isBlank()) {
            throw new ValidationException("password", "can't be blank");
        }
        if (password.length() < 8) {
            throw new ValidationException("password", "is too short (minimum is 8 characters)");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new DuplicateUsernameException("username has already been taken");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new DuplicateEmailException("email has already been taken");
        }

        LocalDateTime now = LocalDateTime.now();
        User user =
                new User(
                        null,
                        username,
                        email,
                        BCrypt.hashpw(password, BCrypt.gensalt()),
                        null,
                        null,
                        now,
                        now);
        return userRepository.save(user);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    /** Batch-loads users by id set in one query — the anti-N+1 counterpart of {@link #findById}. */
    public List<User> findByIds(java.util.Collection<Long> ids) {
        return userRepository.findByIds(ids);
    }

    public User update(
            User user, String username, String email, String password, String bio, String image) {
        String newUsername = user.username();
        String newEmail = user.email();
        String newPassword = user.password();
        String newBio = user.bio();
        String newImage = user.image();

        if (username != null) {
            if (username.isBlank()) {
                throw new ValidationException("username", "can't be blank");
            }
            Optional<User> existing = userRepository.findByUsername(username);
            if (existing.isPresent() && !existing.get().id().equals(user.id())) {
                throw new DuplicateUsernameException("username has already been taken");
            }
            newUsername = username;
        }
        if (email != null) {
            if (email.isBlank()) {
                throw new ValidationException("email", "can't be blank");
            }
            Optional<User> existing = userRepository.findByEmail(email);
            if (existing.isPresent() && !existing.get().id().equals(user.id())) {
                throw new DuplicateEmailException("email has already been taken");
            }
            newEmail = email;
        }
        if (password != null) {
            if (password.isBlank()) {
                throw new ValidationException("password", "can't be blank");
            }
            if (password.length() < 8) {
                throw new ValidationException("password", "is too short (minimum is 8 characters)");
            }
            newPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        }
        if (bio != null) {
            newBio = bio.isBlank() ? null : bio;
        }
        if (image != null) {
            newImage = image.isBlank() ? null : image;
        }

        User updated =
                new User(
                        user.id(),
                        newUsername,
                        newEmail,
                        newPassword,
                        newBio,
                        newImage,
                        user.createdAt(),
                        LocalDateTime.now());
        return userRepository.save(updated);
    }
}

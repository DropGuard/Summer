package com.github.dropguard.summer.issuetracker.user;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.issuetracker.common.BusinessException;
import com.github.dropguard.summer.issuetracker.common.IdGenerator;
import com.github.dropguard.summer.issuetracker.org.OrganizationRepository;
import com.github.dropguard.summer.issuetracker.security.Role;
import java.time.OffsetDateTime;
import java.util.Optional;

@Component
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final IdGenerator idGenerator;

    public UserService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            IdGenerator idGenerator) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.idGenerator = idGenerator;
    }

    public User registerUser(
            Long orgId,
            String username,
            String displayName,
            String email,
            String passwordHash,
            Role role) {
        if (organizationRepository.findById(orgId).isEmpty()) {
            throw BusinessException.badRequest("Organization " + orgId + " does not exist");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw BusinessException.badRequest("Username already taken: " + username);
        }
        User user =
                new User(
                        idGenerator.nextId(),
                        orgId,
                        username,
                        displayName,
                        email,
                        passwordHash,
                        role.name(),
                        OffsetDateTime.now());
        userRepository.insert(user);
        return user;
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public User require(Long id) {
        return userRepository.findById(id).orElseThrow(() -> BusinessException.notFound("User"));
    }
}

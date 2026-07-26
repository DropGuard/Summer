package summer.issuetracker.user;

import java.util.List;
import java.util.Optional;

import summer.core.Component;
import summer.issuetracker.common.BusinessException;
import summer.issuetracker.common.IdGenerator;
import summer.issuetracker.org.Organization;
import summer.issuetracker.org.OrganizationRepository;
import summer.issuetracker.security.Role;

import java.time.OffsetDateTime;

@Component
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final IdGenerator idGenerator;

    public UserService(UserRepository userRepository, OrganizationRepository organizationRepository, IdGenerator idGenerator) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.idGenerator = idGenerator;
    }

    public User registerUser(Long orgId, String username, String displayName, String email, String passwordHash, Role role) {
        if (organizationRepository.findById(orgId).isEmpty()) {
            throw BusinessException.badRequest("Organization " + orgId + " does not exist");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw BusinessException.badRequest("Username already taken: " + username);
        }
        User user = new User(idGenerator.nextId(), orgId, username, displayName, email, passwordHash,
                role.name(), OffsetDateTime.now());
        userRepository.insert(user);
        return user;
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public List<User> listByOrg(Long orgId) {
        return userRepository.findByOrg(orgId);
    }

    public List<User> listByProject(Long projectId) {
        return userRepository.findByProject(projectId);
    }

    public User require(Long id) {
        return userRepository.findById(id).orElseThrow(() -> BusinessException.notFound("User"));
    }
}

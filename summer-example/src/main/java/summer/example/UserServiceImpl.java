package summer.example;

import summer.core.Component;
import summer.tx.Transactional;
import java.util.Map;

@Component
public record UserServiceImpl(UserRepository userRepository) implements UserService {

    @Override
    @Transactional
    public User create(User user) {
        return userRepository.save(user);
    }

    @Override
    public User findById(String id) {
        return userRepository.findById(id);
    }

    @Override
    @Transactional
    public User update(String id, User user) {
        User existing = userRepository.findById(id);
        if (existing == null) {
            throw new UserNotFoundException("User not found: " + id);
        }
        existing.setName(user.getName());
        existing.setEmail(user.getEmail());
        return userRepository.save(existing);
    }

    @Override
    @Transactional
    public void delete(String id) {
        userRepository.delete(id);
    }

    @Override
    public Map<String, User> findAll() {
        return userRepository.findAll();
    }
}
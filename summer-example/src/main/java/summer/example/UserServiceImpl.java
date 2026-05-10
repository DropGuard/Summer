package summer.example;

import summer.core.Component;
import summer.tx.Transactional;

@Component
public class UserServiceImpl implements UserService {

	private final UserRepository userRepository;

	public UserServiceImpl(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

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
		User updated = new User(id, user.name(), user.email());
		return userRepository.save(updated);
	}

	@Override
	@Transactional
	public void delete(String id) {
		userRepository.deleteById(id);
	}

	@Override
	public java.util.List<User> findAll() {
		return userRepository.findAll();
	}
}
package summer.example;

import summer.core.Component;
import summer.tx.Transactional;

@Component
public class UserServiceImpl implements UserService {

	private final UserRepository userRepository;

	public UserServiceImpl(UserRepository userRepository) {
		this.userRepository = userRepository;
		this.userRepository.initSchema();
	}

	@Override
	@Transactional
	@Logged
	public User create(User user) {
		String newId = user.id() == null ? java.util.UUID.randomUUID().toString() : user.id();
		User toSave = new User(newId, user.name(), user.email());
		userRepository.insert(toSave);
		return toSave;
	}

	@Override
	@Logged
	public User findById(String id) {
		return userRepository.findById(id);
	}

	@Override
	@Transactional
	@Logged
	public User update(String id, User user) {
		User existing = userRepository.findById(id);
		if (existing == null) {
			throw new UserNotFoundException("User not found: " + id);
		}
		User updated = new User(id, user.name(), user.email());
		userRepository.update(updated);
		return updated;
	}

	@Override
	@Transactional
	@Logged
	public void delete(String id) {
		userRepository.deleteById(id);
	}

	@Override
	@Logged
	public java.util.List<User> findAll() {
		return userRepository.findAll();
	}
}

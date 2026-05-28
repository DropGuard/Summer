package summer.example;

import java.util.List;
import summer.core.Component;
import summer.data.jdbc.JdbcTemplate;

@Component
public class UserRepository {

	private final JdbcTemplate jdbcTemplate;

	public UserRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void initSchema() {
		jdbcTemplate.update(
				"CREATE TABLE IF NOT EXISTS users (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255), email VARCHAR(255))");
	}

	public void insert(User user) {
		jdbcTemplate.update("INSERT INTO users (id, name, email) VALUES (?, ?, ?)", user.id(), user.name(),
				user.email());
	}

	public void update(User user) {
		jdbcTemplate.update("UPDATE users SET name = ?, email = ? WHERE id = ?", user.name(), user.email(), user.id());
	}

	public void deleteById(String id) {
		jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
	}

	public List<User> findAll() {
		return jdbcTemplate.queryForList("SELECT id, name, email FROM users", User.class);
	}

	public User findById(String id) {
		return jdbcTemplate.queryForObject("SELECT id, name, email FROM users WHERE id = ?", User.class, id);
	}
}
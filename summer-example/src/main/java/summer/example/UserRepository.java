package summer.example;

import summer.core.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class UserRepository {
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    public UserRepository() {
        // Initial data
        save(new User(null, "Alice Smith", "alice@example.com"));
        save(new User(null, "Bob Jones", "bob@example.com"));
    }

    public List<User> findAll() {
        return new ArrayList<>(users.values());
    }

    public User findById(String id) {
        return users.get(id);
    }

    public User save(User user) {
        String id = user.id();
        if (id == null) {
            id = String.valueOf(idGenerator.getAndIncrement());
            user = new User(id, user.name(), user.email());
        }
        users.put(id, user);
        return user;
    }

    public void deleteById(String id) {
        users.remove(id);
    }
}
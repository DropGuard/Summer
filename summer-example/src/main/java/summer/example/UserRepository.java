package summer.example;

import summer.core.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class UserRepository {
    private final Map<String, User> storage = new HashMap<>();
    private int idCounter = 1;

    public User save(User user) {
        if (user.getId() == null) {
            user.setId(String.valueOf(idCounter++));
        }
        storage.put(user.getId(), user);
        return user;
    }

    public User findById(String id) {
        return storage.get(id);
    }

    public void delete(String id) {
        storage.remove(id);
    }

    public Map<String, User> findAll() {
        return new HashMap<>(storage);
    }
}
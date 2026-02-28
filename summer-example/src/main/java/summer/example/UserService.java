package summer.example;

import java.util.Map;

public interface UserService {
    User create(User user);
    User findById(String id);
    User update(String id, User user);
    void delete(String id);
    Map<String, User> findAll();
}
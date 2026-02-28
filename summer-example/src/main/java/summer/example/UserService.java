package summer.example;

import java.util.List;

public interface UserService {
    User create(User user);

    User findById(String id);

    User update(String id, User user);

    void delete(String id);

    List<User> findAll();
}
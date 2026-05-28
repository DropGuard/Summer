package com.example.baseline;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    private final Map<String, User> userMap = new ConcurrentHashMap<>();

    public UserService() {
        // Pre-populate with some data for the benchmark
        for (int i = 1; i <= 10; i++) {
            userMap.put(String.valueOf(i), new User(String.valueOf(i), "User" + i, "user" + i + "@example.com"));
        }
    }

    public User getUser(String id) {
        return userMap.get(id);
    }

    public User createUser(User user) {
        userMap.put(user.id(), user);
        return user;
    }

    public User updateUser(String id, User user) {
        if (userMap.containsKey(id)) {
            userMap.put(id, user);
            return user;
        }
        return null;
    }

    public boolean deleteUser(String id) {
        return userMap.remove(id) != null;
    }
}

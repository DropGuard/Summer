package com.github.dropguard.summer.realworld.user;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public class UserDtos {

    public record RegisterRequest(User user) {
        public record User(
                @jakarta.validation.constraints.NotBlank String username,
                @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email
                        String email,
                @jakarta.validation.constraints.NotBlank @Size(min = 8) String password) {}
    }

    public record LoginRequest(User user) {
        public record User(
                @jakarta.validation.constraints.NotBlank String email,
                @jakarta.validation.constraints.NotBlank String password) {}
    }

    public record UpdateUserRequest(User user) {
        public record User(
                String username, String email, String password, String bio, String image) {}
    }

    public record UserResponse(User user) {
        public record User(
                String email,
                String token,
                String username,
                String bio,
                String image,
                String refreshToken) {}
    }

    public record RefreshRequest(@jakarta.validation.constraints.NotBlank String refreshToken) {}

    public record ProfileResponse(Profile profile) {
        public record Profile(String username, String bio, String image, boolean following) {}
    }

    public record ErrorResponse(Map<String, List<String>> errors) {
        public static ErrorResponse of(String field, String message) {
            return new ErrorResponse(Map.of(field, List.of(message)));
        }
    }
}

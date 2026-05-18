package com.ohara.model;

public class AuthDto {

    public record RegisterRequest(
        String username,
        String email,
        String password
    ) {}

    public record LoginRequest(
        String username,
        String password
    ) {}

    public record AuthResponse(
        String token,
        String username,
        String message
    ) {}

    public record ErrorResponse(String message) {}
}

package com.ohara.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:3000")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // POST /api/auth/register
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthDto.RegisterRequest req) {
        try {
            return ResponseEntity.ok(authService.register(req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new AuthDto.ErrorResponse(e.getMessage()));
        }
    }

    // POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthDto.LoginRequest req) {
        try {
            return ResponseEntity.ok(authService.login(req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new AuthDto.ErrorResponse(e.getMessage()));
        }
    }

    // POST /api/auth/logout
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            authService.logout(auth.substring(7));
        }
        return ResponseEntity.ok(new AuthDto.ErrorResponse("로그아웃 되었습니다."));
    }

    // GET /api/auth/me  (토큰 유효성 확인)
    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth == null || !auth.startsWith("Bearer "))
            return ResponseEntity.status(401).body(new AuthDto.ErrorResponse("인증이 필요합니다."));

        String token = auth.substring(7);
        if (!authService.validateToken(token))
            return ResponseEntity.status(401).body(new AuthDto.ErrorResponse("만료된 토큰입니다."));

        String username = authService.getUsernameFromToken(token);
        return ResponseEntity.ok(new AuthDto.AuthResponse(token, username, "ok"));
    }
}

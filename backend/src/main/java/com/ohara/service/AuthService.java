package com.ohara.service;

import com.ohara.entity.User;
import com.ohara.model.AuthDto;
import com.ohara.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // 인메모리 토큰 저장소 (MVP용)
    // key: token, value: username
    private final Map<String, String> tokenStore = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    // ── 회원가입 ──────────────────────────────────────────────────
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest req) {
        if (req.username() == null || req.username().isBlank())
            throw new IllegalArgumentException("아이디를 입력해주세요.");
        if (req.email() == null || req.email().isBlank())
            throw new IllegalArgumentException("이메일을 입력해주세요.");
        if (req.password() == null || req.password().length() < 6)
            throw new IllegalArgumentException("비밀번호는 6자 이상이어야 합니다.");
        if (userRepo.existsByUsername(req.username()))
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        if (userRepo.existsByEmail(req.email()))
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");

        User user = new User();
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setPassword(encoder.encode(req.password()));
        userRepo.save(user);

        String token = generateToken(req.username());
        return new AuthDto.AuthResponse(token, req.username(), "회원가입 성공");
    }

    // ── 로그인 ────────────────────────────────────────────────────
    public AuthDto.AuthResponse login(AuthDto.LoginRequest req) {
        User user = userRepo.findByUsername(req.username())
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!encoder.matches(req.password(), user.getPassword()))
            throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");

        String token = generateToken(req.username());
        return new AuthDto.AuthResponse(token, req.username(), "로그인 성공");
    }

    // ── 토큰 검증 ─────────────────────────────────────────────────
    public boolean validateToken(String token) {
        return token != null && tokenStore.containsKey(token);
    }

    public String getUsernameFromToken(String token) {
        return tokenStore.get(token);
    }

    // ── 로그아웃 ──────────────────────────────────────────────────
    public void logout(String token) {
        tokenStore.remove(token);
    }

    private String generateToken(String username) {
        String token = UUID.randomUUID().toString();
        tokenStore.put(token, username);
        return token;
    }
}
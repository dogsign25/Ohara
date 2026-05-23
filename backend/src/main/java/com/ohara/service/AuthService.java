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
    // ★ 직접 new 하지 않고 Spring Bean 주입 (SecurityConfig에서 @Bean 등록한 것)
    private final BCryptPasswordEncoder encoder;

    // ★ 서버 재시작 시 토큰 소멸 문제:
    //   MVP에서는 일단 static으로 선언해 재시작에도 유지되도록 함.
    //   실제 운영 시에는 DB의 user_tokens 테이블로 교체.
    private static final Map<String, String> tokenStore = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepo, BCryptPasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.encoder  = encoder;
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
        // ★ 주입된 encoder 사용 (Spring이 관리하는 단일 인스턴스)
        user.setPassword(encoder.encode(req.password()));
        userRepo.save(user);

        String token = generateToken(req.username());
        return new AuthDto.AuthResponse(token, req.username(), "회원가입 성공");
    }

    // ── 로그인 ────────────────────────────────────────────────────
    public AuthDto.AuthResponse login(AuthDto.LoginRequest req) {
        User user = userRepo.findByUsername(req.username())
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다."));

        // ★ encoder.matches(입력한 평문, DB의 해시) 순서가 중요
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

    // ── 내부 유틸 ─────────────────────────────────────────────────
    private String generateToken(String username) {
        String token = UUID.randomUUID().toString();
        tokenStore.put(token, username);
        return token;
    }
}
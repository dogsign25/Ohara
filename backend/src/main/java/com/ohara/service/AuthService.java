package com.ohara.service;

import com.ohara.entity.User;
import com.ohara.entity.UserToken;
import com.ohara.model.AuthDto;
import com.ohara.repository.UserRepository;
import com.ohara.repository.UserTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Map<String, String> fallbackTokenStore = new ConcurrentHashMap<>();

    private final UserRepository userRepo;
    private final UserTokenRepository tokenRepo;
    // ★ 직접 new 하지 않고 Spring Bean 주입 (SecurityConfig에서 @Bean 등록한 것)
    private final BCryptPasswordEncoder encoder;

    public AuthService(UserRepository userRepo,
                       UserTokenRepository tokenRepo,
                       BCryptPasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.tokenRepo = tokenRepo;
        this.encoder  = encoder;
    }

    /**
     * 회원가입 요청을 검증하고 사용자를 생성합니다.
     * username/email은 앞뒤 공백을 제거해 저장하며, 비밀번호는 BCrypt 해시로 저장합니다.
     * 성공 시 로그인 토큰을 즉시 발급해 가입 후 바로 로그인된 상태로 진입할 수 있게 합니다.
     */
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest req) {
        if (req.username() == null || req.username().isBlank())
            throw new IllegalArgumentException("아이디를 입력해주세요.");
        if (req.email() == null || req.email().isBlank())
            throw new IllegalArgumentException("이메일을 입력해주세요.");
        if (req.password() == null || req.password().length() < 6)
            throw new IllegalArgumentException("비밀번호는 6자 이상이어야 합니다.");
        String username = req.username().trim();
        String email = req.email().trim();

        if (userRepo.existsByUsername(username))
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        if (userRepo.existsByEmail(email))
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        // ★ 주입된 encoder 사용 (Spring이 관리하는 단일 인스턴스)
        user.setPassword(encoder.encode(req.password()));
        userRepo.save(user);

        String token = generateToken(user);
        return new AuthDto.AuthResponse(token, username, "회원가입 성공");
    }

    /**
     * 로그인 요청을 검증합니다.
     * DB에 저장된 BCrypt 해시와 입력 비밀번호를 비교하고 성공 시 새 토큰을 발급합니다.
     */
    public AuthDto.AuthResponse login(AuthDto.LoginRequest req) {
        if (req.username() == null || req.username().isBlank() || req.password() == null)
            throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");

        String username = req.username().trim();
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다."));

        // ★ encoder.matches(입력한 평문, DB의 해시) 순서가 중요
        if (!encoder.matches(req.password(), user.getPassword()))
            throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");

        String token = generateToken(user);
        return new AuthDto.AuthResponse(token, req.username(), "로그인 성공");
    }

    /**
     * 토큰이 현재 유효한지 확인합니다.
     * 우선 메모리 fallback 저장소를 확인하고, 없으면 MySQL user_tokens 테이블을 조회합니다.
     */
    public boolean validateToken(String token) {
        if (token == null) return false;
        if (fallbackTokenStore.containsKey(token)) return true;
        try {
            return tokenRepo.existsById(token);
        } catch (DataAccessException e) {
            log.warn("토큰 DB 조회 실패: {}", e.getMostSpecificCause().getMessage());
            return false;
        }
    }

    /**
     * 토큰에서 username을 복원합니다.
     * UserToken.user는 LAZY 관계이므로 JPA 트랜잭션 안에서 조회해 LazyInitialization 문제를 피합니다.
     */
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public String getUsernameFromToken(String token) {
        String fallbackUsername = fallbackTokenStore.get(token);
        if (fallbackUsername != null) return fallbackUsername;

        try {
            return tokenRepo.findById(token)
                    .map(userToken -> userToken.getUser().getUsername())
                    .orElse(null);
        } catch (DataAccessException e) {
            log.warn("토큰 DB 조회 실패: {}", e.getMostSpecificCause().getMessage());
            return null;
        }
    }

    /**
     * 로그아웃 시 fallback 저장소와 DB 토큰을 모두 정리합니다.
     * DB 토큰 삭제 실패는 로그로 남기고 요청 자체는 실패시키지 않습니다.
     */
    public void logout(String token) {
        fallbackTokenStore.remove(token);
        try {
            if (tokenRepo.existsById(token)) {
                tokenRepo.deleteById(token);
            }
        } catch (DataAccessException e) {
            log.warn("토큰 DB 삭제 실패: {}", e.getMostSpecificCause().getMessage());
        }
    }

    /**
     * UUID 기반 로그인 토큰을 생성하고 user_tokens 테이블에 저장합니다.
     * 토큰 DB 저장에 실패해도 로그인 흐름이 완전히 깨지지 않도록 메모리 fallback에 저장합니다.
     */
    private String generateToken(User user) {
        String token = UUID.randomUUID().toString();
        UserToken userToken = new UserToken();
        userToken.setToken(token);
        userToken.setUser(user);
        try {
            tokenRepo.save(userToken);
        } catch (DataAccessException e) {
            fallbackTokenStore.put(token, user.getUsername());
            log.warn("토큰 DB 저장 실패, 메모리 토큰으로 대체합니다: {}", e.getMostSpecificCause().getMessage());
        }
        return token;
    }
}

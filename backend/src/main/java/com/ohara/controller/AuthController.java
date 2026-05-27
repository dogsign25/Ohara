package com.ohara.controller;

import com.ohara.model.AuthDto;
import com.ohara.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/register
     * 새 사용자를 만들고, 성공하면 토큰을 발급한 뒤 서버 세션에도 로그인 상태를 저장합니다.
     * 프론트엔드는 응답의 token/username을 세션 스토리지에 보관하고,
     * 서버는 JSESSIONID로 새로고침 이후 로그인 상태를 복원합니다.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthDto.RegisterRequest req, HttpSession session) {
        try {
            AuthDto.AuthResponse response = authService.register(req);
            saveSession(session, response);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new AuthDto.ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new AuthDto.ErrorResponse(e.getMessage()));
        }
    }

    /**
     * POST /api/auth/login
     * username/password를 검증하고 성공 시 토큰과 HttpSession을 함께 발급합니다.
     * 검증 실패는 400으로 내려 클라이언트가 사용자에게 메시지를 표시할 수 있게 합니다.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthDto.LoginRequest req, HttpSession session) {
        try {
            AuthDto.AuthResponse response = authService.login(req);
            saveSession(session, response);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new AuthDto.ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new AuthDto.ErrorResponse(e.getMessage()));
        }
    }

    /**
     * POST /api/auth/logout
     * Authorization 헤더의 토큰을 삭제하고 서버 세션을 무효화합니다.
     * 세션과 토큰을 모두 정리해 새로고침 후 자동 복원이 일어나지 않게 합니다.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestHeader(value = "Authorization", required = false) String auth,
            HttpSession session) {
        if (auth != null && auth.startsWith("Bearer ")) {
            authService.logout(auth.substring(7));
        }
        session.invalidate();
        return ResponseEntity.ok(new AuthDto.ErrorResponse("로그아웃 되었습니다."));
    }

    /**
     * GET /api/auth/me
     * 1순위로 기존 HttpSession에서 로그인 상태를 복원합니다.
     * 세션이 없으면 Authorization Bearer 토큰을 검증하고,
     * 유효한 토큰이면 새 세션을 만들어 이후 새로고침에 대비합니다.
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(
            @RequestHeader(value = "Authorization", required = false) String auth,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                Object sessionUsername = session.getAttribute("username");
                Object sessionToken = session.getAttribute("token");
                if (sessionUsername instanceof String username && sessionToken instanceof String token) {
                    return ResponseEntity.ok(new AuthDto.AuthResponse(token, username, "ok"));
                }
            } catch (IllegalStateException ignored) {
                session = null;
            }
        }

        if (auth == null || !auth.startsWith("Bearer "))
            return ResponseEntity.status(401).body(new AuthDto.ErrorResponse("인증이 필요합니다."));

        String token = auth.substring(7);
        if (!authService.validateToken(token))
            return ResponseEntity.status(401).body(new AuthDto.ErrorResponse("만료된 토큰입니다."));

        String username = authService.getUsernameFromToken(token);
        if (username == null)
            return ResponseEntity.status(401).body(new AuthDto.ErrorResponse("만료된 토큰입니다."));

        HttpSession newSession = request.getSession(true);
        newSession.setAttribute("username", username);
        newSession.setAttribute("token", token);
        return ResponseEntity.ok(new AuthDto.AuthResponse(token, username, "ok"));
    }

    /**
     * 인증 성공 응답을 HttpSession에 저장합니다.
     * 컨트롤러에서 세션 저장을 한 곳으로 모아 회원가입/로그인 흐름을 동일하게 유지합니다.
     */
    private void saveSession(HttpSession session, AuthDto.AuthResponse response) {
        session.setAttribute("username", response.username());
        session.setAttribute("token", response.token());
    }
}

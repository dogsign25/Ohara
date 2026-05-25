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

    // POST /api/auth/register
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

    // POST /api/auth/login
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

    // POST /api/auth/logout
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

    // GET /api/auth/me  (토큰 유효성 확인)
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

    private void saveSession(HttpSession session, AuthDto.AuthResponse response) {
        session.setAttribute("username", response.username());
        session.setAttribute("token", response.token());
    }
}

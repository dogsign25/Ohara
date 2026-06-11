package com.ohara.model;

/**
 * 인증 API에서 사용하는 요청/응답 DTO 묶음입니다.
 *
 * 사용 출처:
 * - AuthController는 RequestBody와 ResponseEntity body 타입으로 사용합니다.
 * - AuthService는 register/login 성공 시 AuthResponse를 생성합니다.
 * - 프론트 frontend/src/api/auth.js는 token, username, message를 받아 로그인 상태를 저장합니다.
 */
public class AuthDto {

    /**
     * 회원가입 요청 본문입니다.
     * 수신 출처: AuthController.register()
     */
    public record RegisterRequest(
        String username,
        String email,
        String password
    ) {}

    /**
     * 로그인 요청 본문입니다.
     * 수신 출처: AuthController.login()
     */
    public record LoginRequest(
        String username,
        String password
    ) {}

    /**
     * 인증 성공 응답입니다.
     *
     * 생성 출처:
     * - AuthService.register()
     * - AuthService.login()
     * - AuthController.me()
     *
     * token은 API 인증과 세션 복원 fallback에 사용됩니다.
     */
    public record AuthResponse(
        String token,
        String username,
        String message
    ) {}

    /**
     * 인증 실패 또는 로그아웃처럼 message만 필요한 응답입니다.
     * 생성 출처: AuthController의 예외 처리 및 logout()
     */
    public record ErrorResponse(String message) {}
}

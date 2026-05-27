package com.ohara.model;

public class AuthDto {

    /** 회원가입 요청 본문입니다. */
    public record RegisterRequest(
        String username,
        String email,
        String password
    ) {}

    /** 로그인 요청 본문입니다. */
    public record LoginRequest(
        String username,
        String password
    ) {}

    /** 인증 성공 응답입니다. token은 API 인증과 세션 복원 fallback에 사용됩니다. */
    public record AuthResponse(
        String token,
        String username,
        String message
    ) {}

    /** 인증 실패 또는 로그아웃처럼 message만 필요한 응답입니다. */
    public record ErrorResponse(String message) {}
}

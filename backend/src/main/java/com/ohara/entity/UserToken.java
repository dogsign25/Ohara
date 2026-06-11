package com.ohara.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 발급된 로그인 토큰을 저장하는 엔티티입니다.
 * 서버 재시작 후에도 Authorization Bearer 토큰을 검증할 수 있도록 User와 연결합니다.
 *
 * 사용 출처:
 * - AuthService.generateToken()이 생성/저장합니다.
 * - AuthService.validateToken()이 token 존재 여부를 확인합니다.
 * - AuthService.getUsernameFromToken()이 token에서 user.username을 복원합니다.
 */
@Entity
@Table(name = "user_tokens")
public class UserToken {

    @Id
    @Column(length = 64)
    /** UUID 문자열 토큰입니다. UserTokenRepository의 ID 타입으로 사용됩니다. */
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    /** 토큰 소유 사용자입니다. LAZY 관계이므로 AuthService.getUsernameFromToken()은 트랜잭션 안에서 접근합니다. */
    private User user;

    @Column(name = "created_at", nullable = false)
    /** 토큰 발급 시각입니다. 현재 만료 정책은 없지만 추후 만료 처리 기준으로 쓸 수 있습니다. */
    private LocalDateTime createdAt = LocalDateTime.now();

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

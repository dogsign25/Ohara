package com.ohara.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 로그인 가능한 사용자 계정입니다.
 * username/email은 중복을 허용하지 않고, password에는 BCrypt 해시만 저장합니다.
 *
 * 사용 출처:
 * - AuthService.register()가 생성합니다.
 * - AuthService.login()이 username으로 조회하고 password 해시를 검증합니다.
 * - WorkspaceService.getUserByToken()이 워크스페이스 소유권 검사의 기준 사용자로 조회합니다.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    /** MySQL users.id 기본키입니다. Workspace.user, UserToken.user의 FK 대상입니다. */
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    /** 로그인 아이디입니다. UserRepository.findByUsername()/existsByUsername()에서 사용합니다. */
    private String username;

    @Column(unique = true, nullable = false, length = 100)
    /** 사용자 이메일입니다. 회원가입 중복 검사에 사용합니다. */
    private String email;

    @Column(nullable = false)
    /** BCrypt 해시 문자열입니다. 평문 비밀번호는 저장하지 않습니다. */
    private String password;

    @Column(name = "created_at")
    /** 계정 생성 시각입니다. 현재 생성 시 기본값으로 기록합니다. */
    private LocalDateTime createdAt = LocalDateTime.now();

    // getters/setters: AuthService와 JPA가 사용자 필드를 읽고 쓸 때 사용합니다.
    public Long getId()                      { return id; }
    public String getUsername()              { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail()                 { return email; }
    public void setEmail(String email)       { this.email = email; }
    public String getPassword()              { return password; }
    public void setPassword(String password) { this.password = password; }
    public LocalDateTime getCreatedAt()      { return createdAt; }
}

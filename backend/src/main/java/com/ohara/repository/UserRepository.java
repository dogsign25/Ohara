package com.ohara.repository;

import com.ohara.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * users 테이블 접근용 Spring Data JPA Repository입니다.
 *
 * 사용 출처:
 * - AuthService.register()/login()
 * - WorkspaceService.getUserByToken()
 *
 * 엔티티 출처:
 * - User는 entity/User.java에 정의되어 있습니다.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * 로그인과 토큰 복원에서 username으로 사용자를 찾습니다.
     * 호출 출처: AuthService.login(), WorkspaceService.getUserByToken()
     */
    Optional<User> findByUsername(String username);

    /**
     * 회원가입 시 username 중복을 빠르게 검사합니다.
     * 호출 출처: AuthService.register()
     */
    boolean existsByUsername(String username);

    /**
     * 회원가입 시 email 중복을 빠르게 검사합니다.
     * 호출 출처: AuthService.register()
     */
    boolean existsByEmail(String email);
}

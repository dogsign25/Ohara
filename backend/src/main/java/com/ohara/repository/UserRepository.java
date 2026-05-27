package com.ohara.repository;

import com.ohara.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    /** 로그인과 토큰 복원에서 username으로 사용자를 찾습니다. */
    Optional<User> findByUsername(String username);

    /** 현재는 중복 검사 보조용으로 사용할 수 있는 이메일 조회입니다. */
    Optional<User> findByEmail(String email);

    /** 회원가입 시 username 중복을 빠르게 검사합니다. */
    boolean existsByUsername(String username);

    /** 회원가입 시 email 중복을 빠르게 검사합니다. */
    boolean existsByEmail(String email);
}

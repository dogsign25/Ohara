package com.ohara.repository;

import com.ohara.entity.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 로그인 토큰을 저장/조회/삭제하는 JPA Repository입니다.
 * 기본 CRUD 메서드만으로 AuthService의 토큰 검증 흐름을 처리합니다.
 */
public interface UserTokenRepository extends JpaRepository<UserToken, String> {
}

package com.ohara.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 설정 클래스입니다.
 *
 * 사용 출처:
 * - Spring Boot가 시작될 때 자동으로 읽어 SecurityFilterChain과 BCryptPasswordEncoder Bean을 등록합니다.
 * - AuthService는 passwordEncoder()가 등록한 BCryptPasswordEncoder Bean을 생성자 주입으로 사용합니다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // ★ BCryptPasswordEncoder를 Spring Bean으로 등록
    // AuthService에서 new로 만들면 Spring Security 내부 인코더 체인과 충돌 가능
    /**
     * 사용자 비밀번호 저장/검증에 사용할 BCrypt 인코더입니다.
     * 회원가입 시 평문 비밀번호는 이 인코더를 통해 해시로 저장되고,
     * 로그인 시 입력 비밀번호와 DB 해시를 matches()로 비교합니다.
     *
     * 호출 출처:
     * - 직접 호출하는 코드는 없고, Spring 컨테이너가 Bean으로 등록한 뒤 AuthService 생성자에 주입합니다.
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * API 서버의 SecurityFilterChain을 구성합니다.
     * 현재 프로젝트는 컨트롤러에서 직접 세션/토큰 인증을 처리하므로
     * Spring Security의 폼 로그인과 HTTP Basic은 비활성화합니다.
     * 세션 정책은 IF_REQUIRED로 두어 로그인 성공 시 HttpSession을 만들 수 있게 합니다.
     *
     * 호출 출처:
     * - 직접 호출하는 코드는 없고, Spring Security가 애플리케이션 시작 시 이 Bean을 사용합니다.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // AuthController/WorkspaceController가 JSON API로 동작하므로 CSRF 토큰 검증은 개발 환경에서 끕니다.
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // ★ Spring Security의 기본 폼 로그인 비활성화
                .formLogin(fl -> fl.disable())
                .httpBasic(hb -> hb.disable())
                .authorizeHttpRequests(auth -> auth
                        // 인증 검증은 AuthController/AuthService와 각 서비스의 토큰 검사에서 직접 수행합니다.
                        .anyRequest().permitAll()
                );
        return http.build();
    }
}

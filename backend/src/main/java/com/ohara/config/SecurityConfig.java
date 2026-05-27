package com.ohara.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // ★ BCryptPasswordEncoder를 Spring Bean으로 등록
    // AuthService에서 new로 만들면 Spring Security 내부 인코더 체인과 충돌 가능
    /**
     * 사용자 비밀번호 저장/검증에 사용할 BCrypt 인코더입니다.
     * 회원가입 시 평문 비밀번호는 이 인코더를 통해 해시로 저장되고,
     * 로그인 시 입력 비밀번호와 DB 해시를 matches()로 비교합니다.
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
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // ★ Spring Security의 기본 폼 로그인 비활성화
                .formLogin(fl -> fl.disable())
                .httpBasic(hb -> hb.disable())
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                );
        return http.build();
    }
}

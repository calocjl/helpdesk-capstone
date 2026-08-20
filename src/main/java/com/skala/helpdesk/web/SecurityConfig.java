package com.skala.helpdesk.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Phase 7 — 인증·인가 (교안 313쪽 패키지 지도).
 *
 * <p>{@code /api/admin/**}는 ADMIN 역할만 접근 가능하다. {@code /api/chat/**}는
 * 인증된 사용자면 누구나 접근 가능하되, {@code Principal.getName()}이 곧 도구
 * 권한 검증에 쓰이는 userId가 된다 — 로그인 계정과 주문 소유자가 실제로 일치해야
 * 정상적으로 조회된다.
 *
 * <p>테스트 계정 (데모용 — 운영에 그대로 쓰지 않는다):
 * <ul>
 *   <li>user1 / pass — USER (주문 12345, 12346 보유)</li>
 *   <li>user2 / pass — USER (주문 99999 보유 — user1 입장에선 "남의 주문")</li>
 *   <li>admin / admin — ADMIN (관리자 API 전용)</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        var user1 = User.withUsername("user1").password(encoder.encode("pass")).roles("USER").build();
        var user2 = User.withUsername("user2").password(encoder.encode("pass")).roles("USER").build();
        var admin = User.withUsername("admin").password(encoder.encode("admin")).roles("ADMIN").build();
        return new InMemoryUserDetailsManager(user1, user2, admin);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())               // 상태 없는 API — 폼 기반이 아니다
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/chat/**").authenticated()
                        .anyRequest().permitAll())
                .httpBasic(basic -> {})                     // Swagger의 Authorize 버튼으로 로그인
                .build();
    }
}

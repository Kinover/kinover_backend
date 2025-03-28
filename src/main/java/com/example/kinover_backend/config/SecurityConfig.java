package com.example.kinover_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // CSRF 비활성화 (필요시 활성화)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/login/kakao").permitAll() // 로그인 관련 API는 인증 없이 접근 허용
                        .requestMatchers("/swagger-ui/**", "/api-docs/**","/v3/api-docs/**", "/swagger-ui.html").permitAll() // Swagger UI와 API 문서도 인증 없이 허용
                        .requestMatchers("/api/**").authenticated() // /api/** 요청은 모두 JWT 인증 필요
                        .anyRequest().authenticated() // 나머지 요청은 인증 필요
                )
                .addFilterBefore(jwtAuthenticationFilter, SecurityContextPersistenceFilter.class)
                .formLogin((formLogin) -> formLogin.disable())
                .logout((logoutConfig) -> logoutConfig.disable())
                .httpBasic(httpBasic -> httpBasic.disable()); // 기본 HTTP 인증 비활성화

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}

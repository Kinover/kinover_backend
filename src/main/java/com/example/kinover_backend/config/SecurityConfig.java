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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

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
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // ✅ 여기서 직접 지정

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

    // ✅ CORS 설정 직접 정의
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Collections.singletonList("*")); // OR 실제 도메인 목록
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Collections.singletonList("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return (CorsConfigurationSource) source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}

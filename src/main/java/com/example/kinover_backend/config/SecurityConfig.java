// src/main/java/com/example/kinover_backend/config/SecurityConfig.java
package com.example.kinover_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

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
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                // ✅ Preflight는 무조건 통과
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ✅ 인증 없이 허용할 것들
                .requestMatchers("/api/login/kakao").permitAll()
                .requestMatchers(
                    "/swagger-ui/**",
                    "/api-docs/**",
                    "/v3/api-docs/**",
                    "/swagger-ui.html"
                ).permitAll()

                // ✅ WebSocket endpoint
                .requestMatchers("/chat", "/status", "/family-status").permitAll()

                // ✅ 스프링 기본 에러 경로 (인증 걸면 꼬일 수 있음)
                .requestMatchers("/error").permitAll()

                // ✅ API는 인증 필요
                .requestMatchers("/api/**").authenticated()

                // 나머지
                .anyRequest().authenticated()
            )
            // ✅ JWT 필터는 UsernamePasswordAuthenticationFilter 이전에
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .formLogin(formLogin -> formLogin.disable())
            .logout(logoutConfig -> logoutConfig.disable())
            .httpBasic(httpBasic -> httpBasic.disable())
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            .sessionManagement(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // ✅ allowedOrigins에 * / 포트 와일드카드가 필요하면 allowedOriginPatterns를 써야 함
        config.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:*",
            "http://13.124.192.206:*",
            "http://13.124.192.206",
            "https://kinover.shop",
            "https://www.kinover.shop"
        ));

        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("*"));

        // Authorization 헤더 노출(필요 시)
        config.setExposedHeaders(Arrays.asList("Authorization"));

        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}

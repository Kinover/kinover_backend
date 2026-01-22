// src/main/java/com/example/kinover_backend/config/SecurityConfig.java
package com.example.kinover_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
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
                // ✅ Preflight 무조건 통과
                .requestMatchers(new AntPathRequestMatcher("/**", HttpMethod.OPTIONS.name())).permitAll()

                // ✅ 인증 없이 허용
                .requestMatchers(new AntPathRequestMatcher("/api/login/kakao")).permitAll()

                // ✅ Swagger / OpenAPI
                .requestMatchers(
                    new AntPathRequestMatcher("/swagger-ui/**"),
                    new AntPathRequestMatcher("/v3/api-docs/**"),
                    new AntPathRequestMatcher("/swagger-ui.html")
                ).permitAll()

                // ✅ WebSocket endpoint
                .requestMatchers(
                    new AntPathRequestMatcher("/chat/**"),
                    new AntPathRequestMatcher("/status/**"),
                    new AntPathRequestMatcher("/family-status/**")
                ).permitAll()

                // ✅ 기본 에러
                .requestMatchers(new AntPathRequestMatcher("/error")).permitAll()

                // ✅ API는 인증 필요
                .requestMatchers(new AntPathRequestMatcher("/api/**")).authenticated()

                .anyRequest().authenticated()
            )
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

        config.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:*",
            "http://13.124.192.206:*",
            "http://13.124.192.206",
            "https://kinover.shop",
            "https://www.kinover.shop"
        ));

        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("*"));
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

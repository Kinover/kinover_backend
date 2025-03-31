package com.example.kinover_backend.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**") // 모든 경로에 대해
                        .allowedOrigins("http://localhost:8080","http://localhost:8081","http://localhost:3000", "http://13.209.88.75:8081") // 프론트 도메인 정확히 명시
                        .allowedMethods("*") // 모든 HTTP 메소드 허용
                        .allowedHeaders("*")
                        .allowCredentials(true); // 쿠키/인증 정보 포함 허용
            }
        };
    }
}

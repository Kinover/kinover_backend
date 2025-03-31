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
                registry.addMapping("/**")
                        .allowedOriginPatterns("*") // ✅ 이걸로 변경 (패턴 허용)
                        .allowedMethods("*")
                        .allowedHeaders("*")
                        .allowCredentials(true); // ✅ 인증 정보 허용 시 *은 안 됨 → patterns만 가능
            }
        };
    }
}

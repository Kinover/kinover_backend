package com.example.kinover_backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components())
                .info(apiInfo());
    }


    private Info apiInfo() {
        return new Info()
                .title("kinover API 문서") // API의 제목
                .description("가족과 함께 보낸 모든 순간을 소중히 기록하세요. \n" +
                        "우리의 소중한 이야기, 함께 만들어가는 행복한 기억들.") // API에 대한 설명
                .version("2.0"); // API의 버전
    }
}
package com.example.kinover_backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    private final ApplicationContext ctx;

    // 우선순위 1: application.yml 프로퍼티 (예: classpath:... 또는 file:/... 또는 절대경로)
    @Value("${firebase.credentials:}")
    private String credentialsLocation;

    public FirebaseConfig(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @PostConstruct
    public void initialize() {
        try (InputStream serviceAccount = openCredentialsStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            System.out.println("✅ Firebase initialized.");
        } catch (Exception e) {
            System.err.println("❌ Firebase initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private InputStream openCredentialsStream() throws IOException {
        // 우선순위 2: 환경변수 GOOGLE_APPLICATION_CREDENTIALS (파일 경로)
        String envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");

        // 1) 프로퍼티가 설정되어 있으면 그대로 사용 (classpath:/file: 프리픽스 지원)
        if (StringUtils.hasText(credentialsLocation)) {
            Resource r = toResource(credentialsLocation);
            if (!r.exists()) {
                throw new IOException("Credential not found at " + r.getDescription());
            }
            return r.getInputStream();
        }

        // 2) 환경변수 사용
        if (StringUtils.hasText(envPath)) {
            Resource r = toResource(envPath);
            if (!r.exists()) {
                throw new IOException("Credential not found at " + r.getDescription());
            }
            return r.getInputStream();
        }

        // 3) 기본값: 클래스패스 firebase/firebase-service-account.json
        Resource fallback = ctx.getResource("classpath:firebase/firebase-service-account.json");
        if (!fallback.exists()) {
            throw new IOException(
                    "No Firebase credential found. " +
                    "Set 'firebase.credentials', or GOOGLE_APPLICATION_CREDENTIALS, " +
                    "or provide classpath:firebase/firebase-service-account.json");
        }
        return fallback.getInputStream();
    }

    private Resource toResource(String location) {
        // prefix 없으면 파일 경로로 간주
        if (location.startsWith("classpath:") || location.startsWith("file:")) {
            return ctx.getResource(location);
        }
        return ctx.getResource("file:" + location);
    }
}

package com.example.kinover_backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initialize() {
        try {
            InputStream serviceAccount =
                    getClass().getClassLoader().getResourceAsStream("firebase/firebase-service-account.json");

            if (serviceAccount == null) {
                throw new IllegalStateException("firebase-service-account.json 파일을 찾을 수 없습니다. 경로 확인 필요.");
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }

            System.out.println("✅ Firebase initialized.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

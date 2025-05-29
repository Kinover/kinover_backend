package com.example.kinover_backend.service;

import com.example.kinover_backend.entity.ChatRoom;
import com.example.kinover_backend.entity.FcmToken;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.entity.ChatRoomNotificationSetting;
import com.example.kinover_backend.repository.ChatRoomNotificationRepository;
import com.example.kinover_backend.repository.FcmTokenRepository;
import com.example.kinover_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FcmTokenService {

    private final UserRepository userRepository;
    private final FcmTokenRepository fcmTokenRepository;

    public void saveToken(Long userId, String token) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        FcmToken fcmToken = fcmTokenRepository.findByUser(user)
                .orElse(new FcmToken());
        fcmToken.setUser(user);
        fcmToken.setToken(token);
        fcmToken.setUpdatedAt(LocalDateTime.now());

        fcmTokenRepository.save(fcmToken);
    }
}


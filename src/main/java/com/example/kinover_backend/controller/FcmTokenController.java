package com.example.kinover_backend.controller;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.service.FcmTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "FCM 토큰 Controller", description = "백그라운드 알림에 사용되는 FCM 토큰을 관리합니다")
@RestController
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@RequestMapping("/api/fcm")
public class FcmTokenController {

    private final FcmTokenService fcmTokenService;
    private final JwtUtil jwtUtil;

    @Operation(summary = "FCM 토큰 저장", description = "유저의 FCM 토큰을 서버에 저장합니다.")
    @PostMapping("/register")
    public ResponseEntity<Void> registerFcmToken(@RequestHeader("Authorization") String token,
                                                 @RequestBody Map<String, String> request) {
        String jwt = token.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(jwt);
        fcmTokenService.saveToken(userId, request.get("fcmToken"));
        return ResponseEntity.ok().build();
    }
}

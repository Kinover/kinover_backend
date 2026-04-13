package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.PhoneVerifyRequestDto;
import com.example.kinover_backend.service.PhoneVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PhoneVerificationController {

    private final PhoneVerificationService phoneVerificationService;

    /**
     * 전화번호 인증 완료 처리
     * - 클라이언트가 Firebase SDK로 전화번호 인증 후 받은 idToken을 전송
     * - 중복 전화번호 감지 시 409 Conflict 반환
     *
     * POST /api/auth/phone/verify
     * Body: { "userId": 123, "idToken": "firebase_id_token" }
     */
    @PostMapping("/phone/verify")
    public ResponseEntity<Void> verifyPhone(@Valid @RequestBody PhoneVerifyRequestDto request) {
        phoneVerificationService.verifyPhone(request.getUserId(), request.getIdToken());
        return ResponseEntity.ok().build();
    }
}

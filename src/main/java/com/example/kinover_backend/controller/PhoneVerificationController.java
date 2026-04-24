package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.PhoneVerifyRequestDto;
import com.example.kinover_backend.service.PhoneVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PhoneVerificationController {

    private final PhoneVerificationService phoneVerificationService;

    private Long getAuthUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new RuntimeException("인증 정보가 없습니다.");
        }
        return (Long) auth.getPrincipal();
    }

    /**
     * 전화번호 인증 완료 처리
     * - Authorization: Bearer JWT 필수 (본인 계정만 갱신)
     * - 클라이언트가 Firebase SDK로 전화번호 인증 후 받은 idToken을 전송
     * - 중복 전화번호 감지 시 409 Conflict 반환
     *
     * POST /api/auth/phone/verify
     * Body: { "idToken": "firebase_id_token" }
     */
    @PostMapping("/phone/verify")
    public ResponseEntity<Void> verifyPhone(@RequestBody PhoneVerifyRequestDto request) {
        Long userId = getAuthUserId();

        boolean isTestRequest = request.getTestPhone() != null && request.getTestCode() != null;
        if (isTestRequest) {
            phoneVerificationService.verifyPhoneForTest(userId, request.getTestPhone(), request.getTestCode());
            return ResponseEntity.ok().build();
        }

        if (request.getIdToken() == null || request.getIdToken().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        phoneVerificationService.verifyPhone(userId, request.getIdToken());
        return ResponseEntity.ok().build();
    }
}

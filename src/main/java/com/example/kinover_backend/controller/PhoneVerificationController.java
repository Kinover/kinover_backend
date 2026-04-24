package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.PhoneVerifyRequestDto;
import com.example.kinover_backend.service.PhoneVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<Void> verifyPhone(
            @RequestBody PhoneVerifyRequestDto request,
            HttpServletRequest httpServletRequest
    ) {
        Long userId = getAuthUserId();
        String clientIp = resolveClientIp(httpServletRequest);

        String idToken = normalize(request.getIdToken());
        String testPhone = normalize(request.getTestPhone());
        String testCode = normalize(request.getTestCode());

        boolean hasIdToken = idToken != null;
        boolean hasTestPhone = testPhone != null;
        boolean hasTestCode = testCode != null;
        boolean isTestRequest = hasTestPhone && hasTestCode && !hasIdToken;
        boolean isNormalRequest = hasIdToken && !hasTestPhone && !hasTestCode;

        if (!isTestRequest && !isNormalRequest) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_VERIFY_PAYLOAD",
                    "요청 형식이 올바르지 않습니다. idToken 또는 testPhone+testCode 중 하나만 보내야 합니다."
            );
        }

        if (isTestRequest) {
            phoneVerificationService.verifyPhoneForTest(userId, testPhone, testCode, clientIp);
            return ResponseEntity.ok().build();
        }

        phoneVerificationService.verifyPhone(userId, idToken, clientIp);
        return ResponseEntity.ok().build();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

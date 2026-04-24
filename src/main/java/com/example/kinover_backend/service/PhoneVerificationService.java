package com.example.kinover_backend.service;

import com.example.kinover_backend.controller.ApiException;
import com.example.kinover_backend.controller.DuplicatePhoneNumberException;
import com.example.kinover_backend.controller.NotFoundException;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    private static final Logger log = LoggerFactory.getLogger(PhoneVerificationService.class);

    private static final long DEFAULT_WINDOW_MILLIS = 60_000L;
    private static final int DEFAULT_VERIFY_LIMIT = 10;
    private static final int DEFAULT_TEST_VERIFY_LIMIT = 5;
    private static final int DEFAULT_INVALID_TEST_CODE_LIMIT = 3;

    private final UserRepository userRepository;
    private final UserService userService;
    private final PhoneVerifyRateLimitService phoneVerifyRateLimitService;

    @Value("${ALLOW_TEST_OTP_BYPASS:false}")
    private boolean allowTestOtpBypass;

    @Value("${TEST_OTP_ALLOWLIST:}")
    private String testOtpAllowlist;

    @Value("${TEST_OTP_CODE:}")
    private String testOtpCode;

    @Value("${PHONE_VERIFY_RATE_LIMIT_MAX_ATTEMPTS:" + DEFAULT_VERIFY_LIMIT + "}")
    private int verifyRateLimitMaxAttempts;

    @Value("${PHONE_VERIFY_RATE_LIMIT_WINDOW_MS:" + DEFAULT_WINDOW_MILLIS + "}")
    private long verifyRateLimitWindowMillis;

    @Value("${PHONE_VERIFY_TEST_RATE_LIMIT_MAX_ATTEMPTS:" + DEFAULT_TEST_VERIFY_LIMIT + "}")
    private int testRateLimitMaxAttempts;

    @Value("${PHONE_VERIFY_TEST_INVALID_CODE_MAX_ATTEMPTS:" + DEFAULT_INVALID_TEST_CODE_LIMIT + "}")
    private int testInvalidCodeMaxAttempts;

    @Transactional
    public void verifyPhoneForTest(Long userId, String testPhone, String testCode, String clientIp) {
        String maskedPhone = maskPhone(testPhone);
        String maskedCode = maskCode(testCode);
        log.info("event=TEST_OTP_VERIFY_ATTEMPT userId={} ip={} phone={} code={}", userId, clientIp, maskedPhone, maskedCode);

        enforceRateLimit(buildLimitKey("verify", clientIp, testPhone), verifyRateLimitMaxAttempts, verifyRateLimitWindowMillis);
        enforceRateLimit(buildLimitKey("verify-test", clientIp, testPhone), testRateLimitMaxAttempts, verifyRateLimitWindowMillis);

        if (!allowTestOtpBypass) {
            log.warn("event=TEST_OTP_VERIFY_REJECTED userId={} ip={} phone={} reason=bypass_disabled", userId, clientIp, maskedPhone);
            throw new ApiException(HttpStatus.FORBIDDEN, "TEST_OTP_BYPASS_DISABLED", "테스트 OTP 경로가 비활성화되어 있습니다.");
        }

        if (!parseAllowlist().contains(testPhone)) {
            log.warn("event=TEST_OTP_VERIFY_REJECTED userId={} ip={} phone={} reason=phone_not_allowlisted", userId, clientIp, maskedPhone);
            throw new ApiException(HttpStatus.FORBIDDEN, "TEST_PHONE_NOT_ALLOWED", "허용되지 않은 테스트 전화번호입니다.");
        }

        if (testOtpCode == null || testOtpCode.isBlank()) {
            log.error("event=TEST_OTP_VERIFY_ERROR userId={} ip={} phone={} reason=missing_server_test_code", userId, clientIp, maskedPhone);
            throw new ApiException(HttpStatus.FORBIDDEN, "TEST_OTP_BYPASS_DISABLED", "테스트 OTP 경로가 비활성화되어 있습니다.");
        }

        if (!testOtpCode.equals(testCode)) {
            boolean codeAttemptsAllowed = phoneVerifyRateLimitService.isAllowed(
                    buildLimitKey("verify-test-invalid-code", clientIp, testPhone),
                    testInvalidCodeMaxAttempts,
                    verifyRateLimitWindowMillis
            );
            log.warn("event=TEST_OTP_VERIFY_FAILED userId={} ip={} phone={} reason=invalid_test_code", userId, clientIp, maskedPhone);
            if (!codeAttemptsAllowed) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TEST_CODE", "테스트 인증 코드가 올바르지 않습니다.");
        }

        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("유저를 찾을 수 없습니다."));

        currentUser.setPhoneNumber(testPhone);
        currentUser.setPhoneVerified(true);
        log.info("event=TEST_OTP_VERIFY_SUCCESS userId={} ip={} phone={}", userId, clientIp, maskedPhone);
    }

    @Transactional
    public void verifyPhone(Long userId, String firebaseIdToken, String clientIp) {
        enforceRateLimit(buildLimitKey("verify", clientIp, "id-token"), verifyRateLimitMaxAttempts, verifyRateLimitWindowMillis);

        // 1) Firebase idToken 검증 후 전화번호 추출
        FirebaseToken decodedToken;
        try {
            decodedToken = FirebaseAuth.getInstance().verifyIdToken(firebaseIdToken);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_ID_TOKEN", "유효하지 않은 Firebase 토큰입니다.");
        }

        String phoneNumber = decodedToken.getClaims()
                .getOrDefault("phone_number", "").toString();

        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE_NUMBER_NOT_FOUND", "Firebase 토큰에서 전화번호를 가져올 수 없습니다.");
        }

        enforceRateLimit(buildLimitKey("verify", clientIp, phoneNumber), verifyRateLimitMaxAttempts, verifyRateLimitWindowMillis);

        // 2) 현재 유저 조회
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("유저를 찾을 수 없습니다."));

        // 3) 동일 전화번호로 가입된 다른 유저 체크 (중복 감지)
        userRepository.findByPhoneNumber(phoneNumber).ifPresent(existingUser -> {
            if (!existingUser.getUserId().equals(userId)) {
                String provider = existingUser.getKakaoId() != null ? "KAKAO" : "APPLE";
                userService.invalidateUserForDuplicatePhoneSignup(userId);
                throw new DuplicatePhoneNumberException(provider);
            }
        });

        // 4) 전화번호 저장 + 인증 완료 처리
        currentUser.setPhoneNumber(phoneNumber);
        currentUser.setPhoneVerified(true);
    }

    private void enforceRateLimit(String key, int maxAttempts, long windowMillis) {
        boolean allowed = phoneVerifyRateLimitService.isAllowed(key, maxAttempts, windowMillis);
        if (!allowed) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private Set<String> parseAllowlist() {
        if (testOtpAllowlist == null || testOtpAllowlist.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(testOtpAllowlist.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
    }

    private String buildLimitKey(String route, String clientIp, String phoneOrScope) {
        return route + "|" + clientIp + "|" + phoneOrScope;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 2);
    }

    private String maskCode(String code) {
        if (code == null || code.isBlank()) {
            return "***";
        }
        return "***" + code.charAt(code.length() - 1);
    }
}

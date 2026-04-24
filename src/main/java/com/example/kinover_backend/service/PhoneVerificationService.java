package com.example.kinover_backend.service;

import com.example.kinover_backend.controller.DuplicatePhoneNumberException;
import com.example.kinover_backend.controller.NotFoundException;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    private final UserRepository userRepository;
    private final UserService userService;

    private static final java.util.Set<String> TEST_PHONE_NUMBERS = java.util.Set.of("01011112222", "01012345678");
    private static final String TEST_CODE = "123456";

    @Transactional
    public void verifyPhoneForTest(Long userId, String testPhone, String testCode) {
        if (!TEST_PHONE_NUMBERS.contains(testPhone) || !TEST_CODE.equals(testCode)) {
            throw new IllegalArgumentException("테스트 전화번호 또는 인증 코드가 올바르지 않습니다.");
        }

        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("유저를 찾을 수 없습니다."));

        userRepository.findByPhoneNumber(testPhone).ifPresent(existingUser -> {
            if (!existingUser.getUserId().equals(userId)) {
                String provider = existingUser.getKakaoId() != null ? "KAKAO" : "APPLE";
                userService.invalidateUserForDuplicatePhoneSignup(userId);
                throw new DuplicatePhoneNumberException(provider);
            }
        });

        currentUser.setPhoneNumber(testPhone);
        currentUser.setPhoneVerified(true);
    }

    @Transactional
    public void verifyPhone(Long userId, String firebaseIdToken) {
        // 1) Firebase idToken 검증 후 전화번호 추출
        FirebaseToken decodedToken;
        try {
            decodedToken = FirebaseAuth.getInstance().verifyIdToken(firebaseIdToken);
        } catch (Exception e) {
            throw new IllegalArgumentException("유효하지 않은 Firebase 토큰입니다.");
        }

        String phoneNumber = decodedToken.getClaims()
                .getOrDefault("phone_number", "").toString();

        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Firebase 토큰에서 전화번호를 가져올 수 없습니다.");
        }

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
}

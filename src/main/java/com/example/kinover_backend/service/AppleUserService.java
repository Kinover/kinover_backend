package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.LoginResponseDto;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.repository.UserRepository;
import com.example.kinover_backend.security.AppleTokenVerifier;
import com.example.kinover_backend.security.AppleUserClaims;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class AppleUserService {

    private final AppleTokenVerifier appleTokenVerifier;
    private final UserRepository userRepository;
    private final TokenService tokenService;

    public AppleUserService(
            AppleTokenVerifier appleTokenVerifier,
            UserRepository userRepository,
            TokenService tokenService) {
        this.appleTokenVerifier = appleTokenVerifier;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
    }

    @Transactional
    public LoginResponseDto processAppleLogin(String identityToken) {
        // 1) 애플 identityToken 검증 + 클레임 추출
        AppleUserClaims claims = appleTokenVerifier.verify(identityToken);

        // 2) sub = 애플 유저 고유 식별자(핵심)
        String appleSub = claims.getSub();
        String email = claims.getEmail(); // 첫 로그인에만 올 수도 있음(없을 수 있음)

        // 3) appleId로 유저 조회 (없으면 회원가입)
        User user = userRepository.findByAppleId(appleSub)
                .orElseGet(() -> {
                    User newUser = new User();

                    // ⚠️ 중요: 현재 엔티티는 @GeneratedValue가 없어서 직접 userId 세팅 필요
                    newUser.setUserId(generateUserId());

                    newUser.setAppleId(appleSub);
                    newUser.setEmail(email);

                    newUser.setCreatedAt(new Date());
                    newUser.setUpdatedAt(new Date());

                    // 기본값들(원하면 더 세팅)
                    if (newUser.getIsOnline() == null)
                        newUser.setIsOnline(false);

                    return userRepository.save(newUser);
                });

        // 4) 기존 유저인데 email 비어있고 이번에 들어오면 업데이트
        if ((user.getEmail() == null || user.getEmail().isBlank())
                && email != null && !email.isBlank()) {
            user.setEmail(email);
        }

        // 5) 우리 서비스 JWT 발급
        String jwt = tokenService.issueJwt(user);

        // 6) hasFamily 판단 (userFamilyList로)
        boolean hasFamily = user.getUserFamilyList() != null && !user.getUserFamilyList().isEmpty();

        // 7) DTO 반환 (지윤 DTO 구조에 딱 맞게)
        return new LoginResponseDto(jwt, hasFamily);
    }

    private Long generateUserId() {
        long id;
        do {
            // 13자리 시간 + 0~999 랜덤을 섞어서 충돌 확률 줄이기
            id = System.currentTimeMillis() * 1000L + (long) (Math.random() * 1000);
        } while (userRepository.existsByUserId(id));
        return id;
    }

}

package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.LoginResponseDto;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.repository.UserRepository;
import com.example.kinover_backend.security.AppleTokenVerifier;
import com.example.kinover_backend.security.AppleUserClaims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AppleUserService {

    private final AppleTokenVerifier appleTokenVerifier;
    private final UserRepository userRepository;
    private final TokenService tokenService;

    // ✅ UserService랑 동일한 기본 이미지 파일명
    private static final String DEFAULT_USER_IMAGE = "user.png";

    @Value("${cloudfront.domain}")
    private String cloudFrontDomain;

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

        AppleUserClaims claims = appleTokenVerifier.verify(identityToken);

        String appleSub = claims.getSub();
        String email = claims.getEmail();

        User user = userRepository.findByAppleId(appleSub)
                .orElseGet(() -> {

                    User newUser = new User();

                    newUser.setUserId(generateUserId());
                    newUser.setAppleId(appleSub);
                    newUser.setEmail(email);

                    Date now = new Date();
                    newUser.setCreatedAt(now);
                    newUser.setUpdatedAt(now);

                    if (newUser.getIsOnline() == null) {
                        newUser.setIsOnline(false);
                    }

                    // ✅ 기본 프로필 이미지: 탈퇴 유저 이미지와 동일하게 고정
                    newUser.setImage(buildCloudFrontUrl(DEFAULT_USER_IMAGE));

                    // ✅ 기본 닉네임 생성
                    if (isBlank(newUser.getName())) {
                        newUser.setName(generateDefaultName());
                    }

                    return userRepository.save(newUser);
                });

        // 기존 유저인데 이메일 없고 이번에 들어오면 세팅
        if (isBlank(user.getEmail()) && !isBlank(email)) {
            user.setEmail(email);
        }

        // 기존 유저인데 image가 비어있으면 보정 (탈퇴 기본 이미지로 고정)
        if (isBlank(user.getImage())) {
            user.setImage(buildCloudFrontUrl(DEFAULT_USER_IMAGE));
        }

        String jwt = tokenService.issueJwt(user);

        boolean hasFamily =
                user.getUserFamilyList() != null &&
                !user.getUserFamilyList().isEmpty();

        return new LoginResponseDto(jwt, hasFamily);
    }

    // ✅ cloudFrontDomain 끝에 / 있든 없든 안전하게 합치기
    private String buildCloudFrontUrl(String path) {
        if (cloudFrontDomain == null) return path;
        String base = cloudFrontDomain.endsWith("/") ? cloudFrontDomain : cloudFrontDomain + "/";
        String p = path.startsWith("/") ? path.substring(1) : path;
        return base + p;
    }

    private String generateDefaultName() {
        int suffix = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "키노버 유저" + suffix;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private Long generateUserId() {
        long id;
        do {
            id = System.currentTimeMillis() * 1000L
                    + (long) (Math.random() * 1000);
        } while (userRepository.existsByUserId(id));
        return id;
    }
}

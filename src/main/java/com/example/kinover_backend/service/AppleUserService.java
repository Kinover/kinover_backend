package com.example.kinover_backend.service;

import com.example.kinover_backend.controller.AccountBannedException;
import com.example.kinover_backend.controller.AccountInvalidatedException;
import com.example.kinover_backend.controller.DuplicateSocialProviderException;
import com.example.kinover_backend.dto.AppleLoginDTO;
import com.example.kinover_backend.dto.LoginResponseDto;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.enums.UserAccountStatus;
import com.example.kinover_backend.repository.UserRepository;
import com.example.kinover_backend.security.AppleTokenVerifier;
import com.example.kinover_backend.security.AppleUserClaims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

    /**
     * 애플 로그인/가입.
     * <ul>
     *   <li>항상 identity token의 {@code sub}(애플 고유 ID)로 기존 유저를 먼저 찾습니다.</li>
     *   <li>기존 유저: 재로그인(클라이언트 이름·이메일 null)도 그대로 세션 발급.</li>
     *   <li>신규 유저: 최초 인증 시 클라이언트가 넘긴 이메일/이름(또는 토큰의 이메일)을 DB에 저장합니다.</li>
     * </ul>
     */
    @Transactional
    public LoginResponseDto processAppleLogin(AppleLoginDTO dto) {
        if (dto == null || dto.getIdentityToken() == null || dto.getIdentityToken().isBlank()) {
            throw new IllegalArgumentException("identityToken is required.");
        }

        AppleUserClaims claims = appleTokenVerifier.verify(dto.getIdentityToken());

        String appleSub = claims.getSub();
        String tokenEmail = claims.getEmail();

        String resolvedEmail = firstNonBlank(dto.getEmail(), tokenEmail);
        String displayName = buildDisplayName(dto.getFamilyName(), dto.getGivenName());

        return userRepository.findByAppleId(appleSub)
                .map(user -> finalizeExistingAppleUser(user, resolvedEmail))
                .orElseGet(() -> registerNewAppleUser(dto, appleSub, resolvedEmail, displayName));
    }

    private LoginResponseDto finalizeExistingAppleUser(User user, String resolvedEmail) {
        if (isBlank(user.getEmail()) && !isBlank(resolvedEmail)) {
            user.setEmail(resolvedEmail.trim());
        }
        if (isBlank(user.getImage())) {
            user.setImage(buildCloudFrontUrl(DEFAULT_USER_IMAGE));
        }
        return buildLoginResponse(user);
    }

    private LoginResponseDto registerNewAppleUser(
            AppleLoginDTO dto,
            String appleSub,
            String resolvedEmail,
            String displayName) {

        if (resolvedEmail != null && !resolvedEmail.isBlank()) {
            userRepository.findByEmail(resolvedEmail.trim()).ifPresent(existing -> {
                if (existing.getKakaoId() != null) {
                    throw new DuplicateSocialProviderException("KAKAO");
                }
            });
        }

        User newUser = new User();
        newUser.setUserId(generateUserId());
        newUser.setAppleId(appleSub);
        newUser.setEmail(resolvedEmail != null ? resolvedEmail.trim() : null);

        Date now = new Date();
        newUser.setCreatedAt(now);
        newUser.setUpdatedAt(now);

        if (newUser.getIsOnline() == null) {
            newUser.setIsOnline(false);
        }

        newUser.setImage(buildCloudFrontUrl(DEFAULT_USER_IMAGE));

        if (!isBlank(displayName)) {
            newUser.setName(displayName.trim());
        } else {
            newUser.setName(generateDefaultName());
        }

        if (dto.getBirth() != null && !dto.getBirth().isBlank()) {
            newUser.setBirth(parseBirth(dto.getBirth().trim()));
        }

        User saved = userRepository.save(newUser);
        return buildLoginResponse(saved);
    }

    private LoginResponseDto buildLoginResponse(User user) {
        if (UserAccountStatus.BANNED.equals(user.getAccountStatus())) {
            throw new AccountBannedException();
        }
        if (UserAccountStatus.INVALIDATED.equals(user.getAccountStatus())) {
            throw new AccountInvalidatedException();
        }

        String jwt = tokenService.issueJwt(user);

        boolean hasFamily =
                user.getUserFamilyList() != null &&
                        !user.getUserFamilyList().isEmpty();

        boolean phoneVerified = Boolean.TRUE.equals(user.getPhoneVerified());

        return new LoginResponseDto(jwt, hasFamily, phoneVerified);
    }

    private Date parseBirth(String yyyyMmDd) {
        try {
            LocalDate d = LocalDate.parse(yyyyMmDd, DateTimeFormatter.ISO_LOCAL_DATE);
            return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid birth date. Expected yyyy-MM-dd");
        }
    }

    /**
     * 한국어 표기 관례에 맞춰 성·이름 순으로 합칩니다. 한쪽만 있으면 그 값만 사용합니다.
     */
    private String buildDisplayName(String familyName, String givenName) {
        String f = trimToEmpty(familyName);
        String g = trimToEmpty(givenName);
        if (f.isEmpty() && g.isEmpty()) {
            return null;
        }
        if (f.isEmpty()) {
            return g;
        }
        if (g.isEmpty()) {
            return f;
        }
        return f + " " + g;
    }

    private String trimToEmpty(String s) {
        if (s == null) {
            return "";
        }
        return s.trim();
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (!isBlank(preferred)) {
            return preferred.trim();
        }
        if (!isBlank(fallback)) {
            return fallback.trim();
        }
        return null;
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

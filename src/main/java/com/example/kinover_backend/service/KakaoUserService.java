package com.example.kinover_backend.service;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.KakaoUserDto;
import com.example.kinover_backend.dto.LoginResponseDto;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.repository.UserRepository;
import com.example.kinover_backend.repository.UserFamilyRepository;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class    KakaoUserService {

    private final UserRepository userRepository;
    private final UserFamilyRepository userFamilyRepository;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate;
    private final UserService userService;

    @Autowired
    private EntityManager entityManager;

    @Value("${kakao.api-url:https://kapi.kakao.com/v2/user/me}")
    private String kakaoApiUrl;

    private static final Logger logger = LoggerFactory.getLogger(KakaoUserService.class);

    @Transactional
    public LoginResponseDto processKakaoLogin(String accessToken) {
        try {
            KakaoUserDto kakaoUserDto = getKakaoUserInfo(accessToken);
            logger.info("Kakao User Info Retrieved: Kakao ID = {}", kakaoUserDto.getKakaoId());
            User user = findOrCreateUser(kakaoUserDto);
            boolean hasFamily = userFamilyRepository.existsByUser_UserId(user.getUserId());
            return new LoginResponseDto(jwtUtil.generateToken(user.getUserId()), hasFamily);
        } catch (ObjectOptimisticLockingFailureException ex) {
            logger.error("데이터 충돌 발생: {}", ex.getMessage());
            throw new RuntimeException("데이터 충돌이 발생했습니다. 다시 시도해주세요.", ex);
        } catch (Exception ex) {
            logger.error("알 수 없는 오류 발생: {}", ex.getMessage());
            throw new RuntimeException("카카오 로그인 처리 중 오류가 발생했습니다.", ex);
        }
    }

    private KakaoUserDto getKakaoUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        KakaoUserDto kakaoUserDto = restTemplate.exchange(
                kakaoApiUrl,
                HttpMethod.GET,
                entity,
                KakaoUserDto.class
        ).getBody();

        if (kakaoUserDto == null || kakaoUserDto.getKakaoId() == null) {
            throw new RuntimeException("카카오 사용자 정보를 가져오지 못했습니다.");
        }
        logger.info("Kakao API Response: {}", kakaoUserDto);
        return kakaoUserDto;
    }

    @Transactional
    protected User findOrCreateUser(KakaoUserDto kakaoUserDto) {
        return userRepository.findByUserId(kakaoUserDto.getKakaoId())
                .map(user -> userService.updateUserFromKakao(user, kakaoUserDto))
                .orElseGet(() -> userService.createNewUserFromKakao(kakaoUserDto));
    }
}
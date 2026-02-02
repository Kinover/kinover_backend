package com.example.kinover_backend.service;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.KakaoUserDto;
import com.example.kinover_backend.dto.LoginResponseDto;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.repository.UserRepository;
import com.example.kinover_backend.repository.UserFamilyRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class KakaoUserService {

    private final UserRepository userRepository;
    private final UserFamilyRepository userFamilyRepository; // (현재 메서드에서 미사용이면 나중에 정리 가능)
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate;
    private final UserService userService;

    @Value("${kakao.api-url:https://kapi.kakao.com/v2/user/me}")
    private String kakaoApiUrl;

    private static final Logger logger = LoggerFactory.getLogger(KakaoUserService.class);

    @Transactional
    public LoginResponseDto processKakaoLogin(String accessToken) {
        // 1) 카카오 사용자 정보 조회
        KakaoUserDto kakaoUserInfo = getKakaoUserInfo(accessToken);

        // 2) kakaoId 기준으로 유저 조회/생성/업데이트 (혼용 금지)
        User user = userRepository.findByKakaoId(kakaoUserInfo.getKakaoId())
                .map(existing -> userService.updateUserFromKakao(existing, kakaoUserInfo))
                .orElseGet(() -> userService.createNewUserFromKakao(kakaoUserInfo));

        // 3) JWT에는 "우리 서비스 PK(userId)"만 넣는다
        //    (가족 생성/참여 등 모든 API는 이 userId로 userRepository.findById(userId) 하면 됨)
        String token = jwtUtil.generateToken(user.getUserId());

        // 4) 가족 여부 확인
        boolean hasFamily = user.getUserFamilyList() != null && !user.getUserFamilyList().isEmpty();

        // 디버그: 값 혼용 잡기
        logger.info("LOGIN_OK userId(PK)={}, kakaoId={}", user.getUserId(), kakaoUserInfo.getKakaoId());

        return new LoginResponseDto(token, hasFamily);
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

        logger.info("Kakao API Response kakaoId={}", kakaoUserDto.getKakaoId());
        return kakaoUserDto;
    }
}

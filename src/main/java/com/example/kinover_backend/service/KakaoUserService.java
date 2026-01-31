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
        // 1. 액세스 토큰으로 카카오 서버에 사용자 정보 요청
        // (기존에 구현되어 있던 getUserInfo 메서드 활용)
        KakaoUserDto kakaoUserInfo = getKakaoUserInfo(accessToken); 

        // 2. [핵심 변경] 가져온 정보를 UserService로 넘김
        // UserService 내부에서 'kakaoId'로 조회하고, 없으면 '랜덤 userId'로 가입시킴
        User user = userService.createNewUserFromKakao(kakaoUserInfo);

        // 3. [중요] 토큰 발급 시, 카카오 ID가 아니라 'DB의 랜덤 userId'를 넣어야 함
        // user.getUserId()는 이제 5827104921 같은 랜덤 숫자임
        String token = jwtUtil.generateToken(user.getUserId());

        // 4. 가족 여부 확인 (기존 로직)
        boolean hasFamily = !user.getUserFamilyList().isEmpty();

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
package com.example.kinover_backend.service;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.KakaoUserDto;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class KakaoUserService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate;

    @Autowired
    private EntityManager entityManager;

    @Value("${kakao.api-url:https://kapi.kakao.com/v2/user/me}")
    private String kakaoApiUrl;

    private static final Logger logger = LoggerFactory.getLogger(KakaoUserService.class);

    @Transactional
    public String processKakaoLogin(String accessToken) {
        try {
            KakaoUserDto kakaoUserDto = getKakaoUserInfo(accessToken);
            logger.info("Kakao User Info Retrieved: Kakao ID = {}", kakaoUserDto.getKakaoId());

            User user = findOrCreateUser(kakaoUserDto);
            return jwtUtil.generateToken(user.getUserId()); // Long 타입 직접 전달
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
        return kakaoUserDto;
    }

    @Transactional
    protected User findOrCreateUser(KakaoUserDto kakaoUserDto) {
        return userRepository.findByUserId(kakaoUserDto.getKakaoId())
                .map(user -> updateUser(user, kakaoUserDto))
                .orElseGet(() -> createNewUser(kakaoUserDto));
    }

    protected User createNewUser(KakaoUserDto kakaoUserDto) {
        try {
            User user = entityManager.find(User.class, kakaoUserDto.getKakaoId(), LockModeType.PESSIMISTIC_WRITE);
            if (user == null) {
                user = new User();
                user.setUserId(kakaoUserDto.getKakaoId());
            }
            user.setEmail(kakaoUserDto.getEmail());
            user.setName(kakaoUserDto.getNickname());
            user.setImage(kakaoUserDto.getProfileImageUrl());
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            logger.error("데이터베이스 제약조건 위반: {}", e.getMessage());
            throw new RuntimeException("유저를 저장하는 데 오류가 발생했습니다.");
        } catch (Exception e) {
            logger.error("유저 생성 중 예외 발생: {}", e.getMessage());
            throw new RuntimeException("유저 생성 중 오류가 발생했습니다.");
        }
    }

    protected User updateUser(User user, KakaoUserDto kakaoUserDto) {
        user.setName(kakaoUserDto.getNickname());
        user.setEmail(kakaoUserDto.getEmail());
        user.setImage(kakaoUserDto.getProfileImageUrl());
        return userRepository.saveAndFlush(user);
    }
}
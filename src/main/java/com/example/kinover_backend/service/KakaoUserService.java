package com.example.kinover_backend.service;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.KakaoUserDto;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class KakaoUserService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Autowired
    private EntityManager entityManager;

    private static final Logger logger = LoggerFactory.getLogger(KakaoUserService.class);

    @Transactional
    public String processKakaoUser(KakaoUserDto kakaoUserDto) {
        try {
            logger.info("Kakao User Processing: Kakao ID = {}", kakaoUserDto.getKakaoId());
            User user = findOrCreateUser(kakaoUserDto);
            return jwtUtil.generateToken(user.getUserId());
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
            logger.error("데이터 충돌이 발생했습니다: {}", ex.getMessage());
            throw new RuntimeException("데이터 충돌이 발생했습니다. 다시 시도해주세요.", ex);
        } catch (Exception ex) {
            logger.error("알 수 없는 오류가 발생했습니다: {}", ex.getMessage());
            throw new RuntimeException("알 수 없는 오류가 발생했습니다. 다시 시도해주세요.", ex);
        }
    }

    @Transactional
    protected User findOrCreateUser(KakaoUserDto kakaoUserDto) {
        return userRepository.findByUserId(kakaoUserDto.getKakaoId())
                .map(user -> updateUser(user, kakaoUserDto))  // 유저가 존재하면 바로 업데이트
                .orElseGet(() -> createNewUser(kakaoUserDto)); // 없으면 새로 생성
    }

    protected User createNewUser(KakaoUserDto kakaoUserDto) {
        try {
            // PESSIMISTIC_WRITE 락을 걸고, 유저 데이터를 수정
            User user = entityManager.find(User.class, kakaoUserDto.getKakaoId(), LockModeType.PESSIMISTIC_WRITE);

            if (user == null) {
                // 유저가 없을 경우 새로 생성
                user = new User();
                user.setUserId(kakaoUserDto.getKakaoId());
            }

            user.setEmail(kakaoUserDto.getEmail());
            user.setName(kakaoUserDto.getNickname());
            user.setImage(kakaoUserDto.getProfileImageUrl());

            return userRepository.saveAndFlush(user);  // saveAndFlush 대신 save() 사용
        } catch (DataIntegrityViolationException e) {
            logger.error("데이터베이스 제약조건 위반: {}", e.getMessage());
            throw new RuntimeException("유저를 저장하는 데 오류가 발생했습니다. 다시 시도해주세요.");
        } catch (Exception e) {
            logger.error("유저 생성 중 예외 발생: {}", e.getMessage());
            throw new RuntimeException("유저 생성 중 오류가 발생했습니다.");
        }
    }

    protected User updateUser(User user, KakaoUserDto kakaoUserDto) {
        user.setName(kakaoUserDto.getNickname());
        user.setEmail(kakaoUserDto.getEmail());
        user.setImage(kakaoUserDto.getProfileImageUrl());

        return userRepository.saveAndFlush(user);  // save() 사용
    }
}

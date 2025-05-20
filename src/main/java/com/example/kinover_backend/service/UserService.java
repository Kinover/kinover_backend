package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.KakaoUserDto;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.dto.UserStatusDTO;
import com.example.kinover_backend.entity.Family;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.entity.UserFamily;
import com.example.kinover_backend.repository.UserRepository;
import com.example.kinover_backend.repository.UserFamilyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;
import org.webjars.NotFoundException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserFamilyRepository userFamilyRepository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    @Autowired
    private EntityManager entityManager;

    private static final Logger logger = LoggerFactory.getLogger(KakaoUserService.class);

    @Value("${cloudfront.domain}")
    private String cloudFrontDomain;


    // 유저 아이디 통해서 유저 조회 (DTO 반환)
    @Transactional
    public UserDTO getUserById(Long userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
        return new UserDTO(user);
    }

    public User createNewUserFromKakao(KakaoUserDto kakaoUserDto) {
        try {
            // 1. PESSIMISTIC_WRITE 잠금으로 유저 조회 (중복 방지)
            User user = entityManager.find(User.class, kakaoUserDto.getKakaoId(), LockModeType.PESSIMISTIC_WRITE);

            if (user == null) {
                user = new User();
                user.setUserId(kakaoUserDto.getKakaoId());
            }

            // 2. 필수 정보 업데이트
            user.setEmail(kakaoUserDto.getEmail());
            user.setName(kakaoUserDto.getNickname());
            user.setPhoneNumber(kakaoUserDto.getPhoneNumber());

            // 3. 프로필 이미지: http → https (iOS 호환)
            String profileImageUrl = kakaoUserDto.getProfileImageUrl();
            if (profileImageUrl != null && profileImageUrl.startsWith("http://")) {
                profileImageUrl = "https://" + profileImageUrl.substring(7);
            }
            user.setImage(profileImageUrl);

            // 4. 생일 정보 가공 (예: birthyear=1990, birthday=0101)
            if (kakaoUserDto.getBirthyear() != null && kakaoUserDto.getBirthday() != null) {
                try {
                    String yyyyMMdd = kakaoUserDto.getBirthyear() + kakaoUserDto.getBirthday(); // "19900101"
                    LocalDate birthDate = LocalDate.parse(yyyyMMdd, DateTimeFormatter.ofPattern("yyyyMMdd"));
                    user.setBirth(Date.from(birthDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
                } catch (DateTimeParseException ex) {
                    logger.warn("생일 정보 파싱 실패: {}", ex.getMessage());
                }
            }

            logger.info("Creating/updating user: id={}, name={}, email={}, phone={}, birth={}",
                    user.getUserId(), user.getName(), user.getEmail(), user.getPhoneNumber(), user.getBirth());

            return userRepository.saveAndFlush(user);

        } catch (DataIntegrityViolationException e) {
            logger.error("데이터베이스 제약조건 위반", e);
            throw new RuntimeException("유저를 저장하는 데 오류가 발생했습니다.");
        } catch (Exception e) {
            logger.error("유저 생성 중 예외 발생", e);
            throw new RuntimeException("유저 생성 중 오류가 발생했습니다.");
        }
    }

    public User updateUserFromKakao(User user, KakaoUserDto kakaoUserDto) {
        user.setName(kakaoUserDto.getNickname());
        user.setEmail(kakaoUserDto.getEmail());
        // 이미지 URL을 https로 변환 (ios에서 http:// 이미지 안열리는 이슈)
        String profileImageUrl = kakaoUserDto.getProfileImageUrl();
        if (profileImageUrl != null && profileImageUrl.startsWith("http://")) {
            profileImageUrl = "https://" + profileImageUrl.substring(7);
        }
        user.setImage(profileImageUrl);
        user.setPhoneNumber(kakaoUserDto.getPhoneNumber());
        if (kakaoUserDto.getBirthyear() != null && kakaoUserDto.getBirthday() != null) {
            String birthDateStr = kakaoUserDto.getBirthyear() + "-" + kakaoUserDto.getBirthday().substring(0, 2) + "-" + kakaoUserDto.getBirthday().substring(2);
            LocalDate birthDate = LocalDate.parse(birthDateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            user.setBirth(Date.from(birthDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        }
        return userRepository.saveAndFlush(user);
    }


    @Transactional
    public void deleteUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 유저입니다."));

        // 1. 유저 정보 익명화
        user.setName("탈퇴했음");
        user.setImage(cloudFrontDomain + "kinover_deleted_user.png");
        user.setBirth(null);
        user.setEmail(null);
        user.setPwd(null);
        user.setPhoneNumber(null);

        // 2. 유저-가족 관계 제거
        List<UserFamily> toRemove = userFamilyRepository.findAllByUser_UserId(userId);
        userFamilyRepository.deleteAll(toRemove);  // FK만 끊기면 됨

        // 3. 유저는 삭제하지 않고 저장 (익명화만 적용)
        userRepository.save(user);
    }


    // 유저 프로필 수정
    public UserDTO modifyUser(UserDTO userDTO) {
        if (userDTO.getUserId() == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        User user = userRepository.findById(userDTO.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 수정 가능한 필드들만 null 체크 후 반영
        if (userDTO.getName() != null) {
            user.setName(userDTO.getName());
        }

        if (userDTO.getBirth() != null) {
            user.setBirth(userDTO.getBirth());
        }

        if (userDTO.getImage() != null) {
            user.setImage(cloudFrontDomain + userDTO.getImage());
        }

        User saved = userRepository.save(user);
        return new UserDTO(saved);
    }

    public void updateUserOnlineStatus(Long userId, boolean isOnline) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + userId));

        user.setIsOnline(isOnline);
        user.setLastActiveAt(LocalDateTime.now());
        userRepository.save(user);

        // 유저가 속한 모든 가족 찾기
        List<Family> families = userFamilyRepository.findFamiliesByUserId(userId);

        for (Family family : families) {
            List<UserStatusDTO> statusList = getFamilyStatus(family.getFamilyId());

            try {
                String json = objectMapper.writeValueAsString(statusList);
                String redisTopic = "family:status:" + family.getFamilyId();
                redisTemplate.convertAndSend(redisTopic, json);
            } catch (Exception e) {
                throw new RuntimeException("접속 상태 broadcast 실패", e);
            }
        }
    }


    public List<UserStatusDTO> getFamilyStatus(UUID familyId) {
        List<User> familyMembers = userFamilyRepository.findUsersByFamilyId(familyId);

        return familyMembers.stream()
                .map(member -> new UserStatusDTO(
                        member.getUserId(),
                        Boolean.TRUE.equals(member.getIsOnline()),
                        member.getLastActiveAt()
                ))
                .collect(Collectors.toList());
    }




}

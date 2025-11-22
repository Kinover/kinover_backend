package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.*;
import com.example.kinover_backend.entity.*;
import com.example.kinover_backend.enums.NotificationType;
import com.example.kinover_backend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;
import org.webjars.NotFoundException;
import com.example.kinover_backend.websocket.WebSocketStatusHandler;

import java.text.ParseException;
import java.text.SimpleDateFormat;
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
    private final NotificationRepository notificationRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectProvider<WebSocketStatusHandler> statusHandlerProvider;

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

        UserDTO dto = new UserDTO(user);

        List<UserFamily> list = userFamilyRepository.findAllByUser_UserId(user.getUserId());
        if (!list.isEmpty()) {
            dto.setFamilyId(list.get(0).getFamily().getFamilyId());  // 보통 1인 1가족 구조
        }

        return dto;
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
        user.setTrait(null);

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

        if (userDTO.getTrait() != null) {
            user.setTrait(userDTO.getTrait());
        }

        if (userDTO.getEmotion() != null) {
        if (user.getEmotion() == null || !user.getEmotion().equals(userDTO.getEmotion())) {
            user.setEmotion(userDTO.getEmotion());
            user.setEmotionUpdatedAt(LocalDateTime.now());
        }
    }

        User saved = userRepository.save(user);

        UserDTO dto = new UserDTO(saved);

        List<UserFamily> list = userFamilyRepository.findAllByUser_UserId(user.getUserId());
        if (!list.isEmpty()) {
            dto.setFamilyId(list.get(0).getFamily().getFamilyId());
        }

        return dto;
    }

    public void updateUserOnlineStatus(Long userId, boolean isOnline, boolean isMyself) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + userId));

        if (isMyself) {
            user.setLastActiveAt(LocalDateTime.now());
            userRepository.save(user);
        }

        
        // 유저가 속한 모든 가족 찾기
        if(isMyself){
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
    }


    public List<UserStatusDTO> getFamilyStatus(UUID familyId) {
    System.out.println("\n[FamilyStatus] === getFamilyStatus() called ===");
    System.out.println("[FamilyStatus] familyId = " + familyId);

    List<User> familyMembers = userFamilyRepository.findUsersByFamilyId(familyId);
    System.out.println("[FamilyStatus] found " + familyMembers.size() + " members in DB");

    WebSocketStatusHandler handler = statusHandlerProvider.getObject();

    // 각 멤버별 상태 출력
    List<UserStatusDTO> result = familyMembers.stream()
        .map(member -> {
            Long memberId = member.getUserId();
            boolean online = !handler.getSessionsByUserId(memberId).isEmpty();
            System.out.println("[FamilyStatus] memberId=" + memberId 
                    + " | online=" + online 
                    + " | lastActiveAt=" + member.getLastActiveAt());
            return new UserStatusDTO(memberId, online, member.getLastActiveAt());
        })
        .collect(Collectors.toList());

    // 전체 결과 출력
    System.out.println("[FamilyStatus] returning " + result.size() + " DTOs:");
    for (UserStatusDTO dto : result) {
        System.out.println("  → userId=" + dto.getUserId() 
                + ", online=" + dto.isOnline() 
                + ", lastActiveAt=" + dto.getLastActiveAt());
    }
    System.out.println("[FamilyStatus] === getFamilyStatus() end ===\n");

    return result;
}

    @Transactional
    public NotificationResponseDTO getUserNotifications(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));

        LocalDateTime lastCheckedAt = user.getLastNotificationCheckedAt();

        List<UUID> familyIds = userFamilyRepository.findFamiliesByUserId(userId).stream()
                .map(Family::getFamilyId)
                .collect(Collectors.toList());

        if (familyIds.isEmpty()) {
            return NotificationResponseDTO.builder()
                    .lastCheckedAt(lastCheckedAt)
                    .notifications(Collections.emptyList())
                    .build();
        }

        List<Notification> notifications = notificationRepository.findByFamilyIdInOrderByCreatedAtDesc(familyIds);

        List<NotificationDTO> dtoList = notifications.stream()
                .filter(n -> !n.getAuthorId().equals(userId)) // 자기 알림 제외
                .map(n -> {
                    User author = userRepository.findById(n.getAuthorId())
                            .orElseThrow(() -> new RuntimeException("작성자 정보 없음"));

                    String categoryTitle = "";
                    String contentPreview = "";
                    String firstImageUrl = "";

                    if (n.getNotificationType() == NotificationType.POST) {
                        Post post = postRepository.findById(n.getPostId())
                                .orElseThrow(() -> new RuntimeException("게시물 없음"));
                        categoryTitle = post.getCategory().getTitle();
                        contentPreview = post.getContent() != null && post.getContent().length() > 30
                                ? post.getContent().substring(0, 30) + "..."
                                : post.getContent();
                        firstImageUrl = post.getImages().isEmpty() ? null : post.getImages().get(0).getImageUrl();
                    } else if (n.getNotificationType() == NotificationType.COMMENT) {
                        Comment comment = commentRepository.findById(n.getCommentId())
                                .orElseThrow(() -> new RuntimeException("댓글 없음"));
                        categoryTitle = comment.getPost().getCategory().getTitle();
                        contentPreview = comment.getContent() != null && comment.getContent().length() > 30
                                ? comment.getContent().substring(0, 30) + "..."
                                : comment.getContent();
                        Post post = comment.getPost();
                        firstImageUrl = post.getImages().isEmpty() ? null : post.getImages().get(0).getImageUrl();
                    }

                    return NotificationDTO.builder()
                            .notificationType(n.getNotificationType())
                            .postId(n.getPostId())
                            .commentId(n.getCommentId())
                            .createdAt(n.getCreatedAt())
                            .authorName(author.getName())
                            .authorImage(author.getImage())
                            .categoryTitle(categoryTitle)
                            .contentPreview(contentPreview)
                            .firstImageUrl(firstImageUrl)
                            .build();
                })
                .collect(Collectors.toList());

        user.setLastNotificationCheckedAt(LocalDateTime.now());
        userRepository.save(user);

        return NotificationResponseDTO.builder()
                .lastCheckedAt(lastCheckedAt)
                .notifications(dtoList)
                .build();
    }

    @Transactional
    public boolean updatePostNotificationSetting(Long userId, boolean isOn) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isEmpty()) return false;

        User user = optionalUser.get();
        user.setIsPostNotificationOn(isOn);
        userRepository.save(user);
        return true;
    }

    @Transactional
    public boolean updateCommentNotificationSetting(Long userId, boolean isOn) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isEmpty()) return false;

        User user = optionalUser.get();
        user.setIsCommentNotificationOn(isOn);
        userRepository.save(user);
        return true;
    }

    @Transactional
    public boolean updateChatNotificationSetting(Long userId, boolean isOn) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) return false;

        User user = userOpt.get();
        user.setIsChatNotificationOn(isOn);
        userRepository.save(user);
        return true;
    }

    private Date parseDate(String dateStr) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(dateStr);
        } catch (Exception e) {
            throw new RuntimeException("Invalid date: " + dateStr);
        }
    }

    private Date parseDateTime(String birth) {
        if (birth == null || birth.isEmpty()) {
            return null;
        }

        // "YYYY-MM-DD" 기준 파싱
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        try {
            LocalDate localDate = LocalDate.parse(birth, formatter);
            // LocalDate → Date 변환
            return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Expected yyyy-MM-dd");
        }
    }



    public UserDTO updateUserProfile(Long userId, UpdateProfileRequest req) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 이름
        if (req.getName() != null && !req.getName().isBlank()) {
            user.setName(req.getName());
        }

        // 생년월일
        if (req.getBirth() != null && !req.getBirth().isBlank()) {
            user.setBirth(parseDate(req.getBirth()));
        }

        // 필수 약관 동의
        if (req.getTermsAgreed() != null) {
            user.setTermsAgreed(req.getTermsAgreed());
        }

        if (req.getPrivacyAgreed() != null) {
            user.setPrivacyAgreed(req.getPrivacyAgreed());
        }

        if (req.getMarketingAgreed() != null) {
            user.setMarketingAgreed(req.getMarketingAgreed());
        }

        if (req.getTermsVersion() != null) {
            user.setTermsVersion(req.getTermsVersion());
        }

        if (req.getPrivacyVersion() != null) {
            user.setPrivacyVersion(req.getPrivacyVersion());
        }

        if (req.getAgreedAt() != null) {
            user.setAgreedAt(parseDateTime(req.getAgreedAt()));
        }

        if (req.getMarketingAgreedAt() != null) {
            user.setMarketingAgreedAt(parseDateTime(req.getMarketingAgreedAt()));
        }

        userRepository.save(user);

        return new UserDTO(user);
    }





}

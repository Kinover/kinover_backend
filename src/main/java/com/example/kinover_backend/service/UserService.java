package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.*;
import com.example.kinover_backend.entity.*;
import com.example.kinover_backend.enums.NotificationType;
import com.example.kinover_backend.repository.*;
import com.example.kinover_backend.websocket.WebSocketStatusHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.webjars.NotFoundException;

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
    private final UserChatRoomRepository userChatRoomRepository;

    @Autowired
    private EntityManager entityManager;

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

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
            dto.setFamilyId(list.get(0).getFamily().getFamilyId());
        }

        return dto;
    }

    public User createNewUserFromKakao(KakaoUserDto kakaoUserDto) {
        try {
            User user = entityManager.find(User.class, kakaoUserDto.getKakaoId(), LockModeType.PESSIMISTIC_WRITE);

            if (user == null) {
                user = new User();
                user.setUserId(kakaoUserDto.getKakaoId());
            }

            user.setEmail(kakaoUserDto.getEmail());
            user.setName(kakaoUserDto.getNickname());
            user.setPhoneNumber(kakaoUserDto.getPhoneNumber());

            String profileImageUrl = kakaoUserDto.getProfileImageUrl();
            if (profileImageUrl != null && profileImageUrl.startsWith("http://")) {
                profileImageUrl = "https://" + profileImageUrl.substring(7);
            }
            user.setImage(profileImageUrl);

            if (kakaoUserDto.getBirthyear() != null && kakaoUserDto.getBirthday() != null) {
                try {
                    String yyyyMMdd = kakaoUserDto.getBirthyear() + kakaoUserDto.getBirthday();
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

        String profileImageUrl = kakaoUserDto.getProfileImageUrl();
        if (profileImageUrl != null && profileImageUrl.startsWith("http://")) {
            profileImageUrl = "https://" + profileImageUrl.substring(7);
        }
        user.setImage(profileImageUrl);

        user.setPhoneNumber(kakaoUserDto.getPhoneNumber());

        if (kakaoUserDto.getBirthyear() != null && kakaoUserDto.getBirthday() != null) {
            String birthDateStr = kakaoUserDto.getBirthyear() + "-" +
                    kakaoUserDto.getBirthday().substring(0, 2) + "-" +
                    kakaoUserDto.getBirthday().substring(2);
            LocalDate birthDate = LocalDate.parse(birthDateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            user.setBirth(Date.from(birthDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        }

        return userRepository.saveAndFlush(user);
    }

    @Transactional
    public void deleteUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 유저입니다."));

        Long chatbotId = 9999999999L;
        userChatRoomRepository.deleteCommonChatRoomWithBot(userId, chatbotId);

        user.setName("탈퇴했음");
        user.setImage(cloudFrontDomain + "kinover_deleted_user.png");
        user.setBirth(null);
        user.setEmail(null);
        user.setPwd(null);
        user.setPhoneNumber(null);
        user.setTrait(null);

        List<UserFamily> toRemove = userFamilyRepository.findAllByUser_UserId(userId);
        userFamilyRepository.deleteAll(toRemove);

        userRepository.save(user);
    }

    public UserDTO modifyUser(UserDTO userDTO) {
        if (userDTO.getUserId() == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        User user = userRepository.findById(userDTO.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (userDTO.getName() != null) user.setName(userDTO.getName());
        if (userDTO.getBirth() != null) user.setBirth(userDTO.getBirth());

        if (userDTO.getImage() != null) {
            String imagePath = userDTO.getImage();
            user.setImage(imagePath.startsWith("http") ? imagePath : cloudFrontDomain + imagePath);
        }

        if (userDTO.getTrait() != null) user.setTrait(userDTO.getTrait());

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

        if (isMyself) {
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
        List<User> familyMembers = userFamilyRepository.findUsersByFamilyId(familyId);
        WebSocketStatusHandler handler = statusHandlerProvider.getObject();

        return familyMembers.stream()
                .map(member -> {
                    Long memberId = member.getUserId();
                    boolean online = !handler.getSessionsByUserId(memberId).isEmpty();
                    return new UserStatusDTO(memberId, online, member.getLastActiveAt());
                })
                .collect(Collectors.toList());
    }

    /**
     * ✅ 알림 조회 (알림 화면 진입 = 즉시 읽음 처리)
     * - 호출 시점(now)으로 lastNotificationCheckedAt 갱신해서 "읽음 확정"
     */
    @Transactional
    public NotificationResponseDTO getUserNotifications(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));

        LocalDateTime now = LocalDateTime.now();

        // ✅ 알림 화면 들어오면 즉시 읽음 처리 확정
        user.setLastNotificationCheckedAt(now);
        userRepository.save(user);

        List<UUID> familyIds = userFamilyRepository.findFamiliesByUserId(userId).stream()
                .map(Family::getFamilyId)
                .collect(Collectors.toList());

        if (familyIds.isEmpty()) {
            return NotificationResponseDTO.builder()
                    .lastCheckedAt(now)
                    .notifications(Collections.emptyList())
                    .build();
        }

        List<Notification> notifications =
                notificationRepository.findByFamilyIdInOrderByCreatedAtDesc(familyIds);

        List<NotificationDTO> dtoList = notifications.stream()
                .filter(n -> !Objects.equals(n.getAuthorId(), userId)) // 자기 알림 제외
                .map(n -> {
                    User author = userRepository.findById(n.getAuthorId())
                            .orElseThrow(() -> new RuntimeException("작성자 정보 없음"));

                    String categoryTitle = "";
                    String contentPreview = "";
                    String firstImageUrl = null;

                    if (n.getNotificationType() == NotificationType.POST) {
                        Post post = postRepository.findById(n.getPostId())
                                .orElseThrow(() -> new RuntimeException("게시물 없음"));
                        categoryTitle = post.getCategory().getTitle();
                        contentPreview = post.getContent() != null && post.getContent().length() > 30
                                ? post.getContent().substring(0, 30) + "..."
                                : post.getContent();
                        firstImageUrl = post.getImages().isEmpty()
                                ? null
                                : post.getImages().get(0).getImageUrl();

                    } else if (n.getNotificationType() == NotificationType.COMMENT) {
                        Comment comment = commentRepository.findById(n.getCommentId())
                                .orElseThrow(() -> new RuntimeException("댓글 없음"));
                        Post post = comment.getPost();
                        categoryTitle = post.getCategory().getTitle();
                        contentPreview = comment.getContent() != null && comment.getContent().length() > 30
                                ? comment.getContent().substring(0, 30) + "..."
                                : comment.getContent();
                        firstImageUrl = post.getImages().isEmpty()
                                ? null
                                : post.getImages().get(0).getImageUrl();
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

        return NotificationResponseDTO.builder()
                .lastCheckedAt(now)
                .notifications(dtoList)
                .build();
    }

    /**
     * ✅ 벨 아이콘 빨간점용: 안 읽은 알림 존재 여부
     * - lastNotificationCheckedAt 이후 생성된 알림이 있으면 true
     */
    @Transactional(readOnly = true)
    public boolean hasUnreadNotifications(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));

        LocalDateTime lastCheckedAt = user.getLastNotificationCheckedAt();

        List<UUID> familyIds = userFamilyRepository.findFamiliesByUserId(userId).stream()
                .map(Family::getFamilyId)
                .collect(Collectors.toList());

        if (familyIds.isEmpty()) return false;

        LocalDateTime 기준 = (lastCheckedAt != null) ? lastCheckedAt : LocalDateTime.MIN;

        return notificationRepository.existsByFamilyIdInAndCreatedAtAfter(familyIds, 기준);
    }

    /**
     * ✅ 뱃지 숫자(unreadCount)용: 안 읽은 알림 개수
     * - lastNotificationCheckedAt 이후 생성된 알림 "개수"
     * - 본인 author 알림 제외
     */
    @Transactional(readOnly = true)
    public long getUnreadNotificationCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));

        LocalDateTime lastCheckedAt = user.getLastNotificationCheckedAt();

        List<UUID> familyIds = userFamilyRepository.findFamiliesByUserId(userId).stream()
                .map(Family::getFamilyId)
                .collect(Collectors.toList());

        if (familyIds.isEmpty()) return 0L;

        LocalDateTime 기준 = (lastCheckedAt != null) ? lastCheckedAt : LocalDateTime.MIN;

        return notificationRepository.countByFamilyIdInAndCreatedAtAfterAndAuthorIdNot(
                familyIds, 기준, userId
        );
    }

    /**
     * ✅ 알림 화면 안 가도 읽음 확정시키는 API용
     * - 알림 클릭해서 바로 post/chat로 이동할 때도 서버 기준 읽음 정리 가능
     */
    @Transactional
    public LocalDateTime markNotificationsRead(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));

        LocalDateTime now = LocalDateTime.now();
        user.setLastNotificationCheckedAt(now);
        userRepository.save(user);

        return now;
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
        if (birth == null || birth.isEmpty()) return null;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        try {
            LocalDate localDate = LocalDate.parse(birth, formatter);
            return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Expected yyyy-MM-dd");
        }
    }

    public UserDTO updateUserProfile(Long userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (req.getName() != null && !req.getName().isBlank()) user.setName(req.getName());
        if (req.getBirth() != null && !req.getBirth().isBlank()) user.setBirth(parseDate(req.getBirth()));

        if (req.getTermsAgreed() != null) user.setTermsAgreed(req.getTermsAgreed());
        if (req.getPrivacyAgreed() != null) user.setPrivacyAgreed(req.getPrivacyAgreed());
        if (req.getMarketingAgreed() != null) user.setMarketingAgreed(req.getMarketingAgreed());
        if (req.getTermsVersion() != null) user.setTermsVersion(req.getTermsVersion());
        if (req.getPrivacyVersion() != null) user.setPrivacyVersion(req.getPrivacyVersion());
        if (req.getAgreedAt() != null) user.setAgreedAt(parseDateTime(req.getAgreedAt()));
        if (req.getMarketingAgreedAt() != null) user.setMarketingAgreedAt(parseDateTime(req.getMarketingAgreedAt()));

        userRepository.save(user);
        return new UserDTO(user);
    }
}

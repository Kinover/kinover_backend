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
import com.example.kinover_backend.controller.NotFoundException;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;

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

    // ✅ 채팅 unread 계산용
    private final MessageRepository messageRepository;

    @Autowired
    private EntityManager entityManager;

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Value("${cloudfront.domain}")
    private String cloudFrontDomain;

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

    @Transactional
    public User createNewUserFromKakao(KakaoUserDto kakaoUserDto) {
        try {
            Long kakaoId = kakaoUserDto.getKakaoId();

            // 1. 기존에는 PK로 바로 찾았지만, 이제는 kakaoId 컬럼으로 찾습니다.
            // (LockModeType은 필요하다면 repository 레벨에서 @Lock으로 처리하거나, 여기서는 일단 제외하고 조회)
            User user = userRepository.findByKakaoId(kakaoId).orElse(null);

            if (user == null) {
                // 2. 신규 가입: 랜덤 PK 생성 로직 수행
                user = new User();
                user.setUserId(generateRandomUserId()); // 하단에 메서드 구현
                user.setKakaoId(kakaoId); // 카카오 ID 저장
                logger.info("신규 유저 생성: userId={}, kakaoId={}", user.getUserId(), kakaoId);
            }

            // 3. 정보 업데이트 (기존과 동일)
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

            return userRepository.saveAndFlush(user);

        } catch (DataIntegrityViolationException e) {
            logger.error("데이터베이스 제약조건 위반", e);
            throw new RuntimeException("유저를 저장하는 데 오류가 발생했습니다.");
        } catch (Exception e) {
            logger.error("유저 생성 중 예외 발생", e);
            throw new RuntimeException("유저 생성 중 오류가 발생했습니다.");
        }
    }

    private Long generateRandomUserId() {
        Long randomId;
        // 중복되지 않는 ID가 나올 때까지 반복
        do {
            // 10억 ~ 90억 사이의 랜덤 숫자 (범위는 자유롭게 설정 가능)
            // Long.MAX_VALUE까지 쓰면 너무 기니까 적당한 길이로 설정
            randomId = ThreadLocalRandom.current().nextLong(1000000000L, 9999999999L);
        } while (userRepository.existsByUserId(randomId));
        
        return randomId;
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
        user.setEmail(null); 
        user.setKakaoId(null);

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

        if (userDTO.getName() != null)
            user.setName(userDTO.getName());
        if (userDTO.getBirth() != null)
            user.setBirth(userDTO.getBirth());

        if (userDTO.getImage() != null) {
            String imagePath = userDTO.getImage();
            user.setImage(imagePath.startsWith("http") ? imagePath : cloudFrontDomain + imagePath);
        }

        if (userDTO.getTrait() != null)
            user.setTrait(userDTO.getTrait());

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

    /*
     * =========================================================
     * ✅ 알림 조회 (수정본)
     * - 조회에서 절대 터지지 않게(orElseThrow 금지)
     * - 참조(작성자/게시물/댓글) 깨져도 DTO는 만들고, 비워서 내려주기
     * - N+1 방지 벌크 조회(Map)
     * =========================================================
     */
    @Transactional(readOnly = true)
    public NotificationResponseDTO getUserNotifications(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            // 여기서 throw하면 프론트가 “주원/은재만” 같은 케이스에서 또 죽을 수 있음
            // (토큰은 맞는데 DB에서 유저 조회가 꼬이는 상황도 방어)
            return NotificationResponseDTO.builder()
                    .lastCheckedAt(null)
                    .notifications(Collections.emptyList())
                    .build();
        }

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
        if (notifications == null || notifications.isEmpty()) {
            return NotificationResponseDTO.builder()
                    .lastCheckedAt(lastCheckedAt)
                    .notifications(Collections.emptyList())
                    .build();
        }

        // ✅ 내 알림 제외(서버에서 DTO 생성 전에 필터)
        List<Notification> filtered = notifications.stream()
                .filter(n -> !Objects.equals(n.getAuthorId(), userId))
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            return NotificationResponseDTO.builder()
                    .lastCheckedAt(lastCheckedAt)
                    .notifications(Collections.emptyList())
                    .build();
        }

        // ✅ 벌크 조회 준비
        Set<Long> authorIds = new HashSet<>();
        Set<UUID> postIds = new HashSet<>();
        Set<UUID> commentIds = new HashSet<>();

        for (Notification n : filtered) {
            if (n.getAuthorId() != null)
                authorIds.add(n.getAuthorId());
            if (n.getPostId() != null)
                postIds.add(n.getPostId());
            if (n.getCommentId() != null)
                commentIds.add(n.getCommentId());
        }

        Map<Long, User> authorMap = authorIds.isEmpty()
                ? Collections.emptyMap()
                : userRepository.findAllById(authorIds).stream()
                        .collect(Collectors.toMap(User::getUserId, u -> u, (a, b) -> a));

        Map<UUID, Post> postMap = postIds.isEmpty()
                ? Collections.emptyMap()
                : postRepository.findAllById(postIds).stream()
                        .collect(Collectors.toMap(Post::getPostId, p -> p, (a, b) -> a));

        Map<UUID, Comment> commentMap = commentIds.isEmpty()
                ? Collections.emptyMap()
                : commentRepository.findAllById(commentIds).stream()
                        .collect(Collectors.toMap(Comment::getCommentId, c -> c, (a, b) -> a));

        List<NotificationDTO> dtoList = new ArrayList<>();

        for (Notification n : filtered) {
            try {
                NotificationDTO dto = buildNotificationDTO(n, authorMap, postMap, commentMap);
                if (dto != null)
                    dtoList.add(dto);
            } catch (Exception e) {
                // ✅ 한 건 터져도 전체 응답은 살림
                logger.warn("[NOTI_SKIP] userId={}, notificationId={}, type={}, err={}",
                        userId, n.getNotificationId(), n.getNotificationType(), e.toString());
            }
        }

        return NotificationResponseDTO.builder()
                .lastCheckedAt(lastCheckedAt)
                .notifications(dtoList)
                .build();
    }

    private NotificationDTO buildNotificationDTO(
            Notification n,
            Map<Long, User> authorMap,
            Map<UUID, Post> postMap,
            Map<UUID, Comment> commentMap) {
        NotificationType type = n.getNotificationType();

        User author = (n.getAuthorId() == null) ? null : authorMap.get(n.getAuthorId());
        String authorName = safe(author != null ? author.getName() : null, "알 수 없음");
        String authorImage = safe(author != null ? author.getImage() : null, "");

        String categoryTitle = "";
        String contentPreview = "";
        String firstImageUrl = null;

        UUID postId = n.getPostId();
        UUID commentId = n.getCommentId();

        Post post = (postId == null) ? null : postMap.get(postId);
        Comment comment = (commentId == null) ? null : commentMap.get(commentId);

        // ✅ 타입별로 필요한 정보 채우기 (없으면 그냥 빈 값)
        if (type == NotificationType.POST) {
            if (post != null) {
                categoryTitle = safe(getCategoryTitleSafe(post), "");
                contentPreview = safe(trimContentSafe(post.getContent()), "");
                firstImageUrl = getFirstImageUrlSafe(post);
            }
        } else if (type == NotificationType.COMMENT) {
            if (comment != null) {
                // comment -> post 로 타고 들어가는데, 이게 LAZY/삭제로 터질 수 있어서 보호
                Post p = null;
                try {
                    p = comment.getPost();
                } catch (Exception ignore) {
                }

                if (p != null) {
                    categoryTitle = safe(getCategoryTitleSafe(p), "");
                    firstImageUrl = getFirstImageUrlSafe(p);
                } else if (post != null) {
                    // 혹시 notification.postId도 같이 저장되어 있다면 이걸로라도 복구
                    categoryTitle = safe(getCategoryTitleSafe(post), "");
                    firstImageUrl = getFirstImageUrlSafe(post);
                }

                contentPreview = safe(trimContentSafe(comment.getContent()), "");
            }
        }

        return NotificationDTO.builder()
                .notificationType(type)
                .postId(postId)
                .commentId(commentId)
                .createdAt(n.getCreatedAt())
                .authorName(authorName)
                .authorImage(authorImage)
                .categoryTitle(categoryTitle)
                .contentPreview(contentPreview)
                .firstImageUrl(firstImageUrl)
                .build();
    }

    private String getCategoryTitleSafe(Post post) {
        try {
            if (post == null)
                return "";
            Category c = post.getCategory();
            if (c == null)
                return "";
            return safe(c.getTitle(), "");
        } catch (Exception e) {
            return "";
        }
    }

    private String getFirstImageUrlSafe(Post post) {
        try {
            if (post == null)
                return null;
            if (post.getImages() == null || post.getImages().isEmpty())
                return null;
            return post.getImages().get(0).getImageUrl();
        } catch (Exception e) {
            return null;
        }
    }

    private String trimContentSafe(String content) {
        if (content == null)
            return "";
        return content.length() > 30 ? content.substring(0, 30) + "..." : content;
    }

    private String safe(String s, String fallback) {
        return (s == null) ? fallback : s;
    }

    /**
     * ✅ 변경 핵심 2) hasUnread도 "내가 만든 알림은 제외"해야 함.
     */
    @Transactional(readOnly = true)
    public boolean hasUnreadNotifications(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));

        LocalDateTime lastCheckedAt = user.getLastNotificationCheckedAt();

        List<UUID> familyIds = userFamilyRepository.findFamiliesByUserId(userId).stream()
                .map(Family::getFamilyId)
                .collect(Collectors.toList());

        if (familyIds.isEmpty())
            return false;

        LocalDateTime 기준 = (lastCheckedAt != null) ? lastCheckedAt : LocalDateTime.MIN;

        return notificationRepository.existsByFamilyIdInAndCreatedAtAfterAndAuthorIdNot(
                familyIds, 기준, userId);
    }

    @Transactional(readOnly = true)
    public long getUnreadNotificationCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));

        LocalDateTime lastCheckedAt = user.getLastNotificationCheckedAt();

        List<UUID> familyIds = userFamilyRepository.findFamiliesByUserId(userId).stream()
                .map(Family::getFamilyId)
                .collect(Collectors.toList());

        if (familyIds.isEmpty())
            return 0L;

        LocalDateTime 기준 = (lastCheckedAt != null) ? lastCheckedAt : LocalDateTime.MIN;

        return notificationRepository.countByFamilyIdInAndCreatedAtAfterAndAuthorIdNot(
                familyIds, 기준, userId);
    }

    // ✅ 채팅 unread 합계 (종과 분리)
    @Transactional(readOnly = true)
    public long getChatUnreadCount(Long userId) {
        List<UserChatRoom> links = userChatRoomRepository.findByUserId(userId);
        if (links == null || links.isEmpty())
            return 0L;

        long total = 0L;

        for (UserChatRoom ucr : links) {
            UUID chatRoomId = ucr.getChatRoom().getChatRoomId();
            LocalDateTime lastReadAt = ucr.getLastReadAt();

            int cnt;
            if (lastReadAt == null) {
                cnt = messageRepository.countByChatRoom_ChatRoomIdAndSender_UserIdNot(chatRoomId, userId);
            } else {
                cnt = messageRepository.countByChatRoom_ChatRoomIdAndCreatedAtAfterAndSender_UserIdNot(
                        chatRoomId, lastReadAt, userId);
            }

            total += Math.max(cnt, 0);
        }

        return total;
    }

    // ✅ 앱 배지 합계 = 종 unread + 채팅 unread
    @Transactional(readOnly = true)
    public long getBadgeCount(Long userId) {
        long bell = getUnreadNotificationCount(userId);
        long chat = getChatUnreadCount(userId);
        return Math.max(0L, bell + chat);
    }

    /**
     * ✅ 읽음 확정은 여기서만 한다.
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
        if (optionalUser.isEmpty())
            return false;

        User user = optionalUser.get();
        user.setIsPostNotificationOn(isOn);
        userRepository.save(user);
        return true;
    }

    @Transactional
    public boolean updateCommentNotificationSetting(Long userId, boolean isOn) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isEmpty())
            return false;

        User user = optionalUser.get();
        user.setIsCommentNotificationOn(isOn);
        userRepository.save(user);
        return true;
    }

    @Transactional
    public boolean updateChatNotificationSetting(Long userId, boolean isOn) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty())
            return false;

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
        if (birth == null || birth.isEmpty())
            return null;

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

        if (req.getName() != null && !req.getName().isBlank())
            user.setName(req.getName());
        if (req.getBirth() != null && !req.getBirth().isBlank())
            user.setBirth(parseDate(req.getBirth()));

        if (req.getTermsAgreed() != null)
            user.setTermsAgreed(req.getTermsAgreed());
        if (req.getPrivacyAgreed() != null)
            user.setPrivacyAgreed(req.getPrivacyAgreed());
        if (req.getMarketingAgreed() != null)
            user.setMarketingAgreed(req.getMarketingAgreed());
        if (req.getTermsVersion() != null)
            user.setTermsVersion(req.getTermsVersion());
        if (req.getPrivacyVersion() != null)
            user.setPrivacyVersion(req.getPrivacyVersion());
        if (req.getAgreedAt() != null)
            user.setAgreedAt(parseDateTime(req.getAgreedAt()));
        if (req.getMarketingAgreedAt() != null)
            user.setMarketingAgreedAt(parseDateTime(req.getMarketingAgreedAt()));

        userRepository.save(user);
        return new UserDTO(user);
    }
}

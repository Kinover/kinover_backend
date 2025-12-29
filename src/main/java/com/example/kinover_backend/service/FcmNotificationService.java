package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.CommentDTO;
import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.dto.PostDTO;
import com.example.kinover_backend.entity.*;
import com.example.kinover_backend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.google.auth.oauth2.AccessToken;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

// ✅ 추가: 분리된 enum
import com.example.kinover_backend.enums.PushType;

@Service
@RequiredArgsConstructor
public class FcmNotificationService {

    private final UserRepository userRepository;
    private final UserFamilyRepository userFamilyRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomNotificationRepository chatRoomNotificationRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final CategoryRepository categoryRepository;
    private final PostRepository postRepository;
    private final NotificationRepository notificationRepository;

    // ✅ 추가: 채팅 unread 계산용
    private final UserChatRoomRepository userChatRoomRepository;
    private final MessageRepository messageRepository;

    private static GoogleCredentials firebaseCreds;

    public boolean isChatRoomNotificationOn(Long userId, UUID chatRoomId) {
        User user = userRepository.findById(userId).orElseThrow();

        if (!Boolean.TRUE.equals(user.getIsChatNotificationOn())) {
            return false;
        }

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId).orElseThrow();

        return chatRoomNotificationRepository.findByUserAndChatRoom(user, chatRoom)
                .map(ChatRoomNotificationSetting::isNotificationOn)
                .orElse(true);
    }

    // ✅ 벨(종) unreadCount: Notification 테이블 기준 (채팅 제외)
    private long calcBellUnreadCount(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();

        LocalDateTime lastCheckedAt = user.getLastNotificationCheckedAt();
        LocalDateTime 기준 = (lastCheckedAt != null) ? lastCheckedAt : LocalDateTime.MIN;

        List<UUID> familyIds = userFamilyRepository.findFamiliesByUserId(userId).stream()
                .map(Family::getFamilyId)
                .collect(Collectors.toList());

        if (familyIds.isEmpty()) return 0L;

        return notificationRepository.countByFamilyIdInAndCreatedAtAfterAndAuthorIdNot(
                familyIds, 기준, userId
        );
    }

    // ✅ 채팅 unreadCount: UserChatRoom.lastReadAt + Message.createdAt 기준
    private long calcChatUnreadCount(Long userId) {
        List<UserChatRoom> links = userChatRoomRepository.findByUserId(userId);
        if (links == null || links.isEmpty()) return 0L;

        long total = 0L;

        for (UserChatRoom ucr : links) {
            UUID chatRoomId = ucr.getChatRoom().getChatRoomId();
            LocalDateTime lastReadAt = ucr.getLastReadAt();

            int cnt;
            if (lastReadAt == null) {
                cnt = messageRepository.countByChatRoom_ChatRoomIdAndSender_UserIdNot(chatRoomId, userId);
            } else {
                cnt = messageRepository.countByChatRoom_ChatRoomIdAndCreatedAtAfterAndSender_UserIdNot(
                        chatRoomId, lastReadAt, userId
                );
            }
            total += Math.max(cnt, 0);
        }

        return total;
    }

    // ✅ 앱 배지 = 종 + 채팅
    private long calcBadgeCount(Long userId) {
        long bell = calcBellUnreadCount(userId);
        long chat = calcChatUnreadCount(userId);
        return Math.max(0L, bell + chat);
    }

    // ✅ 공통: pushType / notificationType 둘 다 넣어주는 헬퍼 (레거시 호환)
    private static void putPushType(Message.Builder mb, PushType pushType) {
        if (pushType == null) return;
        mb.putData("pushType", pushType.name());
        // 레거시 호환(기존 프론트가 notificationType만 보고 있을 수 있음)
        mb.putData("notificationType", pushType.name());
    }

    public void sendChatNotification(Long userId, MessageDTO messageDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        ChatRoom chatRoom = chatRoomRepository.findById(messageDTO.getChatRoomId())
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다."));
        Optional<FcmToken> fcmTokenOpt = fcmTokenRepository.findByUser(user);

        if (fcmTokenOpt.isEmpty()) return;

        String token = fcmTokenOpt.get().getToken();
        String messageType = messageDTO.getMessageType().name();

        String body;
        switch (messageType) {
            case "text" -> body = messageDTO.getSenderName() + ": " + messageDTO.getContent();
            case "image" -> {
                int imageCount = messageDTO.getImageUrls() != null ? messageDTO.getImageUrls().size() : 0;
                body = messageDTO.getSenderName() + ": 사진 " + imageCount + "장을 보냈습니다.";
            }
            case "video" -> body = messageDTO.getSenderName() + ": 동영상을 보냈습니다.";
            default -> body = messageDTO.getSenderName() + ": 새로운 메시지가 도착했습니다.";
        }

        long bellUnreadCount = calcBellUnreadCount(userId);   // ✅ 종용(채팅 제외)
        long badgeCount = calcBadgeCount(userId);             // ✅ 앱 배지용(채팅 포함)

        // --- iOS(APNs) ---
        ApsAlert apsAlert = ApsAlert.builder()
                .setTitle(chatRoom.getRoomName())
                .setBody(body)
                .build();

        Aps aps = Aps.builder()
                .setAlert(apsAlert)
                .setSound("default")
                .setBadge((int) badgeCount)
                .setThreadId("chat_" + chatRoom.getChatRoomId())
                .build();

        ApnsConfig apnsConfig = ApnsConfig.builder()
                .putHeader("apns-push-type", "alert")
                .putHeader("apns-priority", "10")
                .setAps(aps)
                .build();

        // --- Android ---
        AndroidNotification androidNotification = AndroidNotification.builder()
                .setSound("default")
                .setChannelId("chat")
                .setTag("chat_" + chatRoom.getChatRoomId())
                .setNotificationCount((int) badgeCount)
                .build();

        AndroidConfig androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(androidNotification)
                .build();

        // --- 공통 ---
        Message.Builder messageBuilder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(chatRoom.getRoomName())
                        .setBody(body)
                        .build())
                .setApnsConfig(apnsConfig)
                .setAndroidConfig(androidConfig)
                .putData("chatRoomId", String.valueOf(chatRoom.getChatRoomId()))
                .putData("messageType", messageType)
                .putData("senderName", messageDTO.getSenderName())
                .putData("senderImage", messageDTO.getSenderImage())
                .putData("roomName", chatRoom.getRoomName())
                // ✅ 프론트 호환용: bell unread(기존 unreadCount)
                .putData("unreadCount", String.valueOf(bellUnreadCount))
                // ✅ 앱 배지(채팅 포함)
                .putData("badgeCount", String.valueOf(badgeCount));

        // ✅ 여기서 타입 세팅
        putPushType(messageBuilder, PushType.CHAT);

        if (messageType.equals("image") || messageType.equals("video")) {
            try {
                String imageUrlJson = new ObjectMapper().writeValueAsString(messageDTO.getImageUrls());
                messageBuilder.putData("imageUrls", imageUrlJson);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try {
            FirebaseMessaging.getInstance().send(messageBuilder.build());
        } catch (FirebaseMessagingException e) {
            e.printStackTrace();
        }
    }

    @Transactional
    public void sendPostNotification(Long userId, PostDTO postDTO) {
        if (postDTO.getAuthorId() != null && postDTO.getAuthorId().equals(userId)) {
            return;
        }

        User receiver = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("알림 받을 사용자를 찾을 수 없습니다."));
        User author = userRepository.findById(postDTO.getAuthorId())
                .orElseThrow(() -> new RuntimeException("작성자를 찾을 수 없습니다."));
        Category category = categoryRepository.findById(postDTO.getCategoryId())
                .orElseThrow(() -> new RuntimeException("카테고리를 찾을 수 없습니다."));

        Optional<FcmToken> fcmTokenOpt = fcmTokenRepository.findByUser(receiver);
        if (fcmTokenOpt.isEmpty()) return;

        final String token = fcmTokenOpt.get().getToken();
        final String firstImageUrl = (postDTO.getImageUrls() != null && !postDTO.getImageUrls().isEmpty())
                ? postDTO.getImageUrls().get(0)
                : null;

        final String title = "새 게시물 알림";
        final String body  = author.getName() + "님이 \"" + category.getTitle() + "\"에 \""
                + trimContent(postDTO.getContent()) + "\" 글을 작성했습니다.";

        long bellUnreadCount = calcBellUnreadCount(userId);
        long badgeCount = calcBadgeCount(userId);

        // --- iOS(APNs) ---
        ApsAlert apsAlert = ApsAlert.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        Aps aps = Aps.builder()
                .setAlert(apsAlert)
                .setSound("default")
                .setBadge((int) badgeCount)
                .setThreadId("post_" + category.getCategoryId())
                .setMutableContent(true)
                .build();

        ApnsConfig.Builder apnsBuilder = ApnsConfig.builder()
                .putHeader("apns-push-type", "alert")
                .putHeader("apns-priority", "10")
                .setAps(aps);

        if (firstImageUrl != null && !firstImageUrl.isBlank()) {
            apnsBuilder.putCustomData("fcm_options", Map.of("image", firstImageUrl));
            apnsBuilder.putCustomData("imageUrl", firstImageUrl);
        }

        ApnsConfig apnsConfig = apnsBuilder.build();

        // --- Android ---
        AndroidNotification.Builder an = AndroidNotification.builder()
                .setSound("default")
                .setChannelId("post")
                .setTag("post_" + category.getCategoryId())
                .setNotificationCount((int) badgeCount);

        if (firstImageUrl != null && !firstImageUrl.isBlank()) {
            an.setImage(firstImageUrl);
        }

        AndroidConfig androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(an.build())
                .build();

        Notification.Builder notifBuilder = Notification.builder()
                .setTitle(title)
                .setBody(body);

        if (firstImageUrl != null && !firstImageUrl.isBlank()) {
            notifBuilder.setImage(firstImageUrl);
        }

        Message.Builder mb = Message.builder()
                .setToken(token)
                .setNotification(notifBuilder.build())
                .setApnsConfig(apnsConfig)
                .setAndroidConfig(androidConfig)
                .putData("postId", String.valueOf(postDTO.getPostId()))
                .putData("authorName", nvl(author.getName()))
                .putData("authorImage", nvl(author.getImage()))
                .putData("categoryTitle", nvl(category.getTitle()))
                .putData("contentPreview", nvl(trimContent(postDTO.getContent())))
                .putData("unreadCount", String.valueOf(bellUnreadCount))
                .putData("badgeCount", String.valueOf(badgeCount));

        // ✅ 타입 세팅
        putPushType(mb, PushType.POST);

        if (firstImageUrl != null && !firstImageUrl.isBlank()) {
            mb.putData("firstImageUrl", firstImageUrl);
        }

        try {
            FirebaseMessaging.getInstance().send(mb.build());
        } catch (FirebaseMessagingException e) {
            e.printStackTrace();
        }
    }

    private static String nvl(String s) { return s == null ? "" : s; }

    public void sendCommentNotification(Long userId, CommentDTO commentDTO) {
        if (commentDTO.getAuthorId() != null && commentDTO.getAuthorId().equals(userId)) {
            return;
        }

        User receiver = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("알림 받을 사용자를 찾을 수 없습니다."));
        User author = userRepository.findById(commentDTO.getAuthorId())
                .orElseThrow(() -> new RuntimeException("작성자를 찾을 수 없습니다."));
        Post post = postRepository.findById(commentDTO.getPostId())
                .orElseThrow(() -> new RuntimeException("게시물을 찾을 수 없습니다."));
        Category category = post.getCategory();

        Optional<FcmToken> fcmTokenOpt = fcmTokenRepository.findByUser(receiver);
        if (fcmTokenOpt.isEmpty()) return;

        String token = fcmTokenOpt.get().getToken();

        String firstImageUrl = (post.getImages() != null && !post.getImages().isEmpty())
                ? post.getImages().get(0).getImageUrl()
                : null;

        String title = "새 댓글 알림";
        String body = author.getName() + "님이 \"" + category.getTitle() + "\"에 \"" +
                trimContent(commentDTO.getContent()) + "\" 댓글을 작성했습니다.";

        long bellUnreadCount = calcBellUnreadCount(userId);
        long badgeCount = calcBadgeCount(userId);

        // --- iOS(APNs) ---
        ApsAlert apsAlert = ApsAlert.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        Aps aps = Aps.builder()
                .setAlert(apsAlert)
                .setSound("default")
                .setBadge((int) badgeCount)
                .setThreadId("comment_" + post.getPostId())
                .build();

        ApnsConfig apnsConfig = ApnsConfig.builder()
                .putHeader("apns-push-type", "alert")
                .putHeader("apns-priority", "10")
                .setAps(aps)
                .build();

        // --- Android ---
        AndroidNotification.Builder an = AndroidNotification.builder()
                .setSound("default")
                .setChannelId("comment")
                .setTag("comment_" + post.getPostId())
                .setNotificationCount((int) badgeCount);

        if (firstImageUrl != null) {
            an.setImage(firstImageUrl);
        }

        AndroidConfig androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(an.build())
                .build();

        Message.Builder messageBuilder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .setApnsConfig(apnsConfig)
                .setAndroidConfig(androidConfig)
                .putData("postId", String.valueOf(post.getPostId()))
                .putData("authorName", author.getName())
                .putData("authorImage", author.getImage())
                .putData("categoryTitle", category.getTitle())
                .putData("contentPreview", trimContent(commentDTO.getContent()))
                .putData("unreadCount", String.valueOf(bellUnreadCount))
                .putData("badgeCount", String.valueOf(badgeCount));

        // ✅ 타입 세팅
        putPushType(messageBuilder, PushType.COMMENT);

        if (firstImageUrl != null) {
            messageBuilder.putData("firstImageUrl", firstImageUrl);
        }

        try {
            FirebaseMessaging.getInstance().send(messageBuilder.build());
        } catch (FirebaseMessagingException e) {
            e.printStackTrace();
            diagnoseFirebaseAuth();
            throw new RuntimeException(e);
        }
    }

    private void diagnoseFirebaseAuth() {
        try {
            FirebaseApp app = FirebaseApp.getInstance();
            FirebaseOptions opts = app.getOptions();

            System.out.println("[FCM DIAG] projectId(opt)=" + opts.getProjectId());

            if (firebaseCreds == null) {
                System.out.println("[FCM DIAG] firebaseCreds is null (init not done?)");
                return;
            }

            System.out.println("[FCM DIAG] credsClass=" + firebaseCreds.getClass().getName());
            if (firebaseCreds instanceof ServiceAccountCredentials sac) {
                System.out.println("[FCM DIAG] saEmail=" + sac.getClientEmail()
                        + ", saProjectId=" + sac.getProjectId());
            }

            try {
                AccessToken t = firebaseCreds.refreshAccessToken();
                System.out.println("[FCM DIAG] accessToken exp=" + t.getExpirationTime());
            } catch (IOException tokEx) {
                System.out.println("[FCM DIAG] token refresh failed: " + tokEx);
            }

            System.out.println("[FCM DIAG] $GOOGLE_APPLICATION_CREDENTIALS="
                    + System.getenv("GOOGLE_APPLICATION_CREDENTIALS"));
        } catch (Throwable diagEx) {
            System.out.println("[FCM DIAG] failed: " + diagEx);
        }
    }

    private String trimContent(String content) {
        if (content == null) return "";
        return content.length() > 30 ? content.substring(0, 30) + "..." : content;
    }

    @Transactional
    public boolean updateChatRoomNotificationSetting(Long userId, UUID chatRoomId, boolean isOn) {
        Optional<User> userOpt = userRepository.findById(userId);
        Optional<ChatRoom> chatRoomOpt = chatRoomRepository.findById(chatRoomId);

        if (userOpt.isEmpty() || chatRoomOpt.isEmpty()) return false;

        User user = userOpt.get();
        ChatRoom chatRoom = chatRoomOpt.get();

        Optional<ChatRoomNotificationSetting> settingOpt =
                chatRoomNotificationRepository.findByUserAndChatRoom(user, chatRoom);

        if (settingOpt.isEmpty()) return false;

        ChatRoomNotificationSetting setting = settingOpt.get();
        setting.setNotificationOn(isOn);
        chatRoomNotificationRepository.save(setting);

        return true;
    }

    public void sendMentionCommentNotification(Long userId, CommentDTO commentDTO) {
        if (commentDTO.getAuthorId() != null && commentDTO.getAuthorId().equals(userId)) {
            return;
        }

        User receiver = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("알림 받을 사용자를 찾을 수 없습니다."));
        User author = userRepository.findById(commentDTO.getAuthorId())
                .orElseThrow(() -> new RuntimeException("작성자를 찾을 수 없습니다."));
        Post post = postRepository.findById(commentDTO.getPostId())
                .orElseThrow(() -> new RuntimeException("게시물을 찾을 수 없습니다."));
        Category category = post.getCategory();

        Optional<FcmToken> fcmTokenOpt = fcmTokenRepository.findByUser(receiver);
        if (fcmTokenOpt.isEmpty()) return;

        String token = fcmTokenOpt.get().getToken();

        String firstImageUrl = (post.getImages() != null && !post.getImages().isEmpty())
                ? post.getImages().get(0).getImageUrl()
                : null;

        String title = "멘션 알림";
        String body = author.getName() + "님이 댓글에서 당신을 언급했어요: \"" +
                trimContent(commentDTO.getContent()) + "\"";

        long bellUnreadCount = calcBellUnreadCount(userId);
        long badgeCount = calcBadgeCount(userId);

        ApsAlert apsAlert = ApsAlert.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        Aps aps = Aps.builder()
                .setAlert(apsAlert)
                .setSound("default")
                .setBadge((int) badgeCount)
                .setThreadId("mention_comment_" + post.getPostId())
                .build();

        ApnsConfig apnsConfig = ApnsConfig.builder()
                .putHeader("apns-push-type", "alert")
                .putHeader("apns-priority", "10")
                .setAps(aps)
                .build();

        AndroidNotification.Builder an = AndroidNotification.builder()
                .setSound("default")
                .setChannelId("comment")
                .setTag("mention_comment_" + post.getPostId())
                .setNotificationCount((int) badgeCount);

        if (firstImageUrl != null) {
            an.setImage(firstImageUrl);
        }

        AndroidConfig androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(an.build())
                .build();

        Message.Builder messageBuilder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .setApnsConfig(apnsConfig)
                .setAndroidConfig(androidConfig)
                .putData("postId", String.valueOf(post.getPostId()))
                .putData("authorName", nvl(author.getName()))
                .putData("authorImage", nvl(author.getImage()))
                .putData("categoryTitle", nvl(category.getTitle()))
                .putData("contentPreview", nvl(trimContent(commentDTO.getContent())))
                .putData("unreadCount", String.valueOf(bellUnreadCount))
                .putData("badgeCount", String.valueOf(badgeCount));

        // ✅ 타입 세팅
        putPushType(messageBuilder, PushType.MENTION_COMMENT);

        if (firstImageUrl != null) {
            messageBuilder.putData("firstImageUrl", firstImageUrl);
        }

        try {
            FirebaseMessaging.getInstance().send(messageBuilder.build());
        } catch (FirebaseMessagingException e) {
            diagnoseFirebaseAuth();
            throw new RuntimeException(e);
        }
    }

    public void sendMentionChatNotification(Long userId, MessageDTO messageDTO) {
        if (messageDTO.getSenderId() != null && messageDTO.getSenderId().equals(userId)) {
            return;
        }

        User receiver = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("알림 받을 사용자를 찾을 수 없습니다."));
        ChatRoom chatRoom = chatRoomRepository.findById(messageDTO.getChatRoomId())
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다."));

        Optional<FcmToken> fcmTokenOpt = fcmTokenRepository.findByUser(receiver);
        if (fcmTokenOpt.isEmpty()) return;

        String token = fcmTokenOpt.get().getToken();

        String body;
        String messageType = messageDTO.getMessageType().name();

        switch (messageType) {
            case "text" -> body = messageDTO.getSenderName() + ": " + nvl(messageDTO.getContent());
            case "image" -> {
                int imageCount = messageDTO.getImageUrls() != null ? messageDTO.getImageUrls().size() : 0;
                body = messageDTO.getSenderName() + ": 사진 " + imageCount + "장을 보냈습니다.";
            }
            case "video" -> body = messageDTO.getSenderName() + ": 동영상을 보냈습니다.";
            default -> body = messageDTO.getSenderName() + ": 새로운 메시지가 도착했습니다.";
        }

        String title = chatRoom.getRoomName();
        String mentionBody = "당신을 언급했어요 · " + body;

        long bellUnreadCount = calcBellUnreadCount(userId);
        long badgeCount = calcBadgeCount(userId);

        ApsAlert apsAlert = ApsAlert.builder()
                .setTitle(title)
                .setBody(mentionBody)
                .build();

        Aps aps = Aps.builder()
                .setAlert(apsAlert)
                .setSound("default")
                .setBadge((int) badgeCount)
                .setThreadId("mention_chat_" + chatRoom.getChatRoomId())
                .build();

        ApnsConfig apnsConfig = ApnsConfig.builder()
                .putHeader("apns-push-type", "alert")
                .putHeader("apns-priority", "10")
                .setAps(aps)
                .build();

        AndroidNotification androidNotification = AndroidNotification.builder()
                .setSound("default")
                .setChannelId("chat")
                .setTag("mention_chat_" + chatRoom.getChatRoomId())
                .setNotificationCount((int) badgeCount)
                .build();

        AndroidConfig androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(androidNotification)
                .build();

        Message.Builder mb = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(mentionBody)
                        .build())
                .setApnsConfig(apnsConfig)
                .setAndroidConfig(androidConfig)
                .putData("chatRoomId", String.valueOf(chatRoom.getChatRoomId()))
                .putData("messageType", messageType)
                .putData("senderName", nvl(messageDTO.getSenderName()))
                .putData("senderImage", nvl(messageDTO.getSenderImage()))
                .putData("roomName", nvl(chatRoom.getRoomName()))
                .putData("unreadCount", String.valueOf(bellUnreadCount))
                .putData("badgeCount", String.valueOf(badgeCount));

        // ✅ 타입 세팅
        putPushType(mb, PushType.MENTION_CHAT);

        if ("image".equals(messageType) || "video".equals(messageType)) {
            try {
                String imageUrlJson = new ObjectMapper().writeValueAsString(messageDTO.getImageUrls());
                mb.putData("imageUrls", imageUrlJson);
            } catch (Exception ignore) {}
        }

        try {
            FirebaseMessaging.getInstance().send(mb.build());
        } catch (FirebaseMessagingException e) {
            e.printStackTrace();
        }
    }
}

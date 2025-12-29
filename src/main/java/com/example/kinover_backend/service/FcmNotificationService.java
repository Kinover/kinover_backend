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

@Service
@RequiredArgsConstructor
public class FcmNotificationService {

    private final UserRepository userRepository;
    private final UserFamilyRepository userFamilyRepository; // ✅ 추가
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomNotificationRepository chatRoomNotificationRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final CategoryRepository categoryRepository;
    private final PostRepository postRepository;
    private final NotificationRepository notificationRepository;

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

    // ✅ 공통: unreadCount 계산 (bell 알림 기준: lastNotificationCheckedAt 이후 + 본인 author 제외)
    private long calcUnreadCount(Long userId) {
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

        long unreadCount = calcUnreadCount(userId);

        // --- iOS(APNs) ---
        ApsAlert apsAlert = ApsAlert.builder()
                .setTitle(chatRoom.getRoomName())
                .setBody(body)
                .build();

        Aps aps = Aps.builder()
                .setAlert(apsAlert)
                .setSound("default")
                .setBadge((int) unreadCount) // ✅ 여기!
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
                .setNotificationCount((int) unreadCount) // ✅ 가능하면 표시
                .build();

        AndroidConfig androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(androidNotification)
                .build();

        // --- 공통 Notification + Data ---
        Message.Builder messageBuilder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(chatRoom.getRoomName())
                        .setBody(body)
                        .build())
                .setApnsConfig(apnsConfig)
                .setAndroidConfig(androidConfig)
                // ✅ 라우팅용 데이터 (프론트에서 눌렀을 때 해당 화면 이동)
                .putData("notificationType", "CHAT")
                .putData("chatRoomId", String.valueOf(chatRoom.getChatRoomId()))
                .putData("messageType", messageType)
                .putData("senderName", messageDTO.getSenderName())
                .putData("senderImage", messageDTO.getSenderImage())
                .putData("roomName", chatRoom.getRoomName())
                .putData("unreadCount", String.valueOf(unreadCount));

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

        long unreadCount = calcUnreadCount(userId);

        // --- iOS(APNs) ---
        ApsAlert apsAlert = ApsAlert.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        Aps aps = Aps.builder()
                .setAlert(apsAlert)
                .setSound("default")
                .setBadge((int) unreadCount) // ✅
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
                .setNotificationCount((int) unreadCount); // ✅

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
                .putData("notificationType", "POST")
                .putData("postId", String.valueOf(postDTO.getPostId()))
                .putData("authorName", nvl(author.getName()))
                .putData("authorImage", nvl(author.getImage()))
                .putData("categoryTitle", nvl(category.getTitle()))
                .putData("contentPreview", nvl(trimContent(postDTO.getContent())))
                .putData("unreadCount", String.valueOf(unreadCount));

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

        long unreadCount = calcUnreadCount(userId);

        // --- iOS(APNs) ---
        ApsAlert apsAlert = ApsAlert.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        Aps aps = Aps.builder()
                .setAlert(apsAlert)
                .setSound("default")
                .setBadge((int) unreadCount) // ✅
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
                .setNotificationCount((int) unreadCount); // ✅

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
                .putData("notificationType", "COMMENT")
                .putData("postId", String.valueOf(post.getPostId()))
                .putData("authorName", author.getName())
                .putData("authorImage", author.getImage())
                .putData("categoryTitle", category.getTitle())
                .putData("contentPreview", trimContent(commentDTO.getContent()))
                .putData("unreadCount", String.valueOf(unreadCount));

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

        long unreadCount = calcUnreadCount(userId);

        ApsAlert apsAlert = ApsAlert.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        Aps aps = Aps.builder()
                .setAlert(apsAlert)
                .setSound("default")
                .setBadge((int) unreadCount) // ✅
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
                .setNotificationCount((int) unreadCount); // ✅

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
                .putData("notificationType", "MENTION_COMMENT")
                .putData("postId", String.valueOf(post.getPostId()))
                .putData("authorName", nvl(author.getName()))
                .putData("authorImage", nvl(author.getImage()))
                .putData("categoryTitle", nvl(category.getTitle()))
                .putData("contentPreview", nvl(trimContent(commentDTO.getContent())))
                .putData("unreadCount", String.valueOf(unreadCount));

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

        long unreadCount = calcUnreadCount(userId);

        ApsAlert apsAlert = ApsAlert.builder()
                .setTitle(title)
                .setBody(mentionBody)
                .build();

        Aps aps = Aps.builder()
                .setAlert(apsAlert)
                .setSound("default")
                .setBadge((int) unreadCount) // ✅
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
                .setNotificationCount((int) unreadCount) // ✅
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
                .putData("notificationType", "MENTION_CHAT")
                .putData("chatRoomId", String.valueOf(chatRoom.getChatRoomId()))
                .putData("messageType", messageType)
                .putData("senderName", nvl(messageDTO.getSenderName()))
                .putData("senderImage", nvl(messageDTO.getSenderImage()))
                .putData("roomName", nvl(chatRoom.getRoomName()))
                .putData("unreadCount", String.valueOf(unreadCount));

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

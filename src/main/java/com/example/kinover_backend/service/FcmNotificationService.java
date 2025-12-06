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

import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FcmNotificationService {

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomNotificationRepository chatRoomNotificationRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final CategoryRepository categoryRepository;
    private final PostRepository postRepository;
    private final NotificationRepository notificationRepository;
    private static GoogleCredentials firebaseCreds;

    public boolean isChatRoomNotificationOn(Long userId, UUID chatRoomId) {
        User user = userRepository.findById(userId).orElseThrow();

        // 유저 전체 채팅 알림 설정이 꺼져있으면 바로 false 반환
        if (!Boolean.TRUE.equals(user.getIsChatNotificationOn())) {
            return false;
        }

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId).orElseThrow();

        return chatRoomNotificationRepository.findByUserAndChatRoom(user, chatRoom)
                .map(ChatRoomNotificationSetting::isNotificationOn)
                .orElse(true); // 설정 없으면 알림 ON
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

        // --- iOS(APNs) 세팅 ---
        ApsAlert apsAlert = ApsAlert.builder()
                .setTitle(chatRoom.getRoomName())
                .setBody(body)
                .build();

        Aps aps = Aps.builder()
                .setAlert(apsAlert)
                .setSound("default")
                .setBadge(1)
                .setThreadId("chat_" + chatRoom.getChatRoomId())
                .build();

        ApnsConfig apnsConfig = ApnsConfig.builder()
                .putHeader("apns-push-type", "alert")
                .putHeader("apns-priority", "10")
                .setAps(aps)
                .build();

        // --- Android 세팅 ---
        AndroidNotification androidNotification = AndroidNotification.builder()
                .setSound("default")
                .setChannelId("chat")
                .setTag("chat_" + chatRoom.getChatRoomId())
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
                .putData("messageType", messageType)
                .putData("senderName", messageDTO.getSenderName())
                .putData("senderImage", messageDTO.getSenderImage())
                .putData("roomName", chatRoom.getRoomName());

        // 이미지/비디오의 경우 URL 리스트를 JSON 문자열로 변환해 전달
        if (messageType.equals("image") || messageType.equals("video")) {
            try {
                String imageUrlJson = new ObjectMapper().writeValueAsString(messageDTO.getImageUrls());
                messageBuilder.putData("imageUrls", imageUrlJson);
            } catch (Exception e) {
                e.printStackTrace();  // JSON 변환 실패 시 무시
            }
        }

        try {
            FirebaseMessaging.getInstance().send(messageBuilder.build());
        } catch (FirebaseMessagingException e) {
            e.printStackTrace(); // TODO: 로깅 시스템으로 전환
        }
    }


    
    @Transactional
    public void sendPostNotification(Long userId, PostDTO postDTO) {
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

        // --- iOS(APNs) 세팅 ---
        ApsAlert apsAlert = ApsAlert.builder()
            .setTitle(title)
            .setBody(body)
            .build();

        Aps aps = Aps.builder()
            .setAlert(apsAlert)       // iOS 배너를 위해 반드시 필요
            .setSound("default")      // 무음 방지
            .setBadge(1)              // 클라에서 누적 관리 권장
            .setThreadId("post_" + category.getCategoryId())
            .setMutableContent(true) // ★수정됨: iOS에서 이미지를 띄우려면 필수 (Notification Extension 트리거)
            .build();

        ApnsConfig.Builder apnsBuilder = ApnsConfig.builder()
            .putHeader("apns-push-type", "alert") // iOS13+ 필수
            .putHeader("apns-priority", "10")     // 즉시 표시
            .setAps(aps);

        if (firstImageUrl != null && !firstImageUrl.isBlank()) {
            // ★수정됨: iOS 표준 이미지 전송 방식 (fcm_options -> image)
            // 클라이언트가 별도 로직 없이 FCM SDK만으로 이미지를 처리하려 할 때 필요
            apnsBuilder.putCustomData("fcm_options", Map.of("image", firstImageUrl));
            
            // (기존 유지) 혹시 클라이언트 앱 코드에서 'imageUrl' 키를 직접 파싱하고 있다면 유지
            apnsBuilder.putCustomData("imageUrl", firstImageUrl);
        }

        ApnsConfig apnsConfig = apnsBuilder.build();

        // --- Android 세팅 ---
        AndroidNotification.Builder an = AndroidNotification.builder()
            .setSound("default")
            .setChannelId("post")
            .setTag("post_" + category.getCategoryId());

        if (firstImageUrl != null && !firstImageUrl.isBlank()) {
            an.setImage(firstImageUrl);
        }

        AndroidConfig androidConfig = AndroidConfig.builder()
            .setPriority(AndroidConfig.Priority.HIGH)
            .setNotification(an.build())
            .build();

        // --- 공통 Notification ---
        Notification.Builder notifBuilder = Notification.builder()
            .setTitle(title)
            .setBody(body);

        // ★수정됨: 공통 알림 객체에도 이미지 설정 (가장 권장되는 방식)
        if (firstImageUrl != null && !firstImageUrl.isBlank()) {
            notifBuilder.setImage(firstImageUrl);
        }

        Message.Builder mb = Message.builder()
            .setToken(token)
            .setNotification(notifBuilder.build()) // 수정된 빌더 사용
            .setApnsConfig(apnsConfig)
            .setAndroidConfig(androidConfig)
            .putData("notificationType", "POST")
            .putData("postId", String.valueOf(postDTO.getPostId()))
            .putData("authorName", nvl(author.getName()))
            .putData("authorImage", nvl(author.getImage()))
            .putData("categoryTitle", nvl(category.getTitle()))
            .putData("contentPreview", nvl(trimContent(postDTO.getContent())));

        if (firstImageUrl != null && !firstImageUrl.isBlank()) {
            mb.putData("firstImageUrl", firstImageUrl);
        }

        try {
            FirebaseMessaging.getInstance().send(mb.build());
        } catch (FirebaseMessagingException e) {
            e.printStackTrace();
        }
    }

    // null -> "" 로 치환
    private static String nvl(String s) { return s == null ? "" : s; }

    public void sendCommentNotification(Long userId, CommentDTO commentDTO) {
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

        // --- iOS(APNs) 세팅 ---
        ApsAlert apsAlert = ApsAlert.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        Aps aps = Aps.builder()
                .setAlert(apsAlert)
                .setSound("default")
                .setBadge(1)
                .setThreadId("comment_" + post.getPostId())
                .build();

        ApnsConfig apnsConfig = ApnsConfig.builder()
                .putHeader("apns-push-type", "alert")
                .putHeader("apns-priority", "10")
                .setAps(aps)
                .build();

        // --- Android 세팅 ---
        AndroidNotification.Builder an = AndroidNotification.builder()
                .setSound("default")
                .setChannelId("comment")
                .setTag("comment_" + post.getPostId());

        if (firstImageUrl != null) {
            an.setImage(firstImageUrl);
        }

        AndroidConfig androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(an.build())
                .build();

        // --- 공통 Notification + Data ---
        Message.Builder messageBuilder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .setApnsConfig(apnsConfig)
                .setAndroidConfig(androidConfig)
                .putData("notificationType", "COMMENT")
                .putData("authorName", author.getName())
                .putData("authorImage", author.getImage())
                .putData("categoryTitle", category.getTitle())
                .putData("contentPreview", trimContent(commentDTO.getContent()));

        if (firstImageUrl != null) {
            messageBuilder.putData("firstImageUrl", firstImageUrl);
        }

        try {
            String messageId = FirebaseMessaging.getInstance().send(messageBuilder.build());
            System.out.println("[FCM OK] messageId=" + messageId);
        } catch (FirebaseMessagingException e) {
            System.out.println("[FCM ERROR] msg=" + e.getMessage());
            try {
                var mec = e.getMessagingErrorCode();
                System.out.println("[FCM ERROR] code=" + mec);
            } catch (Throwable ignore) {}

            Throwable cause = e.getCause();
            if (cause instanceof com.google.api.client.http.HttpResponseException hre) {
                System.out.println("[FCM HTTP] status=" + hre.getStatusCode()
                        + ", statusMsg=" + hre.getStatusMessage());
                String content = hre.getContent();
                if (content != null) System.out.println("[FCM HTTP body] " + content);
            }

            diagnoseFirebaseAuth(); // 아래 함수
            throw new RuntimeException(e); // 필요 없으면 제거
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
        System.out.println("[FCM DIAG] systemNow=" + new java.util.Date()
                + ", tz=" + java.util.TimeZone.getDefault().getID());
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


}

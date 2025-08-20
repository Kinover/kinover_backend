package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.CommentDTO;
import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.dto.PostDTO;
import com.example.kinover_backend.entity.*;
import com.example.kinover_backend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.*;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
            case "text" -> {
                body = messageDTO.getSenderName() + ": " + messageDTO.getContent();
            }
            case "image" -> {
                int imageCount = messageDTO.getImageUrls() != null ? messageDTO.getImageUrls().size() : 0;
                body = messageDTO.getSenderName() + ": 사진 " + imageCount + "장을 보냈습니다.";
            }
            case "video" -> {
                body = messageDTO.getSenderName() + ": 동영상을 보냈습니다.";
            }
            default -> {
                body = messageDTO.getSenderName() + ": 새로운 메시지가 도착했습니다.";
            }
        }

        Message.Builder messageBuilder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(chatRoom.getRoomName())
                        .setBody(body)
                        .build())
                .putData("messageType", messageType)
                .putData("senderName", messageDTO.getSenderName())
                .putData("senderImage", messageDTO.getSenderImage())
                .putData("roomName", chatRoom.getRoomName());

        // 이미지/비디오의 경우 이미지 URL 리스트를 JSON 문자열로 변환해 전달
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

    public void sendPostNotification(Long userId, PostDTO postDTO) {
    User receiver = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("알림 받을 사용자를 찾을 수 없습니다."));
    User author = userRepository.findById(postDTO.getAuthorId())
            .orElseThrow(() -> new RuntimeException("작성자를 찾을 수 없습니다."));
    Category category = categoryRepository.findById(postDTO.getCategoryId())
            .orElseThrow(() -> new RuntimeException("카테고리를 찾을 수 없습니다."));
    Optional<FcmToken> fcmTokenOpt = fcmTokenRepository.findByUser(receiver);
    if (fcmTokenOpt.isEmpty()) return;

    String token = fcmTokenOpt.get().getToken();

    // 이미지 첫 번째 (있으면)
    String firstImageUrl = (postDTO.getImageUrls() != null && !postDTO.getImageUrls().isEmpty())
            ? postDTO.getImageUrls().get(0)
            : null;

    String title = "새 게시물 알림";
    String contentPreview = trimContent(postDTO.getContent());
    String body = safe(author.getName()) + "님이 \"" + safe(category.getTitle()) + "\"에 \"" +
            safe(contentPreview) + "\" 글을 작성했습니다.";

    // 데이터 페이로드용 안전 문자열
    String authorName = safe(author.getName());
    String authorImage = safe(author.getImage()); // null → ""
    String categoryTitle = safe(category.getTitle());
    String preview = safe(contentPreview);

    Message.Builder messageBuilder = Message.builder()
            .setToken(token)
            // 보이는 알림 (양 플랫폼 공통)
            .setNotification(Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build())
            // 앱 라우팅/딥링크 등에 쓰는 데이터 페이로드
            .putData("notificationType", "POST")
            .putData("authorName", authorName)
            .putData("authorImage", authorImage)
            .putData("categoryTitle", categoryTitle)
            .putData("contentPreview", preview);

    if (firstImageUrl != null) {
        messageBuilder.putData("firstImageUrl", firstImageUrl);
    }
    // (선택) 포스트 딥링크/식별자 전달
    if (postDTO.getPostId() != null) {
        messageBuilder.putData("postId", postDTO.getPostId().toString());
        messageBuilder.putData("deeplink", "/posts/" + postDTO.getPostId()); // 앱에서 해석
    }

    // ---------- iOS (APNs) ----------
    ApnsConfig apnsConfig = ApnsConfig.builder()
            .putHeader("apns-push-type", "alert")   // iOS 13+ 필수
            .putHeader("apns-priority", "10")       // 즉시 표시
            // .putHeader("apns-topic", "com.your.bundleid") // 보통 FCM이 설정하지만 필요시 수동 지정
            .setAps(Aps.builder()
                    .setAlert(ApsAlert.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setSound("default")
                    .setContentAvailable(true)      // 백그라운드 fetch 허용
                    // .setMutableContent(true)     // 리치 알림(이미지 변환/확장) 필요 시
                    .build())
            .build();
    messageBuilder.setApnsConfig(apnsConfig);

    // ---------- Android ----------
    AndroidConfig androidConfig = AndroidConfig.builder()
            .setPriority(AndroidConfig.Priority.HIGH) // 백그라운드 지연 최소화
            .setNotification(AndroidNotification.builder()
                    .setChannelId("default_post_channel") // 앱에서 채널 사전 생성 필요
                    // .setClickAction("OPEN_POST")       // 인텐트 액션 사용 시
                    // .setImage(firstImageUrl)           // 필요 시 시스템 알림에 썸네일
                    .build())
            // .setCollapseKey("post-" + postDTO.getPostId()) // 중복 알림 병합 필요 시
            .build();
    messageBuilder.setAndroidConfig(androidConfig);

    try {
        FirebaseMessaging.getInstance().send(messageBuilder.build());
    } catch (FirebaseMessagingException e) {
        e.printStackTrace(); // TODO: 로깅 시스템 연동
    }
}

    private static String safe(String v) { return v == null ? "" : v; }

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

        Message.Builder messageBuilder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putData("notificationType", "COMMENT")
                .putData("authorName", author.getName())
                .putData("authorImage", author.getImage())
                .putData("categoryTitle", category.getTitle())
                .putData("contentPreview", trimContent(commentDTO.getContent()));

        if (firstImageUrl != null) {
            messageBuilder.putData("firstImageUrl", firstImageUrl);
        }

        try {
            FirebaseMessaging.getInstance().send(messageBuilder.build());
        } catch (FirebaseMessagingException e) {
            e.printStackTrace(); // TODO: 로깅 시스템 연동
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

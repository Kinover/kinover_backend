package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.ChatRoomDTO;
import com.example.kinover_backend.dto.ChatRoomMapper;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.entity.*;
import com.example.kinover_backend.enums.ChatBotPersonality;
import com.example.kinover_backend.enums.KinoType;
import com.example.kinover_backend.enums.MessageType;
import com.example.kinover_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final FamilyRepository familyRepository;
    private final UserChatRoomRepository userChatRoomRepository;
    private final ChatRoomNotificationRepository chatRoomNotificationRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final S3Service s3Service;

    @Value("${cloudfront.domain}")
    private String cloudFrontDomain;

    @Autowired
    private ChatRoomMapper chatRoomMapper;

    // =========================
    // 채팅방 생성
    // =========================
    @Transactional
    public ChatRoomDTO createChatRoom(UUID familyId, Long creatorId, String roomName, List<Long> userIds) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new RuntimeException("Family not found"));

        // creator 포함한 전체 유저 목록 확정
        List<Long> allUserIds = new ArrayList<>(userIds);
        if (!allUserIds.contains(creatorId)) {
            allUserIds.add(creatorId);
        }

        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setRoomName(roomName); // ✅ roomName 반영 (Initial 하드코딩 제거)
        chatRoom.setFamily(family);

        // 참여 인원 기준: 2명=personal, 3명 이상=family (원하는 기준이면 여기서 변경)
        chatRoom.setFamilyType(allUserIds.size() > 2 ? "family" : "personal");

        chatRoomRepository.save(chatRoom);

        for (Long userId : allUserIds) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다: " + userId));

            UserChatRoom userChatRoom = new UserChatRoom();
            userChatRoom.setUser(user);
            userChatRoom.setChatRoom(chatRoom);
            userChatRoomRepository.save(userChatRoom);
        }

        return chatRoomMapper.toDTO(chatRoom);
    }

    // ✅ WebSocket 권한 체크용: 해당 유저가 채팅방 멤버인지 확인
    public boolean isMember(UUID chatRoomId, Long userId) {
        if (chatRoomId == null || userId == null)
            return false;

        // 가장 안전한 방식: userChatRoom 테이블에서 존재 여부 확인
        // (레포지토리에 exists 메서드가 있으면 그걸 쓰는게 제일 빠름)
        return userChatRoomRepository.findByUserId(userId).stream()
                .anyMatch(ucr -> ucr.getChatRoom() != null
                        && chatRoomId.equals(ucr.getChatRoom().getChatRoomId()));
    }

    // =========================
    // 채팅방에 유저 추가
    // =========================
    @Transactional
    public ChatRoomDTO addUsersToChatRoom(UUID chatRoomId, List<Long> userIds, Long requesterId) {
        ChatRoom chatRoom = chatRoomRepository.findByChatRoomId(chatRoomId);
        if (chatRoom == null) {
            throw new RuntimeException("채팅방을 찾을 수 없습니다: " + chatRoomId);
        }

        boolean isRequesterInChat = userChatRoomRepository.findByUserId(requesterId).stream()
                .anyMatch(ucr -> ucr.getChatRoom().getChatRoomId().equals(chatRoomId));
        if (!isRequesterInChat) {
            throw new RuntimeException("요청자가 채팅방에 속해 있지 않습니다");
        }

        if (chatRoom.isKino()) {
            throw new IllegalStateException("Kino 채팅방에는 유저를 추가할 수 없습니다");
        }

        List<Long> existingUserIds = userChatRoomRepository.findUsersByChatRoomId(chatRoomId).stream()
                .map(User::getUserId)
                .collect(Collectors.toList());

        List<Long> newUserIds = userIds.stream()
                .filter(userId -> !existingUserIds.contains(userId))
                .collect(Collectors.toList());

        for (Long userId : newUserIds) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다: " + userId));

            UserChatRoom userChatRoom = new UserChatRoom();
            userChatRoom.setUser(user);
            userChatRoom.setChatRoom(chatRoom);
            userChatRoomRepository.save(userChatRoom);
        }

        // 인원수로 familyType 재설정
        int memberCount = userChatRoomRepository.countByChatRoom(chatRoom);
        chatRoom.setFamilyType(memberCount > 2 ? "family" : "personal");
        chatRoomRepository.save(chatRoom);

        return chatRoomMapper.toDTO(chatRoom);
    }

    // =========================
    // 특정 유저가 가진 채팅방 조회
    // (컨트롤러 주석대로 familyId는 사용하지 않음)
    // =========================
    public List<ChatRoomDTO> getAllChatRooms(Long userId, UUID familyId /* 사용되지 않음 */) {
        // 1) 유저가 속한 모든 채팅방 ID 조회
        List<UserChatRoom> userChatRooms = userChatRoomRepository.findByUserId(userId);
        Set<UUID> chatRoomIds = userChatRooms.stream()
                .map(UserChatRoom::getChatRoom)
                .map(ChatRoom::getChatRoomId)
                .collect(Collectors.toSet());

        // 2) 채팅방 엔티티들 조회
        List<ChatRoom> chatRooms = chatRoomRepository.findByChatRoomIdIn(chatRoomIds);

        return chatRooms.stream().map(chatRoom -> {
            ChatRoomDTO dto = chatRoomMapper.toDTO(chatRoom);

            // 최신 메시지
            messageRepository.findTopByChatRoom_ChatRoomIdOrderByCreatedAtDesc(chatRoom.getChatRoomId())
                    .ifPresent(message -> {
                        if (message.getMessageType() == MessageType.text) {
                            dto.setLatestMessageContent(message.getContent());
                        } else if (message.getMessageType() == MessageType.image) {
                            int count = (message.getContent() == null || message.getContent().isBlank())
                                    ? 0
                                    : message.getContent().split(",").length;
                            dto.setLatestMessageContent("사진을 " + count + "장 보냈습니다.");
                        } else if (message.getMessageType() == MessageType.video) {
                            dto.setLatestMessageContent("동영상을 보냈습니다.");
                        }
                        dto.setLatestMessageTime(message.getCreatedAt());
                    });

            // 멤버 이미지
            List<String> images;
            if (isKinoRoom(chatRoom.getChatRoomId())) {
                String suffix;
                ChatBotPersonality personality = chatRoom.getPersonality();
                if (personality == ChatBotPersonality.SERENE) {
                    suffix = "blueKino.png";
                } else if (personality == ChatBotPersonality.SNUGGLE) {
                    suffix = "pinkKino.png";
                } else {
                    suffix = "yellowKino.png";
                }
                images = List.of(cloudFrontDomain + suffix);
            } else {
                images = userChatRoomRepository.findUsersByChatRoomId(chatRoom.getChatRoomId()).stream()
                        .filter(user -> !user.getUserId().equals(userId)) // 자기 자신 제외
                        .map(User::getImage)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
            dto.setMemberImages(images);

            // 채팅방 알림 설정 (기본 true)
            boolean isNotificationOn = chatRoomNotificationRepository
                    .findByUser_UserIdAndChatRoom_ChatRoomId(userId, chatRoom.getChatRoomId())
                    .map(ChatRoomNotificationSetting::isNotificationOn)
                    .orElse(true);

            dto.setNotificationOn(isNotificationOn);

            return dto;
        }).collect(Collectors.toList());
    }

    // =========================
    // 특정 채팅방의 유저 조회
    // =========================
    public List<UserDTO> getUsersByChatRoom(UUID chatRoomId) {
        return userChatRoomRepository.findUsersByChatRoomId(chatRoomId).stream()
                .map(UserDTO::new)
                .collect(Collectors.toList());
    }

    public boolean isKinoRoom(UUID chatRoomId) {
        return chatRoomRepository.findById(chatRoomId)
                .map(ChatRoom::isKino)
                .orElse(false);
    }

    // =========================
    // 채팅방 이름 변경
    // =========================
    @Transactional
    public void renameChatRoom(UUID chatRoomId, String newRoomName, Long userId) {
        ChatRoom chatRoom = chatRoomRepository.findByChatRoomId(chatRoomId);
        if (chatRoom == null) {
            throw new RuntimeException("채팅방을 찾을 수 없습니다: " + chatRoomId);
        }

        boolean isParticipant = userChatRoomRepository.findByUserId(userId).stream()
                .anyMatch(ucr -> ucr.getChatRoom().getChatRoomId().equals(chatRoomId));
        if (!isParticipant) {
            throw new RuntimeException("해당 유저는 이 채팅방에 속해 있지 않습니다.");
        }

        chatRoom.setRoomName(newRoomName);
        chatRoomRepository.save(chatRoom);
    }

    // =========================
    // 채팅방 나가기 (마지막이면 메시지/채팅방/S3 삭제)
    // =========================
    @Transactional
    public void leaveChatRoom(UUID chatRoomId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자 정보를 찾을 수 없습니다."));

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다."));

        userChatRoomRepository.deleteByUserAndChatRoom(user, chatRoom);

        int remainingUsers = userChatRoomRepository.countByChatRoom(chatRoom);

        if (remainingUsers == 0) {
            List<Message> messages = messageRepository.findAllByChatRoomId(chatRoomId);

            List<String> s3KeysToDelete = new ArrayList<>();

            for (Message message : messages) {
                MessageType type = message.getMessageType();
                if (type == MessageType.image || type == MessageType.video) {
                    String content = message.getContent();
                    if (content != null && !content.isBlank()) {
                        List<String> s3Keys = Arrays.stream(content.split(","))
                                .map(String::trim)
                                .filter(url -> url.startsWith(cloudFrontDomain))
                                .map(url -> url.substring(cloudFrontDomain.length()))
                                .collect(Collectors.toList());
                        s3KeysToDelete.addAll(s3Keys);
                    }
                }
            }

            messageRepository.deleteAll(messages);
            chatRoomRepository.delete(chatRoom);

            for (String s3Key : s3KeysToDelete) {
                s3Service.deleteImageFromS3(s3Key);
            }
        }
    }

    // =========================
    // Kino 채팅방 퍼스널리티 변경 (메시지 초기화 포함)
    // =========================
    @Transactional
    public boolean updateChatBotPersonality(UUID chatRoomId, ChatBotPersonality personality) {
        Optional<ChatRoom> optionalChatRoom = chatRoomRepository.findById(chatRoomId);
        if (optionalChatRoom.isEmpty())
            return false;

        ChatRoom chatRoom = optionalChatRoom.get();
        if (!Boolean.TRUE.equals(chatRoom.isKino()))
            return false;

        messageRepository.deleteByChatRoom(chatRoom);

        chatRoom.setPersonality(personality);
        chatRoom.setKinoType(mapPersonalityToKinoType(personality));

        chatRoomRepository.save(chatRoom);
        return true;
    }

    // =========================
    // 특정 채팅방 알림 설정
    // - 유저 전체 채팅 알림이 true일 때만 유효
    // =========================
    @Transactional
    public boolean updateChatRoomNotificationSetting(Long userId, UUID chatRoomId, boolean isOn) {
        Optional<User> userOpt = userRepository.findById(userId);
        Optional<ChatRoom> chatRoomOpt = chatRoomRepository.findById(chatRoomId);

        if (userOpt.isEmpty() || chatRoomOpt.isEmpty())
            return false;

        User user = userOpt.get();
        ChatRoom chatRoom = chatRoomOpt.get();

        // ✅ User 엔티티 필드명이 Boolean isChatNotificationOn 이므로 getter는
        // getIsChatNotificationOn()
        if (!Boolean.TRUE.equals(user.getIsChatNotificationOn())) {
            return false;
        }

        ChatRoomNotificationSetting setting = chatRoomNotificationRepository
                .findByUserAndChatRoom(user, chatRoom)
                .orElseGet(() -> {
                    ChatRoomNotificationSetting s = new ChatRoomNotificationSetting();
                    s.setUser(user);
                    s.setChatRoom(chatRoom);
                    return s;
                });

        setting.setNotificationOn(isOn);
        chatRoomNotificationRepository.save(setting);

        return true;
    }

    private KinoType mapPersonalityToKinoType(ChatBotPersonality personality) {
        return switch (personality) {
            case SUNNY -> KinoType.YELLOW_KINO;
            case SERENE -> KinoType.BLUE_KINO;
            case SNUGGLE -> KinoType.PINK_KINO;
        };
    }
}

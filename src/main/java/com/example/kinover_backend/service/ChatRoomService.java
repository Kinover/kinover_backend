// src/main/java/com/example/kinover_backend/service/ChatRoomService.java
package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.*;
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

import java.time.LocalDateTime;
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

        List<Long> allUserIds = new ArrayList<>(userIds);
        if (!allUserIds.contains(creatorId)) allUserIds.add(creatorId);

        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setRoomName(roomName); // (전역 이름) - 프론트 표시는 displayRoomName 우선
        chatRoom.setFamily(family);
        chatRoom.setFamilyType(allUserIds.size() > 2 ? "family" : "personal");

        chatRoomRepository.save(chatRoom);

        for (Long userId : allUserIds) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다: " + userId));

            UserChatRoom ucr = new UserChatRoom();
            ucr.setUser(user);
            ucr.setChatRoom(chatRoom);

            // ✅ 신규 생성/초대 정책: 초대 시점 이전 메시지는 읽음 처리
            ucr.setLastReadAt(LocalDateTime.now());

            // ✅ 유저별 이름은 생성 후 한 번에 세팅 (아래 initDisplayRoomNamesAfterCreate에서)
            // ucr.setDisplayRoomName(null);
            // ucr.setCustomRoomName(false);

            userChatRoomRepository.save(ucr);
        }

        // ✅ 생성 직후: 유저별 기본 표시 이름 세팅 (나 제외한 멤버 이름 나열)
        List<UserDTO> members = getUsersByChatRoom(chatRoom.getChatRoomId());
        initDisplayRoomNamesAfterCreate(chatRoom.getChatRoomId(), members);

        ChatRoomDTO dto = chatRoomMapper.toDTO(chatRoom);

        // ✅ 생성자(요청자) 기준으로 roomName을 displayRoomName으로 치환해서 내려줌
        applyDisplayRoomName(dto, creatorId);

        return dto;
    }

    // =========================
    // 멤버 체크
    // =========================
    public boolean isMember(UUID chatRoomId, Long userId) {
        if (chatRoomId == null || userId == null) return false;
        return userChatRoomRepository.existsByUser_UserIdAndChatRoom_ChatRoomId(userId, chatRoomId);
    }

    // =========================
    // ✅ 단건 조회 (푸시/딥링크 진입용)
    // =========================
    @Transactional(readOnly = true)
    public ChatRoomDTO getChatRoom(UUID chatRoomId, Long userId) {
        if (chatRoomId == null || userId == null) {
            throw new IllegalArgumentException("chatRoomId/userId는 필수입니다.");
        }
        if (!isMember(chatRoomId, userId)) {
            throw new RuntimeException("해당 채팅방 멤버가 아닙니다.");
        }

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다: " + chatRoomId));

        ChatRoomDTO dto = chatRoomMapper.toDTO(chatRoom);

        // ✅ 최신 메시지
        messageRepository.findTopByChatRoom_ChatRoomIdOrderByCreatedAtDesc(chatRoomId)
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
                    } else {
                        dto.setLatestMessageContent("");
                    }
                    dto.setLatestMessageTime(message.getCreatedAt());
                });

        // ✅ 멤버 이미지 (LAZY 안전하게 Repository 통해 users 조회)
        List<String> images;
        if (isKinoRoom(chatRoomId)) {
            String suffix;
            ChatBotPersonality personality = chatRoom.getPersonality();
            if (personality == ChatBotPersonality.SERENE) suffix = "blueKino.png";
            else if (personality == ChatBotPersonality.SNUGGLE) suffix = "pinkKino.png";
            else suffix = "yellowKino.png";

            images = List.of(cloudFrontDomain + suffix);
        } else {
            images = userChatRoomRepository.findUsersByChatRoomId(chatRoomId).stream()
                    .filter(u -> !u.getUserId().equals(userId))
                    .map(User::getImage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        dto.setMemberImages(images);

        // ✅ 알림 설정
        boolean isNotificationOn = chatRoomNotificationRepository
                .findByUser_UserIdAndChatRoom_ChatRoomId(userId, chatRoomId)
                .map(ChatRoomNotificationSetting::isNotificationOn)
                .orElse(true);
        dto.setNotificationOn(isNotificationOn);

        // ✅ unreadCount
        LocalDateTime lastReadAt = userChatRoomRepository
                .findByUser_UserIdAndChatRoom_ChatRoomId(userId, chatRoomId)
                .map(UserChatRoom::getLastReadAt)
                .orElse(null);

        int unread;
        if (lastReadAt == null) {
            unread = messageRepository.countByChatRoom_ChatRoomIdAndSender_UserIdNot(chatRoomId, userId);
        } else {
            unread = messageRepository.countByChatRoom_ChatRoomIdAndCreatedAtAfterAndSender_UserIdNot(
                    chatRoomId, lastReadAt, userId
            );
        }
        dto.setUnreadCount(Math.max(unread, 0));

        // ✅ 유저별 표시 이름 적용 (roomName을 displayRoomName으로 치환)
        applyDisplayRoomName(dto, userId);

        return dto;
    }

    // =========================
    // WS 브로드캐스트용: 방 멤버 ID 리스트
    // =========================
    @Transactional(readOnly = true)
    public List<Long> getMemberIds(UUID chatRoomId) {
        return userChatRoomRepository.findMemberIdsByChatRoomId(chatRoomId);
    }

    // =========================
    // ✅ 특정 유저의 lastReadAt 가져오기 (푸시 스킵 등에 사용)
    // =========================
    @Transactional(readOnly = true)
    public LocalDateTime getLastReadAt(UUID chatRoomId, Long userId) {
        return userChatRoomRepository
                .findByUser_UserIdAndChatRoom_ChatRoomId(userId, chatRoomId)
                .map(UserChatRoom::getLastReadAt)
                .orElse(null);
    }

    // =========================
    // 읽음 처리 (역행 방지 max)
    // =========================
    @Transactional
    public boolean markRead(UUID chatRoomId, Long userId, LocalDateTime lastReadAt) {
        if (chatRoomId == null || userId == null || lastReadAt == null) {
            throw new IllegalArgumentException("chatRoomId/userId/lastReadAt는 필수입니다.");
        }
        if (!isMember(chatRoomId, userId)) {
            throw new RuntimeException("해당 채팅방 멤버가 아닙니다.");
        }

        int updated = userChatRoomRepository.updateLastReadAtIfLater(chatRoomId, userId, lastReadAt);

        if (updated == 0) {
            userChatRoomRepository.findByUser_UserIdAndChatRoom_ChatRoomId(userId, chatRoomId)
                    .orElseThrow(() -> new RuntimeException("읽음 처리 대상 row 없음"));
            return false;
        }

        return true;
    }

    // =========================
    // readPointers 조회
    // =========================
    @Transactional(readOnly = true)
    public ReadPointersResponseDTO getReadPointers(UUID chatRoomId) {
        List<UserChatRoom> list = userChatRoomRepository.findByChatRoomIdWithUser(chatRoomId);

        List<ReadPointersResponseDTO.Pointer> pointers = list.stream()
                .map(ucr -> new ReadPointersResponseDTO.Pointer(
                        ucr.getUser().getUserId(),
                        ucr.getLastReadAt()))
                .collect(Collectors.toList());

        return new ReadPointersResponseDTO(chatRoomId, pointers);
    }

    // =========================
    // 채팅방에 유저 추가
    // =========================
    @Transactional
    public ChatRoomDTO addUsersToChatRoom(UUID chatRoomId, List<Long> userIds, Long requesterId) {
        ChatRoom chatRoom = chatRoomRepository.findByChatRoomId(chatRoomId);
        if (chatRoom == null)
            throw new RuntimeException("채팅방을 찾을 수 없습니다: " + chatRoomId);

        if (!isMember(chatRoomId, requesterId)) {
            throw new RuntimeException("요청자가 채팅방에 속해 있지 않습니다");
        }

        if (chatRoom.isKino()) {
            throw new IllegalStateException("Kino 채팅방에는 유저를 추가할 수 없습니다");
        }

        List<Long> existingUserIds = userChatRoomRepository.findUsersByChatRoomId(chatRoomId).stream()
                .map(User::getUserId)
                .collect(Collectors.toList());

        List<Long> newUserIds = userIds.stream()
                .filter(id -> !existingUserIds.contains(id))
                .collect(Collectors.toList());

        for (Long userId : newUserIds) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다: " + userId));

            UserChatRoom ucr = new UserChatRoom();
            ucr.setUser(user);
            ucr.setChatRoom(chatRoom);

            // ✅ 신규 초대 정책: 초대 시점 이전 메시지는 읽음 처리
            ucr.setLastReadAt(LocalDateTime.now());

            userChatRoomRepository.save(ucr);
        }

        int memberCount = userChatRoomRepository.countByChatRoom(chatRoom);
        chatRoom.setFamilyType(memberCount > 2 ? "family" : "personal");
        chatRoomRepository.save(chatRoom);

        // ✅ 멤버 변경 이후: "커스텀 이름이 아닌 사람들"의 기본 표시 이름을 최신 멤버 기준으로 갱신
        // (커스텀으로 바꾼 사람은 그대로 유지)
        refreshDisplayRoomNamesIfNotCustom(chatRoomId);

        ChatRoomDTO dto = chatRoomMapper.toDTO(chatRoom);

        // ✅ 요청자 기준 displayName 적용해서 내려줌
        applyDisplayRoomName(dto, requesterId);

        return dto;
    }

    // =========================
    // ✅ 특정 유저가 가진 채팅방 조회 (+ unreadCount)
    // - 2-step으로 멤버 fetch join 해서 N+1 방지
    // =========================
    @Transactional(readOnly = true)
    public List<ChatRoomDTO> getAllChatRooms(Long userId, UUID familyId) {

        Set<UUID> chatRoomIds = chatRoomRepository.findChatRoomIdsByUserAndFamily(userId, familyId);
        if (chatRoomIds == null || chatRoomIds.isEmpty()) return List.of();

        // ✅ 방 + 멤버 + 유저까지 한 번에 fetch
        List<ChatRoom> chatRooms = chatRoomRepository.findByChatRoomIdInWithMembers(chatRoomIds);

        return chatRooms.stream().map(chatRoom -> {
            ChatRoomDTO dto = chatRoomMapper.toDTO(chatRoom);

            // ✅ 최신 메시지
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

            // ✅ 멤버 이미지
            List<String> images;
            if (isKinoRoom(chatRoom.getChatRoomId())) {
                String suffix;
                ChatBotPersonality personality = chatRoom.getPersonality();
                if (personality == ChatBotPersonality.SERENE) suffix = "blueKino.png";
                else if (personality == ChatBotPersonality.SNUGGLE) suffix = "pinkKino.png";
                else suffix = "yellowKino.png";

                images = List.of(cloudFrontDomain + suffix);
            } else {
                // ✅ 이제 chatRoom.userChatRooms + user가 fetch 되어 있어 LAZY 안정
                images = chatRoom.getUserChatRooms().stream()
                        .map(UserChatRoom::getUser)
                        .filter(u -> !u.getUserId().equals(userId))
                        .map(User::getImage)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
            dto.setMemberImages(images);

            // ✅ 알림 설정
            boolean isNotificationOn = chatRoomNotificationRepository
                    .findByUser_UserIdAndChatRoom_ChatRoomId(userId, chatRoom.getChatRoomId())
                    .map(ChatRoomNotificationSetting::isNotificationOn)
                    .orElse(true);
            dto.setNotificationOn(isNotificationOn);

            // ✅ unreadCount
            LocalDateTime lastReadAt = userChatRoomRepository
                    .findByUser_UserIdAndChatRoom_ChatRoomId(userId, chatRoom.getChatRoomId())
                    .map(UserChatRoom::getLastReadAt)
                    .orElse(null);

            int unread;
            if (lastReadAt == null) {
                unread = messageRepository.countByChatRoom_ChatRoomIdAndSender_UserIdNot(
                        chatRoom.getChatRoomId(), userId);
            } else {
                unread = messageRepository.countByChatRoom_ChatRoomIdAndCreatedAtAfterAndSender_UserIdNot(
                        chatRoom.getChatRoomId(), lastReadAt, userId);
            }
            dto.setUnreadCount(Math.max(unread, 0));

            // ✅ 유저별 표시 이름 적용 (roomName을 displayRoomName으로 치환)
            applyDisplayRoomName(dto, userId);

            return dto;
        }).collect(Collectors.toList());
    }

    // =========================
    // 특정 채팅방의 유저 조회
    // =========================
    @Transactional(readOnly = true)
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
    // 채팅방 이름 변경 (전역)
    // =========================
    @Transactional
    public void renameChatRoom(UUID chatRoomId, String newRoomName, Long userId) {
        ChatRoom chatRoom = chatRoomRepository.findByChatRoomId(chatRoomId);
        if (chatRoom == null)
            throw new RuntimeException("채팅방을 찾을 수 없습니다: " + chatRoomId);

        if (!isMember(chatRoomId, userId)) {
            throw new RuntimeException("해당 유저는 이 채팅방에 속해 있지 않습니다.");
        }

        chatRoom.setRoomName(newRoomName);
        chatRoomRepository.save(chatRoom);
    }

    // =========================
    // ✅ 채팅방 이름 변경 (나만)
    // =========================
    @Transactional
    public void renameChatRoomForUser(UUID chatRoomId, Long userId, String newName) {
        UserChatRoom ucr = userChatRoomRepository.findByChatRoomIdAndUserId(chatRoomId, userId)
                .orElseThrow(() -> new RuntimeException("UserChatRoom not found"));

        ucr.setDisplayRoomName(newName);
        ucr.setCustomRoomName(true);
        userChatRoomRepository.save(ucr);
    }

    // =========================
    // 채팅방 나가기
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
        } else {
            // ✅ 남은 멤버가 있고, 커스텀 이름이 아닌 사람들은 기본 표시 이름을 재계산해서 최신화
            refreshDisplayRoomNamesIfNotCustom(chatRoomId);
        }
    }

    // =========================
    // Kino 채팅방 퍼스널리티 변경
    // =========================
    @Transactional
    public boolean updateChatBotPersonality(UUID chatRoomId, ChatBotPersonality personality) {
        Optional<ChatRoom> optionalChatRoom = chatRoomRepository.findById(chatRoomId);
        if (optionalChatRoom.isEmpty()) return false;

        ChatRoom chatRoom = optionalChatRoom.get();
        if (!Boolean.TRUE.equals(chatRoom.isKino())) return false;

        messageRepository.deleteByChatRoom(chatRoom);

        chatRoom.setPersonality(personality);
        chatRoom.setKinoType(mapPersonalityToKinoType(personality));

        chatRoomRepository.save(chatRoom);
        return true;
    }

    // =========================
    // 특정 채팅방 알림 설정
    // =========================
    @Transactional
    public boolean updateChatRoomNotificationSetting(Long userId, UUID chatRoomId, boolean isOn) {
        Optional<User> userOpt = userRepository.findById(userId);
        Optional<ChatRoom> chatRoomOpt = chatRoomRepository.findById(chatRoomId);

        if (userOpt.isEmpty() || chatRoomOpt.isEmpty()) return false;

        User user = userOpt.get();
        ChatRoom chatRoom = chatRoomOpt.get();

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

    // =========================================================
    // ✅ 유저별 채팅방 표시 이름 로직 (핵심)
    // =========================================================

    private String buildDefaultDisplayNameForUser(Long meUserId, List<UserDTO> members) {
        List<UserDTO> sorted = new ArrayList<>(members);
        sorted.sort(Comparator.comparing(UserDTO::getUserId));

        String name = sorted.stream()
                .filter(u -> u.getUserId() != null && !u.getUserId().equals(meUserId))
                .map(UserDTO::getName) // 프로젝트 UserDTO에 getName() 존재 가정
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.joining(", "));

        if (name == null || name.isBlank()) return "채팅방";
        return name;
    }

    /**
     * ✅ 채팅방 생성 직후 1회 호출:
     * - 각 유저의 displayRoomName = "나 제외 멤버 이름 나열"
     */
    private void initDisplayRoomNamesAfterCreate(UUID chatRoomId, List<UserDTO> members) {
        for (UserDTO me : members) {
            Long meId = me.getUserId();
            if (meId == null) continue;

            userChatRoomRepository.findByChatRoomIdAndUserId(chatRoomId, meId).ifPresent(ucr -> {
                // 생성 직후 커스텀은 보통 false지만, 혹시라도 true면 존중
                if (ucr.isCustomRoomName()) return;

                String defaultName = buildDefaultDisplayNameForUser(meId, members);
                ucr.setDisplayRoomName(defaultName);
                ucr.setCustomRoomName(false);
                userChatRoomRepository.save(ucr);
            });
        }
    }

    /**
     * ✅ 멤버 변동(초대/나가기) 이후:
     * - 커스텀 이름이 아닌 사람들만 기본 표시 이름을 최신 멤버 기준으로 갱신
     */
    private void refreshDisplayRoomNamesIfNotCustom(UUID chatRoomId) {
        List<UserDTO> members = getUsersByChatRoom(chatRoomId);

        for (UserDTO me : members) {
            Long meId = me.getUserId();
            if (meId == null) continue;

            userChatRoomRepository.findByChatRoomIdAndUserId(chatRoomId, meId).ifPresent(ucr -> {
                if (ucr.isCustomRoomName()) return; // 커스텀 이름은 유지
                String defaultName = buildDefaultDisplayNameForUser(meId, members);
                ucr.setDisplayRoomName(defaultName);
                ucr.setCustomRoomName(false);
                userChatRoomRepository.save(ucr);
            });
        }
    }

    /**
     * ✅ DTO 만들 때: 요청 userId 기준으로 displayRoomName을 dto.roomName에 넣음
     * - displayRoomName이 null/blank면 즉석 계산값으로 fallback
     */
    private void applyDisplayRoomName(ChatRoomDTO dto, Long requesterUserId) {
        if (dto == null || dto.getChatRoomId() == null || requesterUserId == null) return;

        String display = userChatRoomRepository
                .findByChatRoomIdAndUserId(dto.getChatRoomId(), requesterUserId)
                .map(UserChatRoom::getDisplayRoomName)
                .orElse(null);

        if (display == null || display.isBlank()) {
            List<UserDTO> members = getUsersByChatRoom(dto.getChatRoomId());
            display = buildDefaultDisplayNameForUser(requesterUserId, members);
        }

        dto.setRoomName(display);
    }
}

package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.ChatRoomDTO;
import com.example.kinover_backend.dto.ChatRoomMapper;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.entity.*;
import com.example.kinover_backend.enums.ChatBotPersonality;
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

    // 채팅방 생성 메서드
    @Transactional
    public ChatRoomDTO createChatRoom(UUID familyId, Long creatorId, String roomName, List<Long> userIds) {
        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setRoomName(roomName);
        chatRoom.setFamilyType(userIds.size() > 1 ? "family" : "personal");

        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new RuntimeException("Family not found"));
        chatRoom.setFamily(family);

        chatRoomRepository.save(chatRoom);

        List<Long> allUserIds = new ArrayList<>(userIds);
        if (!allUserIds.contains(creatorId)) {
            allUserIds.add(creatorId);
        }

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

    // 채팅방에 유저 추가
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

        List<User> updatedUsers = userChatRoomRepository.findUsersByChatRoomId(chatRoomId);
        chatRoom.setFamilyType(updatedUsers.size() > 2 ? "family" : "personal");
        chatRoomRepository.save(chatRoom);

        return chatRoomMapper.toDTO(chatRoom);
    }

    // ✅ familyId까지 받아서 해당 가족 채팅방만 필터링
    public List<ChatRoomDTO> getAllChatRooms(Long userId, UUID familyId) {
        // 1) 유저가 속한 모든 채팅방 ID 조회
        List<UserChatRoom> userChatRooms = userChatRoomRepository.findByUserId(userId);
        Set<UUID> chatRoomIds = userChatRooms.stream()
                .map(UserChatRoom::getChatRoom)
                .map(ChatRoom::getChatRoomId)
                .collect(Collectors.toSet());

        // 2) 채팅방 엔티티들 조회
        List<ChatRoom> chatRooms = chatRoomRepository.findByChatRoomIdIn(chatRoomIds);

        // 3) 🔹 familyId로 한 번 더 필터링
        List<ChatRoom> filteredChatRooms = chatRooms.stream()
                .filter(cr -> cr.getFamily() != null) // family가 null 아닌 것만
                .filter(cr -> familyId.equals(cr.getFamily().getFamilyId()))
                .collect(Collectors.toList());

        // 4) DTO 매핑 + 나머지 기존 로직 그대로
        return filteredChatRooms.stream().map(chatRoom -> {
            ChatRoomDTO dto = chatRoomMapper.toDTO(chatRoom);

            // 최신 메시지 추출
            messageRepository.findTopByChatRoom_ChatRoomIdOrderByCreatedAtDesc(chatRoom.getChatRoomId())
                    .ifPresent(message -> {
                        if (message.getMessageType() == MessageType.text) {
                            dto.setLatestMessageContent(message.getContent());
                        } else if (message.getMessageType() == MessageType.image) {
                            int count = message.getContent().split(",").length;
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
                String kinoImageUrl = cloudFrontDomain + suffix;
                images = List.of(kinoImageUrl);
            } else {
                images = userChatRoomRepository.findUsersByChatRoomId(chatRoom.getChatRoomId()).stream()
                        .map(User::getImage)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
            dto.setMemberImages(images);

            // 알림 설정 여부
            boolean isNotificationOn = chatRoomNotificationRepository
                    .findByUser_UserIdAndChatRoom_ChatRoomId(userId, chatRoom.getChatRoomId())
                    .map(ChatRoomNotificationSetting::isNotificationOn)
                    .orElse(true);

            dto.setNotificationOn(isNotificationOn);

            return dto;
        }).collect(Collectors.toList());
    }

    public List<UserDTO> getUsersByChatRoom(UUID chatRoomId) {
        List<User> list = userChatRoomRepository.findUsersByChatRoomId(chatRoomId);
        List<UserDTO> userDTOList = new ArrayList<>();
        for (User user : list) {
            userDTOList.add(new UserDTO(user));
        }
        return userDTOList;
    }

    public boolean isKinoRoom(UUID chatRoomId) {
        return chatRoomRepository.findById(chatRoomId)
                .map(ChatRoom::isKino)
                .orElse(false); // 없으면 false 처리
    }

    @Transactional
    public void renameChatRoom(UUID chatRoomId, String newRoomName, Long userId) {
        ChatRoom chatRoom = chatRoomRepository.findByChatRoomId(chatRoomId);
        if (chatRoom == null) {
            throw new RuntimeException("채팅방을 찾을 수 없습니다: " + chatRoomId);
        }

        // 채팅방에 사용자가 포함되어 있는지 확인
        boolean isParticipant = userChatRoomRepository.findByUserId(userId).stream()
                .anyMatch(ucr -> ucr.getChatRoom().getChatRoomId().equals(chatRoomId));
        if (!isParticipant) {
            throw new RuntimeException("해당 유저는 이 채팅방에 속해 있지 않습니다.");
        }

        chatRoom.setRoomName(newRoomName);
        chatRoomRepository.save(chatRoom);
    }

    @Transactional
    public void leaveChatRoom(UUID chatRoomId, Long userId) {
        // 1. 유저-채팅방 관계 삭제
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자 정보를 찾을 수 없습니다."));

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다."));

        userChatRoomRepository.deleteByUserAndChatRoom(user, chatRoom);

        // 2. 남은 유저 수 확인
        int remainingUsers = userChatRoomRepository.countByChatRoom(chatRoom);

        // 3. 마지막 사용자였다면 메시지와 채팅방 삭제
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

            // DB 삭제
            messageRepository.deleteAll(messages);
            chatRoomRepository.delete(chatRoom);

            // S3 삭제
            for (String s3Key : s3KeysToDelete) {
                s3Service.deleteImageFromS3(s3Key);
            }
        }
    }

    @Transactional
    public boolean updateChatBotPersonality(UUID chatRoomId, ChatBotPersonality personality) {
        Optional<ChatRoom> optionalChatRoom = chatRoomRepository.findById(chatRoomId);
        if (optionalChatRoom.isEmpty()) {
            System.out.println("[updateChatBotPersonality] ChatRoom not found: " + chatRoomId);
            return false;
        }

        ChatRoom chatRoom = optionalChatRoom.get();
        if (!Boolean.TRUE.equals(chatRoom.isKino())) {
            System.out.println("[updateChatBotPersonality] Not a Kino room: " + chatRoomId);
            return false;
        }

        System.out.println("[updateChatBotPersonality] Deleting messages for chatRoomId: " + chatRoomId);
        messageRepository.deleteByChatRoom(chatRoom);

        chatRoom.setPersonality(personality);
        chatRoomRepository.save(chatRoom);
        System.out.println("[updateChatBotPersonality] Personality updated to: " + personality);

        return true;
    }

    @Transactional
    public boolean updateChatRoomNotificationSetting(Long userId, UUID chatRoomId, boolean isOn) {
        Optional<User> userOpt = userRepository.findById(userId);
        Optional<ChatRoom> chatRoomOpt = chatRoomRepository.findById(chatRoomId);

        if (userOpt.isEmpty() || chatRoomOpt.isEmpty())
            return false;

        User user = userOpt.get();
        ChatRoom chatRoom = chatRoomOpt.get();

        Optional<ChatRoomNotificationSetting> settingOpt = chatRoomNotificationRepository.findByUserAndChatRoom(user,
                chatRoom);

        if (settingOpt.isEmpty())
            return false;

        ChatRoomNotificationSetting setting = settingOpt.get();
        setting.setNotificationOn(isOn);
        chatRoomNotificationRepository.save(setting);
        return true;
    }

}
package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.ChatRoomDTO;
import com.example.kinover_backend.dto.ChatRoomMapper;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.entity.ChatRoom;
import com.example.kinover_backend.entity.Message;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.entity.UserChatRoom;
import com.example.kinover_backend.enums.MessageType;
import com.example.kinover_backend.repository.ChatRoomRepository;
import com.example.kinover_backend.repository.MessageRepository;
import com.example.kinover_backend.repository.UserChatRoomRepository;
import com.example.kinover_backend.repository.UserRepository;
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
    private final UserChatRoomRepository userChatRoomRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final S3Service s3Service;

    @Value("${cloudfront.domain}")
    private String cloudFrontDomain;

    @Autowired
    private ChatRoomMapper chatRoomMapper;

    // 채팅방 생성 메서드
    public ChatRoomDTO createChatRoom(Long creatorId, String roomName, List<Long> userIds) {
        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setChatRoomId(UUID.randomUUID());
        chatRoom.setRoomName(roomName);
        chatRoom.setFamilyType(userIds.size() > 1 ? "family" : "personal");
        chatRoom.setFamily(null);

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

    public List<ChatRoomDTO> getAllChatRooms(Long userId) {
        List<UserChatRoom> userChatRooms = userChatRoomRepository.findByUserId(userId);
        Set<UUID> chatRoomIds = userChatRooms.stream()
                .map(UserChatRoom::getChatRoom)
                .map(ChatRoom::getChatRoomId)
                .collect(Collectors.toSet());
        List<ChatRoom> chatRooms = chatRoomRepository.findByChatRoomIdIn(chatRoomIds);
        return chatRooms.stream().map(chatRoom -> {
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

            // 채팅방 멤버 이미지 리스트
            List<String> images = userChatRoomRepository.findUsersByChatRoomId(chatRoom.getChatRoomId()).stream()
                    .map(User::getImage) // 유저 엔티티에 getImage() 메서드 있어야 함
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            dto.setMemberImages(images);

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
                .orElse(false);  // 없으면 false 처리
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


}
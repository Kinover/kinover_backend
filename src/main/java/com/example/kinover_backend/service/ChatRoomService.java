package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.ChatRoomDTO;
import com.example.kinover_backend.dto.ChatRoomMapper;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.entity.ChatRoom;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.entity.UserChatRoom;
import com.example.kinover_backend.repository.ChatRoomRepository;
import com.example.kinover_backend.repository.UserChatRoomRepository;
import com.example.kinover_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final UserChatRoomRepository userChatRoomRepository;
    private final UserRepository userRepository;

    @Autowired
    private ChatRoomMapper chatRoomMapper;

    @Autowired
    public ChatRoomService(ChatRoomRepository chatRoomRepository, UserChatRoomRepository userChatRoomRepository, UserRepository userRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.userChatRoomRepository = userChatRoomRepository;
        this.userRepository = userRepository;
    }

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
        return chatRooms.stream()
                .map(chatRoomMapper::toDTO)
                .collect(Collectors.toList());
    }

    public ChatRoom getChatRooms(UUID chatRoomId) {
        return this.chatRoomRepository.findByChatRoomId(chatRoomId);
    }

    public List<UserDTO> getUsersByChatRoom(UUID chatRoomId) {
        List<User> list = userChatRoomRepository.findUsersByChatRoomId(chatRoomId);
        List<UserDTO> userDTOList = new ArrayList<>();
        for (User user : list) {
            userDTOList.add(new UserDTO(user));
        }
        return userDTOList;
    }
}
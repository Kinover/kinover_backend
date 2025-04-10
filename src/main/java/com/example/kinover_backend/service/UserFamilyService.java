package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.entity.ChatRoom;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.entity.UserChatRoom;
import com.example.kinover_backend.repository.ChatRoomRepository;
import com.example.kinover_backend.repository.UserChatRoomRepository;
import com.example.kinover_backend.repository.UserFamilyRepository;
import com.example.kinover_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserFamilyService {
    private final UserFamilyRepository userFamilyRepository;
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository; // 추가
    private final UserChatRoomRepository userChatRoomRepository; // 추가

    public UserFamilyService(UserFamilyRepository userFamilyRepository,
                             UserRepository userRepository,
                             ChatRoomRepository chatRoomRepository, // 추가
                             UserChatRoomRepository userChatRoomRepository) { // 추가
        this.userFamilyRepository = userFamilyRepository;
        this.userRepository = userRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.userChatRoomRepository = userChatRoomRepository;
    }

    // 특정 familyId에 속한 유저 리스트(UserDTO) 반환
    public List<UserDTO> getUsersByFamilyId(UUID familyId) {
        List<Long> userIds = userFamilyRepository.findUserIdsByFamilyId(familyId);

        if (userIds.isEmpty()) {
            return List.of();
        }

        return userRepository.findUsersByIds(userIds).stream()
                .map(UserDTO::new)
                .collect(Collectors.toList());
    }

    // 특정 family에 속한 특정 유저 삭제 (가족 탈퇴)
    public void deleteUserByFamilyIdAndUserId(UUID familyId, Long userId) {
        userFamilyRepository.deleteUserByFamilyIdAndUserId(familyId, userId);
    }

    // 특정 family에 특정 유저 추가 (가족 추가)
    public void addUserByFamilyIdAndUserId(UUID familyId, Long userId) {
        // 모든 유저가 기본적으로 Kino 채팅방을 가질 수 있게
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다: " + userId));
        User kino = userRepository.findById(9999999999L)
                .orElseThrow(() -> new RuntimeException("Kino 유저를 찾을 수 없습니다"));

        // Kino 채팅방 데이터 설정
        ChatRoom kinoChatRoom = new ChatRoom();
        kinoChatRoom.setChatRoomId(UUID.randomUUID());
        kinoChatRoom.setRoomName("챗봇 키노");
        kinoChatRoom.setIsKino(true);
        kinoChatRoom.setFamilyType("personal");
        kinoChatRoom.setImage(kino.getImage());
        kinoChatRoom.setFamily(null); // 가족과 무관하므로 null
        chatRoomRepository.save(kinoChatRoom);

        // user와 kino를 user_chat_room에 등록
        UserChatRoom userChatRoom = new UserChatRoom();
        userChatRoom.setUser(user);
        userChatRoom.setChatRoom(kinoChatRoom);
        userChatRoomRepository.save(userChatRoom);

        UserChatRoom kinoChatRoomLink = new UserChatRoom();
        kinoChatRoomLink.setUser(kino);
        kinoChatRoomLink.setChatRoom(kinoChatRoom);
        userChatRoomRepository.save(kinoChatRoomLink);
    }
}
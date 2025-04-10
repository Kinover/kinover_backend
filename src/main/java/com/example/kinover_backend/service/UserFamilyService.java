package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.entity.ChatRoom;
import com.example.kinover_backend.entity.Family;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.entity.UserChatRoom;
import com.example.kinover_backend.entity.UserFamily;
import com.example.kinover_backend.repository.ChatRoomRepository;
import com.example.kinover_backend.repository.FamilyRepository;
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
    private final ChatRoomRepository chatRoomRepository;
    private final UserChatRoomRepository userChatRoomRepository;
    private final FamilyRepository familyRepository;

    public UserFamilyService(UserFamilyRepository userFamilyRepository,
                             UserRepository userRepository,
                             ChatRoomRepository chatRoomRepository,
                             UserChatRoomRepository userChatRoomRepository,
                             FamilyRepository familyRepository) {
        this.userFamilyRepository = userFamilyRepository;
        this.userRepository = userRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.userChatRoomRepository = userChatRoomRepository;
        this.familyRepository = familyRepository;
    }

    public List<UserDTO> getUsersByFamilyId(UUID familyId) {
        List<Long> userIds = userFamilyRepository.findUserIdsByFamilyId(familyId);
        if (userIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findUsersByIds(userIds).stream()
                .map(UserDTO::new)
                .collect(Collectors.toList());
    }

    public void deleteUserByFamilyIdAndUserId(UUID familyId, Long userId) {
        userFamilyRepository.deleteUserByFamilyIdAndUserId(familyId, userId);
    }

    public void addUserByFamilyIdAndUserId(UUID familyId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다: " + userId));

        // Family 객체 조회
        Family family = familyRepository.findByFamilyId(familyId);
        if (family == null) {
            throw new RuntimeException("가족을 찾을 수 없습니다: " + familyId);
        }

        UserFamily userFamily = new UserFamily();
        userFamily.setUserFamilyId(UUID.randomUUID());
        userFamily.setRole("member");
        userFamily.setFamily(family);
        userFamily.setUser(user);
        userFamilyRepository.save(userFamily);

        // Kino 채팅방 생성 및 연결
        User kino = userRepository.findById(9999999999L)
                .orElseThrow(() -> new RuntimeException("Kino 유저를 찾을 수 없습니다"));

        ChatRoom kinoChatRoom = new ChatRoom();
        kinoChatRoom.setChatRoomId(UUID.randomUUID());
        kinoChatRoom.setRoomName("챗봇 키노");
        kinoChatRoom.setIsKino(true);
        kinoChatRoom.setFamilyType("personal");
        kinoChatRoom.setImage(kino.getImage());
        kinoChatRoom.setFamily(null);
        chatRoomRepository.save(kinoChatRoom);

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
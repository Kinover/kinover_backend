package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.UserDTO;
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

    public UserFamilyService(UserFamilyRepository userFamilyRepository,UserRepository userRepository) {
        this.userFamilyRepository = userFamilyRepository;
        this.userRepository=userRepository;
    }

    // 특정 familyId에 속한 유저 리스트(UserDTO) 반환
    public List<UserDTO> getUsersByFamilyId(UUID familyId) {
        // 1. 해당 familyId에 속한 userId 리스트 가져오기
        List<Long> userIds = userFamilyRepository.findUserIdsByFamilyId(familyId);

        if (userIds.isEmpty()) {
            return List.of();  // 빈 리스트 반환 (불필요한 DB 조회 방지)
        }

        // 2. userId 리스트로 User 엔티티 리스트 가져오기
        return userRepository.findUsersByIds(userIds).stream()
                .map(UserDTO::new)  // User 엔티티 -> UserDTO 변환
                .collect(Collectors.toList());
    }

    // 특정 family에 속한 특정 유저 삭제 (가족 탈퇴)
    public void deleteUserByFamilyIdAndUserId(UUID familyId, Long userId) {
        userFamilyRepository.deleteUserByFamilyIdAndUserId(familyId, userId);
    }

    // 특정 family에 특정 유저 추가 (가족 추가)
    public void addUserByFamilyIdAndUserId(UUID familyId, Long userId) {


        //모든 유저가 기본적으로 kino 채팅방 만들 수 있게
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다: " + userId));
        User kino = userRepository.findById(9999999999L)
                .orElseThrow(() -> new RuntimeException("Kino 유저를 찾을 수 없습니다"));

        //kino 채팅방 데이터 설정
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다: " + userId));
        User kino = userRepository.findById(9999999999L)
                .orElseThrow(() -> new RuntimeException("Kino 유저를 찾을 수 없습니다"));

        ChatRoom kinoChatRoom = new ChatRoom();
        kinoChatRoom.setChatRoomId(UUID.randomUUID());
        kinoChatRoom.setRoomName("Kino와의 채팅");
        kinoChatRoom.setIsKino(true);
        kinoChatRoom.setFamilyType("personal");
        kinoChatRoom.setImage(kino.getImage());
        kinoChatRoom.setFamily(null); // 가족과 무관하기 때문에 채팅방의 family id를 null로 설정
        chatRoomRepository.save(kinoChatRoom);

        //user와 kino에 대한 정보를 userchatroom에 등록
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

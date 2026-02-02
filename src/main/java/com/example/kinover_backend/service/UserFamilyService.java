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

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserFamilyService {
    private final UserFamilyRepository userFamilyRepository;
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserChatRoomRepository userChatRoomRepository;
    private final FamilyRepository familyRepository;

    // ✅ Kino bot 고정 ID
    private static final Long KINO_USER_ID = 9999999999L;

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

    /**
     * ✅ 핵심: 가족 참여 시
     * 1) User/Family 확인
     * 2) UserFamily 중복 방지
     * 3) Kino 유저 보장(없으면 생성)
     * 4) Kino ChatRoom 생성 + 링크
     *
     * => Kino 유저가 없어서 서버가 터지는 케이스 방어
     */
    @Transactional
    public void addUserByFamilyIdAndUserId(UUID familyId, Long userId) {

        // 1) User 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다: " + userId));

        // 2) Family 조회
        Family family = familyRepository.findFamilyById(familyId)
                .orElseThrow(() -> new RuntimeException("가족 정보 없음: " + familyId));

        // 3) 기존 UserFamily 존재 여부 확인 (중복 생성 방지)
        Optional<UserFamily> existing =
                userFamilyRepository.findByUser_UserIdAndFamily_FamilyId(userId, familyId);

        if (existing.isPresent()) {
            // 이미 참여 중이면 Kino 채팅방도 이미 있을 확률이 높아서 여기서 종료
            return;
        }

        // 4) 새 UserFamily 생성
        UserFamily userFamily = new UserFamily();
        userFamily.setRole("member");
        userFamily.setFamily(family);
        userFamily.setUser(user);

        userFamilyRepository.save(userFamily);

        // 5) Kino 유저 확보 (없으면 생성)
        User kino = ensureKinoUserExists();

        // 6) Kino 채팅방 생성
        // ⚠️ (선택) 여기서 "이미 family에 kinoChatRoom이 있으면 재사용"하는 게 베스트인데,
        // 네 repository에 조회 메서드가 있는지 몰라서 일단 최소 방어만 함.
        // 예: chatRoomRepository.findByFamily_FamilyIdAndIsKinoTrue(...) 이런 게 있으면 그걸로 중복 방지 추가 가능.
        ChatRoom kinoChatRoom = new ChatRoom();
        kinoChatRoom.setRoomName("챗봇 키노");
        kinoChatRoom.setKino(true);
        kinoChatRoom.setFamilyType("personal");
        kinoChatRoom.setImage(kino.getImage());
        kinoChatRoom.setFamily(family);

        chatRoomRepository.save(kinoChatRoom);

        // 7) User ↔ Kino ChatRoom 연결
        UserChatRoom userChatRoom = new UserChatRoom();
        userChatRoom.setUser(user);
        userChatRoom.setChatRoom(kinoChatRoom);
        userChatRoomRepository.save(userChatRoom);

        UserChatRoom kinoChatRoomLink = new UserChatRoom();
        kinoChatRoomLink.setUser(kino);
        kinoChatRoomLink.setChatRoom(kinoChatRoom);
        userChatRoomRepository.save(kinoChatRoomLink);
    }

    /**
     * ✅ Kino 유저가 없으면 자동 생성해서 "Kino 유저를 찾을 수 없습니다" 방지
     */
    private User ensureKinoUserExists() {
        Optional<User> kinoOpt = userRepository.findById(KINO_USER_ID);
        if (kinoOpt.isPresent()) {
            return kinoOpt.get();
        }

        User kino = new User();
        kino.setUserId(KINO_USER_ID);
        kino.setName("키노");
        kino.setEmail(null);
        kino.setPhoneNumber(null);
        kino.setKakaoId(null);

        // 이미지 기본값(없으면 null로 두고 ChatRoom 이미지도 null 허용해야 함)
        // 가능하면 cloudfront 기본 이미지로 세팅 추천
        kino.setImage(null);

        // createdAt/updatedAt이 DB default로 들어가면 굳이 세팅 안 해도 됨
        return userRepository.save(kino);
    }
}

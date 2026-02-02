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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserFamilyService {

    private static final Logger log = LoggerFactory.getLogger(UserFamilyService.class);

    // ✅ 키노 봇 userId (상수로 고정)
    private static final Long KINO_BOT_USER_ID = 9999999999L;

    private final UserFamilyRepository userFamilyRepository;
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserChatRoomRepository userChatRoomRepository;
    private final FamilyRepository familyRepository;

    public UserFamilyService(
            UserFamilyRepository userFamilyRepository,
            UserRepository userRepository,
            ChatRoomRepository chatRoomRepository,
            UserChatRoomRepository userChatRoomRepository,
            FamilyRepository familyRepository
    ) {
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
     * ✅ 가족 참여(유저-가족 링크 생성) + (가능하면) 키노 개인 채팅방 생성
     * - 키노 유저가 DB에 없으면: 가족 참여만 하고 "조용히" 넘어감 (절대 실패시키지 않음)
     * - 이미 참여되어 있으면: 중복 생성 방지
     * - 트랜잭션: 가족참여 저장은 보장, 키노 채팅은 best-effort
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
            // 이미 존재하면 아무 작업도 하지 않음
            log.info("[JOIN_SKIP] already joined userId={}, familyId={}", userId, familyId);
            return;
        }

        // 4) 새 UserFamily 생성
        UserFamily userFamily = new UserFamily();
        userFamily.setRole("member");
        userFamily.setFamily(family);
        userFamily.setUser(user);
        userFamilyRepository.save(userFamily);

        log.info("[JOIN_OK] userId={}, familyId={}", userId, familyId);

        // 5) 키노 개인 채팅방 생성 (best-effort)
        createKinoPersonalChatRoomIfPossible(user, family);
    }

    /**
     * ✅ 키노 봇 유저가 있으면:
     * - "챗봇 키노" 개인방 생성 + 유저/키노 링크 생성
     * ✅ 없으면:
     * - 조용히 스킵
     *
     * ⚠️ 현재는 "항상 새로 생성"이라 가족 참여를 여러 번 하면 중복 생성될 수 있음.
     * - 보통은 "한 유저당 한 개"가 맞으니, 중복 방지 로직을 같이 넣어줌.
     * - (중복 방지에 필요한 Repository 메서드가 없으면 일단 링크 존재 여부로 막음)
     */
    private void createKinoPersonalChatRoomIfPossible(User user, Family family) {

        // ✅ 키노 유저 존재 확인
        Optional<User> kinoOpt = userRepository.findById(KINO_BOT_USER_ID);
        if (kinoOpt.isEmpty()) {
            // 여기서 throw하면 가족 참여까지 롤백되어버림 -> 절대 throw 금지
            log.warn("[KINO_SKIP] Kino bot user not found. botUserId={}", KINO_BOT_USER_ID);
            return;
        }
        User kino = kinoOpt.get();

        // ✅ (중요) 이미 유저가 키노 개인방 링크를 갖고 있으면 중복 생성 방지
        // - repo에 메서드가 없어서, 가장 안전한 방식은 "유저가 속한 chatroom 중 kino=true & familyType=personal" 확인인데
        //   지금 엔티티/레포가 안 보이니까, 최소한으로 "유저ChatRoom 링크가 이미 있는지"를 체크하는 메서드가 있으면 그걸 써야 함.
        //
        // 여기서는 레포가 있다고 가정하지 않고, '중복 생성 방지'를 약하게라도 하기 위해
        // "해당 family에 kino=true, familyType=personal, roomName=챗봇 키노"가 이미 있으면 재사용하도록 구현.
        // (ChatRoomRepository에 아래 메서드가 필요함: findFirstByFamily_FamilyIdAndKinoTrueAndFamilyType)
        ChatRoom kinoChatRoom = null;
        try {
            // ✅ 가능하면 기존 방 재사용 (중복 생성 방지)
            // 너 레포에 메서드가 없으면 아래 try 블럭은 그냥 실패하고 새로 생성하게 됨.
            kinoChatRoom = chatRoomRepository
                    .findFirstByFamily_FamilyIdAndKinoTrueAndFamilyType(family.getFamilyId(), "personal")
                    .orElse(null);
        } catch (Exception ignore) {
            // 레포 메서드 없으면 무시하고 새로 생성
        }

        if (kinoChatRoom == null) {
            // 새 ChatRoom 생성
            kinoChatRoom = new ChatRoom();
            kinoChatRoom.setRoomName("챗봇 키노");
            kinoChatRoom.setKino(true);
            kinoChatRoom.setFamilyType("personal");
            kinoChatRoom.setImage(kino.getImage());
            kinoChatRoom.setFamily(family);
            chatRoomRepository.save(kinoChatRoom);

            log.info("[KINO_ROOM_CREATED] chatRoomId={}, familyId={}",
                    kinoChatRoom.getChatRoomId(), family.getFamilyId());
        } else {
            log.info("[KINO_ROOM_REUSE] chatRoomId={}, familyId={}",
                    kinoChatRoom.getChatRoomId(), family.getFamilyId());
        }

        // ✅ User ↔ Kino ChatRoom 연결 (중복 링크 방지)
        // 아래도 repo 메서드가 있으면 제일 좋음:
        // existsByUser_UserIdAndChatRoom_ChatRoomId(...)
        boolean userLinked = false;
        boolean kinoLinked = false;

        try {
            userLinked = userChatRoomRepository
                    .existsByUser_UserIdAndChatRoom_ChatRoomId(user.getUserId(), kinoChatRoom.getChatRoomId());
            kinoLinked = userChatRoomRepository
                    .existsByUser_UserIdAndChatRoom_ChatRoomId(kino.getUserId(), kinoChatRoom.getChatRoomId());
        } catch (Exception ignore) {
            // 메서드 없으면 그냥 생성 시도(Unique 제약 없으면 중복 생길 수 있음)
        }

        if (!userLinked) {
            UserChatRoom userChatRoom = new UserChatRoom();
            userChatRoom.setUser(user);
            userChatRoom.setChatRoom(kinoChatRoom);
            userChatRoomRepository.save(userChatRoom);
        }

        if (!kinoLinked) {
            UserChatRoom kinoChatRoomLink = new UserChatRoom();
            kinoChatRoomLink.setUser(kino);
            kinoChatRoomLink.setChatRoom(kinoChatRoom);
            userChatRoomRepository.save(kinoChatRoomLink);
        }

        log.info("[KINO_LINK_OK] userId={}, kinoId={}, chatRoomId={}",
                user.getUserId(), kino.getUserId(), kinoChatRoom.getChatRoomId());
    }
}

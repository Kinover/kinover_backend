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

import java.time.LocalDateTime;
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
    private final ChatRoomService chatRoomService;
    private final UserService userService;

    public UserFamilyService(UserFamilyRepository userFamilyRepository,
                             UserRepository userRepository,
                             ChatRoomRepository chatRoomRepository,
                             UserChatRoomRepository userChatRoomRepository,
                             FamilyRepository familyRepository,
                             ChatRoomService chatRoomService,
                             UserService userService) {
        this.userFamilyRepository = userFamilyRepository;
        this.userRepository = userRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.userChatRoomRepository = userChatRoomRepository;
        this.familyRepository = familyRepository;
        this.chatRoomService = chatRoomService;
        this.userService = userService;
    }

    @Transactional
    public List<UserDTO> getUsersByFamilyId(UUID familyId) {
        List<Long> userIds = userFamilyRepository.findUserIdsByFamilyId(familyId);
        if (userIds.isEmpty()) {
            return List.of();
        }
        List<User> users = userRepository.findUsersByIds(userIds);
        for (User u : users) {
            userService.expireStaleEmotionIfNeeded(u);
        }
        return users.stream()
                .map(UserDTO::new)
                .collect(Collectors.toList());
    }

    public void deleteUserByFamilyIdAndUserId(UUID familyId, Long userId) {
        userFamilyRepository.deleteUserByFamilyIdAndUserId(familyId, userId);
    }

    @Transactional
    public void addUserByFamilyIdAndUserId(UUID familyId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Family family = familyRepository.findFamilyById(familyId)
                .orElseThrow(() -> new RuntimeException("Family not found: " + familyId));

        Optional<UserFamily> existing =
                userFamilyRepository.findByUser_UserIdAndFamily_FamilyId(userId, familyId);

        if (existing.isPresent()) {
            return;
        }

        UserFamily userFamily = new UserFamily();
        userFamily.setRole("member");
        userFamily.setFamily(family);
        userFamily.setUser(user);
        userFamilyRepository.save(userFamily);

        User kino = ensureKinoUserExists();

        ChatRoom kinoChatRoom = new ChatRoom();
        kinoChatRoom.setRoomName(KinoBotProfile.KINO_ROOM_NAME);
        kinoChatRoom.setKino(true);
        kinoChatRoom.setPersonality(KinoBotProfile.normalizePersonality(null));
        kinoChatRoom.setKinoType(KinoBotProfile.kinoTypeFor(kinoChatRoom.getPersonality()));
        kinoChatRoom.setFamilyType("personal");
        kinoChatRoom.setImage(kino.getImage());
        kinoChatRoom.setFamily(family);
        chatRoomRepository.save(kinoChatRoom);

        UserChatRoom userChatRoom = new UserChatRoom();
        userChatRoom.setUser(user);
        userChatRoom.setChatRoom(kinoChatRoom);
        userChatRoom.setLastReadAt(LocalDateTime.now());
        userChatRoomRepository.save(userChatRoom);

        UserChatRoom kinoChatRoomLink = new UserChatRoom();
        kinoChatRoomLink.setUser(kino);
        kinoChatRoomLink.setChatRoom(kinoChatRoom);
        kinoChatRoomLink.setLastReadAt(LocalDateTime.now());
        userChatRoomRepository.save(kinoChatRoomLink);

        chatRoomService.sendKinoOpeningMessage(kinoChatRoom.getChatRoomId());
    }

    private User ensureKinoUserExists() {
        Optional<User> kinoOpt = userRepository.findById(KinoBotProfile.KINO_USER_ID);
        if (kinoOpt.isPresent()) {
            return kinoOpt.get();
        }

        User kino = new User();
        kino.setUserId(KinoBotProfile.KINO_USER_ID);
        kino.setName(KinoBotProfile.KINO_USER_NAME);
        kino.setEmail(null);
        kino.setPhoneNumber(null);
        kino.setKakaoId(null);
        kino.setImage(null);

        return userRepository.save(kino);
    }
}

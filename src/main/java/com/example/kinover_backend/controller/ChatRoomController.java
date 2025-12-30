package com.example.kinover_backend.controller;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.ChatRoomDTO;
import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.dto.ReadPointersResponseDTO;
import com.example.kinover_backend.dto.ReadRequestDTO;
import com.example.kinover_backend.dto.UpdatePersonalityRequestDTO;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.service.ChatRoomService;
import com.example.kinover_backend.service.MessageService;
import com.example.kinover_backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "채팅방 Controller", description = "채팅방 관련 API를 제공합니다.")
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/chatRoom")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final MessageService messageService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    public ChatRoomController(ChatRoomService chatRoomService,
                              MessageService messageService,
                              UserService userService,
                              JwtUtil jwtUtil) {
        this.chatRoomService = chatRoomService;
        this.messageService = messageService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    /* =========================
     * ✅ 단건 조회 추가 (푸시/딥링크용 핵심)
     * =========================
     * - 프론트가 chatRoomId만 갖고 진입할 수 있게 해줌
     * - 멤버 검증 필수
     *
     * ✅ ChatRoomService에 아래 메서드가 있어야 함(구현 필요):
     *   ChatRoomDTO getChatRoom(UUID chatRoomId, Long userId);
     */
    @Operation(summary = "채팅방 단건 조회", description = "chatRoomId로 채팅방을 단건 조회합니다. (푸시/딥링크 진입용)")
    @GetMapping("/{chatRoomId}")
    public ResponseEntity<ChatRoomDTO> getChatRoom(
            @Parameter(description = "채팅방 ID", required = true) @PathVariable UUID chatRoomId,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        String token = authorizationHeader.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(token);

        if (!chatRoomService.isMember(chatRoomId, userId)) {
            throw new RuntimeException("해당 채팅방 멤버가 아닙니다.");
        }

        // ✅ 서비스에서 DTO 조립(권장: unreadCount/lastReadAt/roomName/users 등 필요한 값 포함)
        ChatRoomDTO dto = chatRoomService.getChatRoom(chatRoomId, userId);
        return ResponseEntity.ok(dto);
    }

    // 채팅방 생성
    @Operation(summary = "채팅방 생성", description = "새로운 채팅방을 생성합니다. userIds는 쉼표로 구분된 문자열로 전달 (예: 1,2,3)")
    @PostMapping("/create/{roomName}/{userIds}/{familyId}")
    public ResponseEntity<ChatRoomDTO> createChatRoom(
            @Parameter(description = "채팅방 이름", required = true) @PathVariable String roomName,
            @Parameter(description = "참여 유저 ID 리스트 (쉼표로 구분)", required = true) @PathVariable String userIds,
            @Parameter(description = "채팅방 소속된 가족", required = true) @PathVariable UUID familyId,
            @RequestHeader("Authorization") String authorizationHeader) {

        String token = authorizationHeader.replace("Bearer ", "");
        Long authenticatedUserId = jwtUtil.getUserIdFromToken(token);

        List<Long> userIdList = Arrays.stream(userIds.split(","))
                .map(Long::parseLong)
                .collect(Collectors.toList());

        ChatRoomDTO createdChatRoom = chatRoomService.createChatRoom(
                familyId, authenticatedUserId, roomName, userIdList);

        return new ResponseEntity<>(createdChatRoom, HttpStatus.CREATED);
    }

    // 채팅방에 유저 추가
    @Operation(summary = "채팅방에 유저 추가", description = "기존 채팅방에 새로운 유저를 추가합니다. userIds는 쉼표로 구분된 문자열로 전달 (예: 1,2,3)")
    @PostMapping("/{chatRoomId}/addUsers/{userIds}")
    public ResponseEntity<ChatRoomDTO> addUsersToChatRoom(
            @Parameter(description = "채팅방 아이디", required = true) @PathVariable UUID chatRoomId,
            @Parameter(description = "추가할 유저 ID 리스트 (쉼표로 구분)", required = true) @PathVariable String userIds,
            @RequestHeader("Authorization") String authorizationHeader) {

        String token = authorizationHeader.replace("Bearer ", "");
        Long authenticatedUserId = jwtUtil.getUserIdFromToken(token);

        if (userIds == null || userIds.trim().isEmpty()) {
            throw new IllegalArgumentException("추가할 유저 ID 리스트는 비어있을 수 없습니다");
        }

        List<Long> userIdList;
        try {
            userIdList = Arrays.stream(userIds.split(","))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("userIds 형식이 잘못되었습니다. 쉼표로 구분된 숫자여야 합니다.");
        }

        ChatRoomDTO updatedChatRoom = chatRoomService.addUsersToChatRoom(chatRoomId, userIdList, authenticatedUserId);
        return new ResponseEntity<>(updatedChatRoom, HttpStatus.OK);
    }

    // 특정 유저가 가진 채팅방 조회
    @Operation(summary = "채팅방 조회", description = "특정 유저의 모든 채팅방을 조회합니다.")
    @PostMapping("/{familyId}/{userId}")
    public List<ChatRoomDTO> getAllChatRooms(
            @Parameter(description = "가족 아이디 (사용되지 않음)", required = true) @PathVariable UUID familyId,
            @Parameter(description = "유저 아이디", required = true) @PathVariable Long userId,
            @RequestHeader("Authorization") String authorizationHeader) {

        String token = authorizationHeader.replace("Bearer ", "");
        Long authenticatedUserId = jwtUtil.getUserIdFromToken(token);
        if (!authenticatedUserId.equals(userId)) {
            throw new RuntimeException("인증된 유저와 요청 유저가 일치하지 않습니다");
        }

        // ✅ 여기서 unreadCount를 서버 계산해서 DTO에 포함시키는 걸 chatRoomService에서 처리해야 “정확”
        return chatRoomService.getAllChatRooms(userId, familyId);
    }

    // 채팅방의 모든 메시지 조회
    @Operation(summary = "메세지 불러오기", description = "채팅방의 메시지를 가져옵니다.")
    @GetMapping("/{chatRoomId}/messages/fetch")
    public List<MessageDTO> fetchMessages(
            @PathVariable UUID chatRoomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader("Authorization") String authorizationHeader) {

        // ✅ 토큰 검증(최소)
        String token = authorizationHeader.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(token);

        // ✅ 멤버 검증(권장)
        if (!chatRoomService.isMember(chatRoomId, userId)) {
            throw new RuntimeException("해당 채팅방 멤버가 아닙니다.");
        }

        return messageService.fetchMessagesBefore(chatRoomId, before, limit);
    }

    @Operation(summary = "채팅방 이름 수정", description = "chatRoomId에 해당하는 채팅방의 이름을 수정합니다.")
    @PatchMapping("/{chatRoomId}/rename")
    public ResponseEntity<String> renameChatRoom(
            @Parameter(description = "채팅방 ID", required = true) @PathVariable UUID chatRoomId,
            @RequestParam("roomName") String newRoomName,
            @RequestHeader("Authorization") String authorizationHeader) {

        String token = authorizationHeader.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(token);

        chatRoomService.renameChatRoom(chatRoomId, newRoomName, userId);
        return ResponseEntity.ok("채팅방 이름이 수정되었습니다.");
    }

    // 특정 채팅방의 다른 유저 정보 조회
    @Operation(summary = "채팅방 내 다른 유저 조회", description = "")
    @PostMapping("/{chatRoomId}/users/get")
    public List<UserDTO> getUsersByChatRoom(
            @Parameter(description = "채팅방 아이디", required = true) @PathVariable UUID chatRoomId,
            @RequestHeader("Authorization") String authorizationHeader) {

        String token = authorizationHeader.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(token);

        if (!chatRoomService.isMember(chatRoomId, userId)) {
            throw new RuntimeException("해당 채팅방 멤버가 아닙니다.");
        }

        return chatRoomService.getUsersByChatRoom(chatRoomId);
    }

    @Operation(summary = "채팅방 나가기", description = "사용자가 채팅방을 나갑니다. 마지막 사용자가 나가면 채팅방/메시지 삭제.")
    @DeleteMapping("/{chatRoomId}/leave")
    public ResponseEntity<Void> leaveChatRoom(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable UUID chatRoomId) {

        String token = authorizationHeader.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(token);

        chatRoomService.leaveChatRoom(chatRoomId, userId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{chatRoomId}/personality")
    public ResponseEntity<String> updateChatBotPersonality(
            @PathVariable UUID chatRoomId,
            @RequestBody UpdatePersonalityRequestDTO requestDTO) {

        boolean success = chatRoomService.updateChatBotPersonality(chatRoomId, requestDTO.getPersonality());
        if (success) {
            return ResponseEntity.ok("ChatBot personality updated successfully.");
        } else {
            return ResponseEntity.badRequest().body("Failed to update personality. Check if it's a Kino room.");
        }
    }

    // 유저 전체 알림 설정
    @PatchMapping("/notification/user")
    @Operation(summary = "유저 전체 채팅 알림 ON/OFF", description = "유저 ID와 알림 상태(boolean)를 받아서 모든 채팅방 알림을 일괄 설정합니다.")
    public ResponseEntity<String> updateUserChatNotificationSetting(
            @RequestParam Long userId,
            @RequestParam boolean isOn) {

        boolean success = userService.updateChatNotificationSetting(userId, isOn);
        return success
                ? ResponseEntity.ok("User-wide chat notification setting updated.")
                : ResponseEntity.badRequest().body("Invalid userId");
    }

    // 특정 채팅방 알림 설정
    @PatchMapping("/notification/chatroom")
    @Operation(summary = "특정 채팅방 알림 ON/OFF", description = "userId, chatRoomId, 알림 상태를 입력받아 특정 채팅방 알림을 설정합니다.")
    public ResponseEntity<String> updateChatRoomNotificationSetting(
            @RequestParam Long userId,
            @RequestParam UUID chatRoomId,
            @RequestParam boolean isOn) {

        boolean success = chatRoomService.updateChatRoomNotificationSetting(userId, chatRoomId, isOn);
        return success
                ? ResponseEntity.ok("Chat room-specific notification setting updated.")
                : ResponseEntity.badRequest().body("Invalid userId or chatRoomId");
    }

    /* =========================
     * ✅ 읽음 처리 (카톡 핵심)
     * ========================= */

    @Operation(summary = "채팅방 읽음 처리", description = "해당 유저가 이 채팅방에서 lastReadAt까지 읽었음을 기록합니다.")
    @PostMapping("/{chatRoomId}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable UUID chatRoomId,
            @RequestBody ReadRequestDTO body,
            @RequestHeader("Authorization") String authorizationHeader) {
    
        String token = authorizationHeader.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(token);
    
        if (!chatRoomService.isMember(chatRoomId, userId)) {
            throw new RuntimeException("해당 채팅방 멤버가 아닙니다.");
        }
    
        LocalDateTime lastReadAt =
                (body != null && body.getLastReadAt() != null)
                        ? body.getLastReadAt()
                        : LocalDateTime.now();
    
        chatRoomService.markRead(chatRoomId, userId, lastReadAt);
        return ResponseEntity.ok().build();
    }
    

    @Operation(summary = "채팅방 readPointers 조회", description = "채팅방 참여자별 lastReadAt 포인터를 조회합니다.")
    @GetMapping("/{chatRoomId}/readPointers")
    public ResponseEntity<ReadPointersResponseDTO> getReadPointers(
            @PathVariable UUID chatRoomId,
            @RequestHeader("Authorization") String authorizationHeader) {

        String token = authorizationHeader.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(token);

        if (!chatRoomService.isMember(chatRoomId, userId)) {
            throw new RuntimeException("해당 채팅방 멤버가 아닙니다.");
        }

        return ResponseEntity.ok(chatRoomService.getReadPointers(chatRoomId));
    }
}

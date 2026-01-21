// src/main/java/com/example/kinover_backend/controller/ChatRoomController.java
package com.example.kinover_backend.controller;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.ChatRoomDTO;
import com.example.kinover_backend.dto.ChatRoomMediaResponseDTO;
import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.dto.ReadPointersResponseDTO;
import com.example.kinover_backend.dto.ReadRequestDTO;
import com.example.kinover_backend.dto.UpdatePersonalityRequestDTO;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.dto.RenameRoomForMeRequestDTO;
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

        // ✅ service에서 userId 기준 displayRoomName을 roomName으로 내려주게 만들 것
        ChatRoomDTO dto = chatRoomService.getChatRoom(chatRoomId, userId);
        return ResponseEntity.ok(dto);
    }

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

        // ✅ service 내부에서 "유저별 displayRoomName" 자동 세팅
        ChatRoomDTO createdChatRoom = chatRoomService.createChatRoom(
                familyId, authenticatedUserId, roomName, userIdList);

        return new ResponseEntity<>(createdChatRoom, HttpStatus.CREATED);
    }

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

        // ✅ service에서 각 DTO.roomName을 "userId 기준 displayRoomName"으로 만들어서 내려주게 만들 것
        return chatRoomService.getAllChatRooms(userId, familyId);
    }

    @Operation(summary = "메세지 불러오기", description = "채팅방의 메시지를 가져옵니다.")
    @GetMapping("/{chatRoomId}/messages/fetch")
    public List<MessageDTO> fetchMessages(
            @PathVariable UUID chatRoomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader("Authorization") String authorizationHeader) {

        String token = authorizationHeader.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(token);

        if (!chatRoomService.isMember(chatRoomId, userId)) {
            throw new RuntimeException("해당 채팅방 멤버가 아닙니다.");
        }

        return messageService.fetchMessagesBefore(chatRoomId, before, limit);
    }

    @Operation(summary = "채팅방 이름 수정(전역)", description = "chatRoomId에 해당하는 채팅방의 이름을 수정합니다. (전역 이름)")
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

    // ✅✅ 추가: 내 화면에서만 보이는 채팅방 이름 변경
    @Operation(summary = "채팅방 이름 수정(나만)", description = "같은 채팅방이라도 내 화면에서만 보이는 이름을 수정합니다.")
    @PatchMapping("/{chatRoomId}/rename/me")
    public ResponseEntity<String> renameChatRoomForMe(
            @PathVariable UUID chatRoomId,
            @RequestBody RenameRoomForMeRequestDTO body,
            @RequestHeader("Authorization") String authorizationHeader) {

        String token = authorizationHeader.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(token);

        if (!chatRoomService.isMember(chatRoomId, userId)) {
            throw new RuntimeException("해당 채팅방 멤버가 아닙니다.");
        }

        String newName = (body != null) ? body.getRoomName() : null;
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("roomName은 비어있을 수 없습니다.");
        }

        chatRoomService.renameChatRoomForUser(chatRoomId, userId, newName.trim());
        return ResponseEntity.ok("내 채팅방 이름이 수정되었습니다.");
    }

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

    @Operation(summary = "채팅방 미디어 모아보기", description = "채팅방의 이미지/영상 미디어를 최신순으로 조회합니다. type=ALL|IMAGE|VIDEO, before로 페이지네이션")
    @GetMapping("/{chatRoomId}/media")
    public ResponseEntity<ChatRoomMediaResponseDTO> fetchChatRoomMedia(
            @PathVariable UUID chatRoomId,
            @RequestParam(required = false, defaultValue = "ALL") String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(defaultValue = "30") int limit,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        String token = authorizationHeader.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(token);

        if (!chatRoomService.isMember(chatRoomId, userId)) {
            throw new RuntimeException("해당 채팅방 멤버가 아닙니다.");
        }

        ChatRoomMediaResponseDTO dto = messageService.fetchChatRoomMedia(chatRoomId, type, before, limit);
        return ResponseEntity.ok(dto);
    }
}

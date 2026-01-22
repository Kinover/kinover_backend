// src/main/java/com/example/kinover_backend/controller/ChatRoomController.java
package com.example.kinover_backend.controller;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.*;
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
import java.util.*;
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

    // =========================
    // ✅ 공통: Authorization -> userId
    // =========================
    private Long getUserIdFromAuth(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.trim().isEmpty()) {
            throw new UnauthorizedException("Authorization 헤더가 없습니다.");
        }
        String token = authorizationHeader.replace("Bearer ", "").trim();
        if (token.isEmpty()) {
            throw new UnauthorizedException("Bearer 토큰이 비어있습니다.");
        }
        return jwtUtil.getUserIdFromToken(token);
    }

    private void requireMember(UUID chatRoomId, Long userId) {
        if (!chatRoomService.isMember(chatRoomId, userId)) {
            throw new ForbiddenException("해당 채팅방 멤버가 아닙니다.");
        }
    }

    // =========================
    // ✅ 채팅방 단건 조회 (푸시/딥링크 진입용)
    // =========================
    @Operation(summary = "채팅방 단건 조회", description = "chatRoomId로 채팅방을 단건 조회합니다. (푸시/딥링크 진입용)")
    @GetMapping("/{chatRoomId}")
    public ResponseEntity<ChatRoomDTO> getChatRoom(
            @Parameter(description = "채팅방 ID", required = true) @PathVariable UUID chatRoomId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long userId = getUserIdFromAuth(authorizationHeader);
        requireMember(chatRoomId, userId);

        ChatRoomDTO dto = chatRoomService.getChatRoom(chatRoomId, userId);
        return ResponseEntity.ok(dto);
    }

    // =========================
    // ✅ 채팅방 생성 (권장) - JSON Body 기반
    // =========================
    @Operation(
            summary = "채팅방 생성 (권장, JSON)",
            description = "새로운 채팅방을 생성합니다. (roomName/userIds를 body로 전달해 URL 인코딩 문제를 피합니다.)"
    )
    @PostMapping("/create")
    public ResponseEntity<ChatRoomDTO> createChatRoomJson(
            @RequestBody CreateChatRoomRequestDTO body,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long authenticatedUserId = getUserIdFromAuth(authorizationHeader);

        if (body == null) throw new BadRequestException("요청 바디가 비어있습니다.");

        UUID familyId = body.getFamilyId();
        String roomName = body.getRoomName();
        List<Long> userIds = body.getUserIds();

        if (familyId == null) throw new BadRequestException("familyId는 필수입니다.");
        if (roomName == null || roomName.trim().isEmpty()) throw new BadRequestException("roomName은 비어있을 수 없습니다.");

        // ✅ null-safe
        if (userIds == null) userIds = new ArrayList<>();

        // ✅ 공백/중복 제거 + 본인 자동 포함(서비스 안정화)
        Set<Long> dedup = new LinkedHashSet<>();
        for (Long id : userIds) {
            if (id != null) dedup.add(id);
        }
        dedup.add(authenticatedUserId); // 생성자 본인 포함 강제

        List<Long> finalUserIds = new ArrayList<>(dedup);

        ChatRoomDTO createdChatRoom = chatRoomService.createChatRoom(
                familyId,
                authenticatedUserId,
                roomName.trim(),
                finalUserIds
        );

        return new ResponseEntity<>(createdChatRoom, HttpStatus.CREATED);
    }

    // =========================
    // ✅ 채팅방 생성 (레거시) - PathVariable 기반 (비권장)
    // =========================
    @Operation(summary = "채팅방 생성(레거시)", description = "기존 방식. roomName에 공백/이모지/특수문자 있으면 인코딩 이슈로 실패할 수 있어요.")
    @PostMapping("/create/{roomName}/{userIds}/{familyId}")
    public ResponseEntity<ChatRoomDTO> createChatRoomLegacy(
            @PathVariable String roomName,
            @PathVariable String userIds,
            @PathVariable UUID familyId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long authenticatedUserId = getUserIdFromAuth(authorizationHeader);

        if (roomName == null || roomName.trim().isEmpty()) {
            throw new BadRequestException("roomName은 비어있을 수 없습니다.");
        }
        if (userIds == null || userIds.trim().isEmpty()) {
            throw new BadRequestException("userIds는 비어있을 수 없습니다.");
        }

        List<Long> userIdList;
        try {
            userIdList = Arrays.stream(userIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            throw new BadRequestException("userIds 형식이 잘못되었습니다. 쉼표로 구분된 숫자여야 합니다.");
        }

        // 본인 자동 포함(레거시도 안정화)
        if (!userIdList.contains(authenticatedUserId)) userIdList.add(authenticatedUserId);

        ChatRoomDTO createdChatRoom = chatRoomService.createChatRoom(
                familyId,
                authenticatedUserId,
                roomName.trim(),
                userIdList
        );

        return new ResponseEntity<>(createdChatRoom, HttpStatus.CREATED);
    }

    // =========================
    // ✅ 채팅방에 유저 추가
    // =========================
    @Operation(summary = "채팅방에 유저 추가", description = "기존 채팅방에 새로운 유저를 추가합니다. userIds는 쉼표로 구분된 문자열로 전달 (예: 1,2,3)")
    @PostMapping("/{chatRoomId}/addUsers/{userIds}")
    public ResponseEntity<ChatRoomDTO> addUsersToChatRoom(
            @PathVariable UUID chatRoomId,
            @PathVariable String userIds,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long authenticatedUserId = getUserIdFromAuth(authorizationHeader);

        if (userIds == null || userIds.trim().isEmpty()) {
            throw new BadRequestException("추가할 유저 ID 리스트는 비어있을 수 없습니다");
        }

        List<Long> userIdList;
        try {
            userIdList = Arrays.stream(userIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            throw new BadRequestException("userIds 형식이 잘못되었습니다. 쉼표로 구분된 숫자여야 합니다.");
        }

        ChatRoomDTO updatedChatRoom = chatRoomService.addUsersToChatRoom(chatRoomId, userIdList, authenticatedUserId);
        return new ResponseEntity<>(updatedChatRoom, HttpStatus.OK);
    }

    // =========================
    // ✅ 채팅방 목록 조회
    // =========================
    @Operation(summary = "채팅방 조회", description = "특정 유저의 모든 채팅방을 조회합니다.")
    @PostMapping("/{familyId}/{userId}")
    public List<ChatRoomDTO> getAllChatRooms(
            @PathVariable UUID familyId,
            @PathVariable Long userId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long authenticatedUserId = getUserIdFromAuth(authorizationHeader);
        if (!authenticatedUserId.equals(userId)) {
            throw new ForbiddenException("인증된 유저와 요청 유저가 일치하지 않습니다");
        }

        return chatRoomService.getAllChatRooms(userId, familyId);
    }

    // =========================
    // ✅ 메시지 불러오기
    // =========================
    @Operation(summary = "메세지 불러오기", description = "채팅방의 메시지를 가져옵니다.")
    @GetMapping("/{chatRoomId}/messages/fetch")
    public List<MessageDTO> fetchMessages(
            @PathVariable UUID chatRoomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long userId = getUserIdFromAuth(authorizationHeader);
        requireMember(chatRoomId, userId);

        return messageService.fetchMessagesBefore(chatRoomId, before, limit);
    }

    // =========================
    // ✅ 채팅방 이름 수정(전역) - 레거시 유지
    // =========================
    @Operation(summary = "채팅방 이름 수정(전역)", description = "chatRoomId에 해당하는 채팅방의 이름을 수정합니다. (전역 이름)")
    @PatchMapping("/{chatRoomId}/rename")
    public ResponseEntity<String> renameChatRoom(
            @PathVariable UUID chatRoomId,
            @RequestParam("roomName") String newRoomName,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long userId = getUserIdFromAuth(authorizationHeader);

        if (newRoomName == null || newRoomName.trim().isEmpty()) {
            throw new BadRequestException("roomName은 비어있을 수 없습니다.");
        }

        chatRoomService.renameChatRoom(chatRoomId, newRoomName.trim(), userId);
        return ResponseEntity.ok("채팅방 이름이 수정되었습니다.");
    }

    // =========================
    // ✅ 채팅방 이름 변경 (나만)
    // =========================
    @Operation(summary = "채팅방 이름 수정(나만)", description = "같은 채팅방이라도 내 화면에서만 보이는 이름을 수정합니다.")
    @PatchMapping("/{chatRoomId}/rename/me")
    public ResponseEntity<String> renameChatRoomForMe(
            @PathVariable UUID chatRoomId,
            @RequestBody RenameRoomForMeRequestDTO body,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long userId = getUserIdFromAuth(authorizationHeader);
        requireMember(chatRoomId, userId);

        String newName = (body != null) ? body.getRoomName() : null;
        if (newName == null || newName.trim().isEmpty()) {
            throw new BadRequestException("roomName은 비어있을 수 없습니다.");
        }

        chatRoomService.renameChatRoomForUser(chatRoomId, userId, newName.trim());
        return ResponseEntity.ok("내 채팅방 이름이 수정되었습니다.");
    }

    // =========================
    // ✅ 채팅방 내 유저 조회
    // =========================
    @Operation(summary = "채팅방 내 다른 유저 조회", description = "")
    @PostMapping("/{chatRoomId}/users/get")
    public List<UserDTO> getUsersByChatRoom(
            @PathVariable UUID chatRoomId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long userId = getUserIdFromAuth(authorizationHeader);
        requireMember(chatRoomId, userId);

        return chatRoomService.getUsersByChatRoom(chatRoomId);
    }

    // =========================
    // ✅ 채팅방 나가기
    // =========================
    @Operation(summary = "채팅방 나가기", description = "사용자가 채팅방을 나갑니다. 마지막 사용자가 나가면 채팅방/메시지 삭제.")
    @DeleteMapping("/{chatRoomId}/leave")
    public ResponseEntity<Void> leaveChatRoom(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable UUID chatRoomId
    ) {
        Long userId = getUserIdFromAuth(authorizationHeader);
        chatRoomService.leaveChatRoom(chatRoomId, userId);
        return ResponseEntity.ok().build();
    }

    // =========================
    // ✅ 챗봇 성격 변경
    // =========================
    @PatchMapping("/{chatRoomId}/personality")
    public ResponseEntity<String> updateChatBotPersonality(
            @PathVariable UUID chatRoomId,
            @RequestBody UpdatePersonalityRequestDTO requestDTO
    ) {
        boolean success = chatRoomService.updateChatBotPersonality(chatRoomId, requestDTO.getPersonality());
        return success
                ? ResponseEntity.ok("ChatBot personality updated successfully.")
                : ResponseEntity.badRequest().body("Failed to update personality. Check if it's a Kino room.");
    }

    // =========================
    // ✅ 유저 전체 채팅 알림 ON/OFF
    // =========================
    @PatchMapping("/notification/user")
    @Operation(summary = "유저 전체 채팅 알림 ON/OFF", description = "유저 ID와 알림 상태(boolean)를 받아서 모든 채팅방 알림을 일괄 설정합니다.")
    public ResponseEntity<String> updateUserChatNotificationSetting(
            @RequestParam Long userId,
            @RequestParam boolean isOn
    ) {
        boolean success = userService.updateChatNotificationSetting(userId, isOn);
        return success
                ? ResponseEntity.ok("User-wide chat notification setting updated.")
                : ResponseEntity.badRequest().body("Invalid userId");
    }

    // =========================
    // ✅ 특정 채팅방 알림 ON/OFF
    // =========================
    @PatchMapping("/notification/chatroom")
    @Operation(summary = "특정 채팅방 알림 ON/OFF", description = "userId, chatRoomId, 알림 상태를 입력받아 특정 채팅방 알림을 설정합니다.")
    public ResponseEntity<String> updateChatRoomNotificationSetting(
            @RequestParam Long userId,
            @RequestParam UUID chatRoomId,
            @RequestParam boolean isOn
    ) {
        boolean success = chatRoomService.updateChatRoomNotificationSetting(userId, chatRoomId, isOn);
        return success
                ? ResponseEntity.ok("Chat room-specific notification setting updated.")
                : ResponseEntity.badRequest().body("Invalid userId or chatRoomId");
    }

    // =========================
    // ✅ 채팅방 읽음 처리
    // =========================
    @Operation(summary = "채팅방 읽음 처리", description = "해당 유저가 이 채팅방에서 lastReadAt까지 읽었음을 기록합니다.")
    @PostMapping("/{chatRoomId}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable UUID chatRoomId,
            @RequestBody ReadRequestDTO body,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long userId = getUserIdFromAuth(authorizationHeader);
        requireMember(chatRoomId, userId);

        LocalDateTime lastReadAt =
                (body != null && body.getLastReadAt() != null)
                        ? body.getLastReadAt()
                        : LocalDateTime.now();

        chatRoomService.markRead(chatRoomId, userId, lastReadAt);
        return ResponseEntity.ok().build();
    }

    // =========================
    // ✅ readPointers 조회
    // =========================
    @Operation(summary = "채팅방 readPointers 조회", description = "채팅방 참여자별 lastReadAt 포인터를 조회합니다.")
    @GetMapping("/{chatRoomId}/readPointers")
    public ResponseEntity<ReadPointersResponseDTO> getReadPointers(
            @PathVariable UUID chatRoomId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long userId = getUserIdFromAuth(authorizationHeader);
        requireMember(chatRoomId, userId);

        return ResponseEntity.ok(chatRoomService.getReadPointers(chatRoomId));
    }

    // =========================
    // ✅ 채팅방 미디어 모아보기
    // =========================
    @Operation(summary = "채팅방 미디어 모아보기", description = "채팅방의 이미지/영상 미디어를 최신순으로 조회합니다. type=ALL|IMAGE|VIDEO, before로 페이지네이션")
    @GetMapping("/{chatRoomId}/media")
    public ResponseEntity<ChatRoomMediaResponseDTO> fetchChatRoomMedia(
            @PathVariable UUID chatRoomId,
            @RequestParam(required = false, defaultValue = "ALL") String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(defaultValue = "30") int limit,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long userId = getUserIdFromAuth(authorizationHeader);
        requireMember(chatRoomId, userId);

        ChatRoomMediaResponseDTO dto = messageService.fetchChatRoomMedia(chatRoomId, type, before, limit);
        return ResponseEntity.ok(dto);
    }
}

package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.ChatRoomDTO;
import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.dto.UpdatePersonalityRequestDTO;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.entity.Message;
import com.example.kinover_backend.service.ChatRoomService;
import com.example.kinover_backend.service.MessageService;
import com.example.kinover_backend.JwtUtil;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "채팅방 Controller", description = "채팅방 관련 API를 제공합니다.")
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/chatRoom")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final MessageService messageService;
    private final JwtUtil jwtUtil;

    public ChatRoomController(ChatRoomService chatRoomService, MessageService messageService, JwtUtil jwtUtil) {
        this.chatRoomService = chatRoomService;
        this.messageService = messageService;
        this.jwtUtil = jwtUtil;
    }

    // 채팅방 생성
    @Operation(summary = "채팅방 생성", description = "새로운 채팅방을 생성합니다. userIds는 쉼표로 구분된 문자열로 전달 (예: 1,2,3)")
    @PostMapping("/create/{roomName}/{userIds}")
    public ResponseEntity<ChatRoomDTO> createChatRoom(
            @Parameter(description = "채팅방 이름", required = true) @PathVariable String roomName,
            @Parameter(description = "참여 유저 ID 리스트 (쉼표로 구분)", required = true) @PathVariable String userIds,
            @RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        Long authenticatedUserId = jwtUtil.getUserIdFromToken(token);

        List<Long> userIdList = Arrays.stream(userIds.split(","))
                .map(Long::parseLong)
                .collect(Collectors.toList());

        ChatRoomDTO createdChatRoom = chatRoomService.createChatRoom(authenticatedUserId, roomName, userIdList);
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

    // 특정 유저가 가진 채팅방 조회 (familyId 추가, 사용하지 않음)
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
        return chatRoomService.getAllChatRooms(userId); // familyId는 사용하지 않음
    }

    // 채팅방의 모든 메시지 조회
    @Operation(summary = "메세지 불러오기", description = "채팅방의 모든 메세지를 가져옵니다.")
    @GetMapping("/{chatRoomId}/messages/fetch")
    public List<MessageDTO> fetchMessages(
            @PathVariable UUID chatRoomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader("Authorization") String authorizationHeader) {
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
        return chatRoomService.getUsersByChatRoom(chatRoomId);
    }

    @Operation(
            summary = "채팅방 나가기",
            description = "사용자가 채팅방을 나갑니다. 채팅방에 마지막 사용자가 나갈 경우, 해당 채팅방과 메시지들이 함께 삭제됩니다."
    )
    @DeleteMapping("/{chatRoomId}/leave")
    public ResponseEntity<Void> leaveChatRoom(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable UUID chatRoomId
    ) {
        String token = authorizationHeader.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(token);

        chatRoomService.leaveChatRoom(chatRoomId, userId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{chatRoomId}/personality")
    public ResponseEntity<String> updateChatBotPersonality(
            @PathVariable UUID chatRoomId,
            @RequestBody UpdatePersonalityRequestDTO requestDTO
    ) {
        boolean success = chatRoomService.updateChatBotPersonality(chatRoomId, requestDTO.getPersonality());
        if (success) {
            return ResponseEntity.ok("ChatBot personality updated successfully.");
        } else {
            return ResponseEntity.badRequest().body("Failed to update personality. Check if it's a Kino room.");
        }
    }
}
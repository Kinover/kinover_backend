package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.ChatRoomDTO;
import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.entity.Message;
import com.example.kinover_backend.service.ChatRoomService;
import com.example.kinover_backend.service.MessageService;
import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.service.OpenAIService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private final OpenAIService openAIService;

    public ChatRoomController(ChatRoomService chatRoomService, MessageService messageService, JwtUtil jwtUtil, OpenAIService openAIService) {
        this.chatRoomService = chatRoomService;
        this.messageService = messageService;
        this.jwtUtil = jwtUtil;
        this.openAIService = openAIService;
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
    @Operation(summary = "메세지 수신", description = "채팅방의 모든 메세지를 가져옵니다.")
    @PostMapping("/{chatRoomId}/messages/fetch")
    public List<MessageDTO> getAllMessages(
            @Parameter(description = "채팅방 아이디", required = true) @PathVariable UUID chatRoomId,
            @RequestHeader("Authorization") String authorizationHeader) {
        return messageService.getAllMessagesByChatRoomId(chatRoomId);
    }

    // 메시지 전송 및 저장
    @Operation(summary = "메세지 발신 및 저장", description = "메세지를 보내고 저장시킵니다.")
    @PostMapping("/messages/send")
    public ResponseEntity<Message> saveMessage(
            @RequestBody Message message,
            @RequestHeader("Authorization") String authorizationHeader) {
        Message savedMessage = messageService.saveMessage(message);
        return new ResponseEntity<>(savedMessage, HttpStatus.CREATED);
    }

    // 특정 채팅방의 다른 유저 정보 조회
    @Operation(summary = "채팅방 내 다른 유저 조회", description = "")
    @PostMapping("/{chatRoomId}/users/get")
    public List<UserDTO> getUsersByChatRoom(
            @Parameter(description = "채팅방 아이디", required = true) @PathVariable UUID chatRoomId,
            @RequestHeader("Authorization") String authorizationHeader) {
        return chatRoomService.getUsersByChatRoom(chatRoomId);
    }

    // AI
    @Operation(summary = "AI 챗봇 키노 채팅", description = "챗봇에게 채팅 수신")
    @PostMapping("/ai")
    public ResponseEntity<?> chat(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");
        try {
            String reply = openAIService.askChatGPT(userMessage);
            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("ChatGPT 호출 실패: " + e.getMessage());
        }
    }
}
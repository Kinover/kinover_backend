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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Tag(name = "채팅방 Controller", description = "채팅방 관련 API를 제공합니다.")
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/chatRoom")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final MessageService messageService;
    private final JwtUtil jwtUtil; // JwtUtil 추가
    private final OpenAIService openAIService;


    public ChatRoomController(ChatRoomService chatRoomService, MessageService messageService, JwtUtil jwtUtil, OpenAIService openAIService) {
        this.chatRoomService = chatRoomService;
        this.messageService = messageService;
        this.jwtUtil = jwtUtil;
        this.openAIService = openAIService;
    }

    // 특정 유저가 가진 채팅방 조회
    @Operation(summary = "채팅방 조회", description = "특정 가족과 유저의 채팅방을 조회합니다.")
    @PostMapping("/{familyId}/{userId}")
    public List<ChatRoomDTO> getAllChatRooms(
            @Parameter(description = "가족 아이디", required = true) @PathVariable UUID familyId,
            @Parameter(description = "유저 아이디", required = true) @PathVariable Long userId,
            @RequestHeader("Authorization") String authorizationHeader) { // Authorization 헤더에서 토큰을 받음

        return chatRoomService.getAllChatRooms(familyId, userId);
    }

    // 채팅방의 모든 메시지 조회
    @Operation(summary = "메세지 수신", description = "채팅방의 모든 메세지를 가져옵니다.")
    @PostMapping("/{chatRoomId}/messages/fetch")
    public List<MessageDTO> getAllMessages(
            @Parameter(description = "채팅방 아이디", required = true) @PathVariable UUID chatRoomId,
            @RequestHeader("Authorization") String authorizationHeader) { // Authorization 헤더에서 토큰을 받음

        return messageService.getAllMessagesByChatRoomId(chatRoomId);
    }

    // 메시지 전송 및 저장
    @Operation(summary = "메세지 발신 및 저장", description = "메세지를 보내고 저장시킵니다.")
    @PostMapping("/messages/send")
    public ResponseEntity<Message> saveMessage(
            @RequestBody Message message,
            @RequestHeader("Authorization") String authorizationHeader) { // 메시지는 POST 요청 바디에서 받음

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

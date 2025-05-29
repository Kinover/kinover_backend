package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.ChatRoom;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomDTO {
    private UUID chatRoomId;
    private String roomName;

    @JsonProperty("kino")
    private boolean isKino; // RoomType 대신 boolean으로 kino 여부만 구분

    private String familyType;
    private String image;
    private Date createdAt;
    private FamilyDTO family;  // Family 엔티티의 DTO
    private List<UserChatRoomDTO> userChatRooms; // UserChatRoom 엔티티의 DTO 리스트
    private String latestMessageContent;
    private LocalDateTime latestMessageTime;
    private List<String> memberImages;
}
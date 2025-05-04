package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.ChatRoom;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    // ChatRoom 엔티티를 ChatRoomDTO로 변환하는 생성자
    public ChatRoomDTO(ChatRoom chatRoom) {
        if (chatRoom.getChatRoomId() == null) {
            chatRoom.setChatRoomId(UUID.randomUUID());
        }
        this.chatRoomId = chatRoom.getChatRoomId();
        this.image = chatRoom.getImage();
        this.roomName = chatRoom.getRoomName();
        this.isKino = chatRoom.isKino(); // ChatRoom에서 isKino로 변경된 getter 호출
        this.familyType = chatRoom.getFamilyType();
        this.createdAt = chatRoom.getCreatedAt();
        this.family = chatRoom.getFamily() != null ? new FamilyDTO(chatRoom.getFamily()) : null;
    }

    // ChatRoom 리스트를 ChatRoomDTO 리스트로 변환
    public static List<ChatRoomDTO> fromEntityList(List<ChatRoom> chatRooms) {
        return chatRooms.stream()
                .map(ChatRoomDTO::new)
                .collect(Collectors.toList());
    }
}
package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.ChatRoom;
import com.example.kinover_backend.enums.KinoType;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

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
    private KinoType kinoType;
    private String familyType;
    private String image;
    private Date createdAt;
    private FamilyDTO family; // Family 엔티티의 DTO
    private List<UserChatRoomDTO> userChatRooms; // UserChatRoom 엔티티의 DTO 리스트
    private String latestMessageContent;
    private LocalDateTime latestMessageTime;
    private List<String> memberImages;
    private boolean isNotificationOn;

    public ChatRoomDTO(
            ChatRoom chatRoom,
            String latestMessageContent,
            LocalDateTime latestMessageTime,
            List<String> memberImages,
            List<UserChatRoomDTO> userChatRooms // 필요 시
    ) {
        if (chatRoom.getChatRoomId() == null) {
            chatRoom.setChatRoomId(UUID.randomUUID());
        }
        this.chatRoomId = chatRoom.getChatRoomId();
        this.roomName = chatRoom.getRoomName();
        this.isKino = chatRoom.isKino();
        if (this.isKino) {
            this.kinoType = chatRoom.getKinoType() != null
                    ? chatRoom.getKinoType()
                    : KinoType.YELLOW_KINO;
        } else {
            this.kinoType = null;
        }

        this.familyType = chatRoom.getFamilyType();
        this.createdAt = chatRoom.getCreatedAt();
        this.image = chatRoom.getImage();
        this.family = chatRoom.getFamily() != null ? new FamilyDTO(chatRoom.getFamily()) : null;

        this.latestMessageContent = latestMessageContent;
        this.latestMessageTime = latestMessageTime;
        this.memberImages = memberImages;
        this.userChatRooms = userChatRooms;
    }
}
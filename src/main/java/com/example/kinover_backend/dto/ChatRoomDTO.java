// src/main/java/com/example/kinover_backend/dto/ChatRoomDTO.java
package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.ChatRoom;
import com.example.kinover_backend.enums.KinoType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomDTO {

    private UUID chatRoomId;
    private String roomName;

    @JsonProperty("kino")
    private boolean isKino;

    private KinoType kinoType;
    private String familyType;
    private String image;
    private Date createdAt;
    private FamilyDTO family;

    private List<UserChatRoomDTO> userChatRooms;

    private String latestMessageContent;
    private LocalDateTime latestMessageTime;

    private List<String> memberImages;

    private boolean isNotificationOn;

    // ✅ 추가: 미읽음 개수
    private int unreadCount;

    public ChatRoomDTO(
            ChatRoom chatRoom,
            String latestMessageContent,
            LocalDateTime latestMessageTime,
            List<String> memberImages,
            List<UserChatRoomDTO> userChatRooms
    ) {
        if (chatRoom.getChatRoomId() == null) {
            chatRoom.setChatRoomId(UUID.randomUUID());
        }

        this.chatRoomId = chatRoom.getChatRoomId();
        this.roomName = chatRoom.getRoomName();
        this.isKino = chatRoom.isKino();

        if (this.isKino) {
            this.kinoType = chatRoom.getKinoType() != null ? chatRoom.getKinoType() : KinoType.YELLOW_KINO;
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

        this.unreadCount = 0;
    }
}

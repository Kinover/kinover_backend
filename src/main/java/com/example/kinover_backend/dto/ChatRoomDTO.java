package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.ChatRoom;
import com.example.kinover_backend.enums.RoomType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomDTO {
    private UUID chatRoomId;
    private String roomName;
    private RoomType roomType;
    private String familyType;
    private String image;
    private Date createdAt;
    private FamilyDTO family;  // Family 엔티티의 DTO
    private List<UserChatRoomDTO> userChatRooms; // UserChatRoom 엔티티의 DTO 리스트

    // ChatRoom 엔티티를 ChatRoomDTO로 변환하는 생성자
    public ChatRoomDTO(ChatRoom chatRoom) {
        if(chatRoom.getChatRoomId()==null){
            chatRoom.setChatRoomId(UUID.randomUUID());
        }
        this.chatRoomId = chatRoom.getChatRoomId();
        this.image=chatRoom.getImage();
        this.roomName = chatRoom.getRoomName();
        this.roomType = chatRoom.getRoomType();
        this.familyType = chatRoom.getFamilyType();
        this.createdAt = chatRoom.getCreatedAt();
        this.family = chatRoom.getFamily() != null ? new FamilyDTO(chatRoom.getFamily()) : null; // FamilyDTO로 변환
        this.userChatRooms = chatRoom.getUserChatRooms().stream()
                .map(UserChatRoomDTO::new) // UserChatRoomDTO로 변환
                .collect(Collectors.toList());
    }




    // ChatRoom과 관련된 UserChatRoomDTO 목록을 반환하는 메서드 (옵션)
    public static List<ChatRoomDTO> fromEntityList(List<ChatRoom> chatRooms) {
        return chatRooms.stream()
                .map(ChatRoomDTO::new)
                .collect(Collectors.toList());
    }
}

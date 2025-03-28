package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.UserChatRoom;
import lombok.Getter;

import java.util.Date;
import java.util.UUID;

@Getter
public class UserChatRoomDTO {
    private UUID userChatRoomId;
    private Long userId;          // 유저 ID
    private UUID chatRoomId;      // 채팅방 ID
    private Date joinedAt;        // 유저가 채팅방에 입장한 시간

    // UserChatRoom 엔티티를 UserChatRoomDTO로 변환하는 생성자
    public UserChatRoomDTO(UserChatRoom userChatRoom) {
        if(userChatRoom.getUserChatRoomId()==null){
            userChatRoom.setUserChatRoomId(UUID.randomUUID());
        }
        this.userChatRoomId = userChatRoom.getUserChatRoomId();
        this.userId = userChatRoom.getUser().getUserId();   // User 엔티티의 userId를 사용
        this.chatRoomId = userChatRoom.getChatRoom().getChatRoomId(); // ChatRoom 엔티티의 chatRoomId를 사용
        this.joinedAt = userChatRoom.getJoinedAt();
    }
}

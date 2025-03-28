package com.example.kinover_backend.dto;
import com.example.kinover_backend.entity.ChatRoom;
import org.mapstruct.Mapper;


@Mapper(componentModel = "spring")
public interface ChatRoomMapper {
    ChatRoomDTO toDTO(ChatRoom chatRoom);
}

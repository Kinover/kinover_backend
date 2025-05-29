package com.example.kinover_backend.dto;
import com.example.kinover_backend.entity.ChatRoom;
import org.mapstruct.Mapper;


import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ChatRoomMapper {

    @Mapping(target = "latestMessageContent", ignore = true)
    @Mapping(target = "latestMessageTime", ignore = true)
    @Mapping(target = "memberImages", ignore = true)
    ChatRoomDTO toDTO(ChatRoom chatRoom);
}

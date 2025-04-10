package com.example.kinover_backend.service;


import com.example.kinover_backend.dto.ChatRoomDTO;
import com.example.kinover_backend.dto.ChatRoomMapper;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.entity.ChatRoom;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.entity.UserChatRoom;
import com.example.kinover_backend.repository.ChatRoomRepository;
import com.example.kinover_backend.repository.UserChatRoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final UserChatRoomRepository userChatRoomRepository;

    @Autowired
    private ChatRoomMapper chatRoomMapper;


    @Autowired
    public ChatRoomService(ChatRoomRepository chatRoomRepository, UserChatRoomRepository userChatRoomRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.userChatRoomRepository= userChatRoomRepository;
    }

    // familyId와 userId에 맞는 채팅방 조회
    public List<ChatRoomDTO> getAllChatRooms(Long userId) {
        List<UserChatRoom> userChatRooms = userChatRoomRepository.findByUserId(userId);
        Set<UUID> chatRoomIds = userChatRooms.stream()
                .map(UserChatRoom::getChatRoom)
                .map(ChatRoom::getChatRoomId)
                .collect(Collectors.toSet());
        List<ChatRoom> chatRooms = chatRoomRepository.findByChatRoomIdIn(chatRoomIds);
        return chatRooms.stream()
                .map(chatRoomMapper::toDTO)
                .collect(Collectors.toList());
    }

    // 채팅방 아이디 통해서 해당 채팅방 조회
    public ChatRoom getChatRooms(UUID chatRoomId) {
        return this.chatRoomRepository.findByChatRoomId(chatRoomId);
    }

    // 채팅방 생성 (다른 유저 정보, 등 ..)
//    public ChatRoom getChatRooms(Long chatRoomId) {
//        return this.chatRoomRepository.findByChatRoomId(chatRoomId);
//    }

    // 채팅방에 다른 유저 초대

    public List<UserDTO> getUsersByChatRoom(UUID chatRoomId){
        List<User> list = userChatRoomRepository.findUsersByChatRoomId(chatRoomId);
        List<UserDTO> userDTOList = new ArrayList<>(); // 빈 리스트로 초기화

        for(User user : list){
            userDTOList.add(new UserDTO(user)); // UserDTO로 변환하여 리스트에 추가
        }

        return userDTOList; // 변환된 리스트 반환
    }
}

package com.example.kinover_backend.repository;

import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

    // 유저 아이디, 가족 아이디로 채팅방 찾기
    @Query("SELECT cr FROM ChatRoom cr JOIN cr.userChatRooms ucr WHERE ucr.user.userId = :userId AND cr.family.familyId = :familyId")
    List<ChatRoom> findByUserIdAndFamilyId(@Param("userId") Long userId, @Param("familyId") UUID familyId);

    // 채팅방 아이디로 채팅방 찾기
    @Query("SELECT cr FROM ChatRoom cr WHERE cr.chatRoomId = :chatRoomId")
    ChatRoom findByChatRoomId(@Param("chatRoomId") UUID chatRoomId);

    List<ChatRoom> findByChatRoomIdIn(Set<UUID> chatRoomIds);



}

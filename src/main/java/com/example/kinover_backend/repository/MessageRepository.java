package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.ChatRoom;
import com.example.kinover_backend.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query("SELECT m FROM Message m  WHERE m.chatRoom.chatRoomId= :chatRoomId ORDER BY m.createdAt ASC")
    List<Message> findAllByChatRoomId(@Param("chatRoomId") UUID chatRoomId);

    Page<Message> findByChatRoom(ChatRoom chatRoom, Pageable pageable);

    void deleteByChatRoom(ChatRoom chatRoom);
}

// src/main/java/com/example/kinover_backend/repository/MessageRepository.java
package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.ChatRoom;
import com.example.kinover_backend.entity.Message;
import com.example.kinover_backend.enums.MessageType;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query("SELECT m FROM Message m WHERE m.chatRoom.chatRoomId = :chatRoomId ORDER BY m.createdAt ASC")
    List<Message> findAllByChatRoomId(@Param("chatRoomId") UUID chatRoomId);

    Page<Message> findByChatRoom(ChatRoom chatRoom, Pageable pageable);

    Page<Message> findByChatRoomAndHiddenFalse(ChatRoom chatRoom, Pageable pageable);

    void deleteByChatRoom(ChatRoom chatRoom);

    @Query("""
            select m from Message m
            where m.chatRoom.chatRoomId = :chatRoomId
              and m.createdAt < :before
              and (m.hidden is null or m.hidden = false)
              and not exists (
                select 1 from UserBlock ub
                where ub.blocker.userId = :viewerId
                  and ub.blocked.userId = m.sender.userId
              )
            order by m.createdAt desc
        """)
    Page<Message> findVisibleForViewerBefore(
            @Param("chatRoomId") UUID chatRoomId,
            @Param("before") LocalDateTime before,
            @Param("viewerId") Long viewerId,
            Pageable pageable
    );

    Optional<Message> findTopByChatRoom_ChatRoomIdOrderByCreatedAtDesc(UUID chatRoomId);

    // ✅ unreadCount: lastReadAt이 null일 때(혹은 전체 count 필요할 때)
    int countByChatRoom_ChatRoomIdAndSender_UserIdNot(UUID chatRoomId, Long senderUserId);

    // ✅ unreadCount: lastReadAt 이후만
    int countByChatRoom_ChatRoomIdAndCreatedAtAfterAndSender_UserIdNot(
            UUID chatRoomId,
            LocalDateTime after,
            Long senderUserId);

    // ✅ 미디어 조회: sender/chatRoom까지 fetch join 해서 N+1 방지
    @Query("""
            select m
            from Message m
            join fetch m.sender s
            join fetch m.chatRoom cr
            where cr.chatRoomId = :chatRoomId
              and m.messageType in :types
              and (:before is null or m.createdAt < :before)
              and (m.hidden is null or m.hidden = false)
              and not exists (
                select 1 from UserBlock ub
                where ub.blocker.userId = :viewerId
                  and ub.blocked.userId = s.userId
              )
            order by m.createdAt desc
        """)
    List<Message> findMediaMessagesVisibleForViewerBefore(
            @Param("chatRoomId") UUID chatRoomId,
            @Param("types") List<MessageType> types,
            @Param("before") LocalDateTime before,
            @Param("viewerId") Long viewerId,
            Pageable pageable);
}

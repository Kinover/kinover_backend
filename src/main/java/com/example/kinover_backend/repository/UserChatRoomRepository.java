package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.ChatRoom;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.entity.UserChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserChatRoomRepository extends JpaRepository<UserChatRoom, UUID> {

    @Query("SELECT ucr FROM UserChatRoom ucr WHERE ucr.user.userId = :userId")
    List<UserChatRoom> findByUserId(@Param("userId") Long userId);

    @Query("SELECT ucr.user FROM UserChatRoom ucr WHERE ucr.chatRoom.chatRoomId = :chatRoomId")
    List<User> findUsersByChatRoomId(@Param("chatRoomId") UUID chatRoomId);

    void deleteByUserAndChatRoom(User user, ChatRoom chatRoom);

    int countByChatRoom(ChatRoom chatRoom);

    boolean existsByUser_UserIdAndChatRoom_ChatRoomId(Long userId, UUID chatRoomId);

    Optional<UserChatRoom> findByUser_UserIdAndChatRoom_ChatRoomId(Long userId, UUID chatRoomId);

    @Query("SELECT ucr.user.userId FROM UserChatRoom ucr WHERE ucr.chatRoom.chatRoomId = :chatRoomId")
    List<Long> findMemberIdsByChatRoomId(@Param("chatRoomId") UUID chatRoomId);

    @Query("SELECT ucr FROM UserChatRoom ucr JOIN FETCH ucr.user WHERE ucr.chatRoom.chatRoomId = :chatRoomId")
    List<UserChatRoom> findByChatRoomIdWithUser(@Param("chatRoomId") UUID chatRoomId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE UserChatRoom ucr
           SET ucr.lastReadAt = :lastReadAt
         WHERE ucr.chatRoom.chatRoomId = :chatRoomId
           AND ucr.user.userId = :userId
           AND (ucr.lastReadAt IS NULL OR ucr.lastReadAt < :lastReadAt)
    """)
    int updateLastReadAtIfLater(@Param("chatRoomId") UUID chatRoomId,
                               @Param("userId") Long userId,
                               @Param("lastReadAt") LocalDateTime lastReadAt);

    @Modifying
    @Query("""
        DELETE FROM UserChatRoom ucr
         WHERE ucr.user.userId = :userId
           AND ucr.chatRoom.chatRoomId IN (
               SELECT sub.chatRoom.chatRoomId
                 FROM UserChatRoom sub
                WHERE sub.user.userId = :botId
           )
    """)
    void deleteCommonChatRoomWithBot(@Param("userId") Long userId, @Param("botId") Long botId);
}

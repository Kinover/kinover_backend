// src/main/java/com/example/kinover_backend/repository/UserChatRoomRepository.java
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

    // ✅ 기존: userId로 UserChatRoom 목록
    @Query("SELECT ucr FROM UserChatRoom ucr WHERE ucr.user.userId = :userId")
    List<UserChatRoom> findByUserId(@Param("userId") Long userId);

    // ✅ chatRoomId로 유저 목록
    @Query("SELECT ucr.user FROM UserChatRoom ucr WHERE ucr.chatRoom.chatRoomId = :chatRoomId")
    List<User> findUsersByChatRoomId(@Param("chatRoomId") UUID chatRoomId);

    void deleteByUserAndChatRoom(User user, ChatRoom chatRoom);

    int countByChatRoom(ChatRoom chatRoom);

    // ✅ 멤버 체크 O(1)급
    boolean existsByUser_UserIdAndChatRoom_ChatRoomId(Long userId, UUID chatRoomId);

    // ✅ 유저-방 row 조회 (lastReadAt 꺼내기 용)
    Optional<UserChatRoom> findByUser_UserIdAndChatRoom_ChatRoomId(Long userId, UUID chatRoomId);

    // ✅ WS 브로드캐스트용: memberId만 뽑기
    @Query("SELECT ucr.user.userId FROM UserChatRoom ucr WHERE ucr.chatRoom.chatRoomId = :chatRoomId")
    List<Long> findMemberIdsByChatRoomId(@Param("chatRoomId") UUID chatRoomId);

    // ✅ readPointers 조회 최적화: user까지 fetch join
    @Query("SELECT ucr FROM UserChatRoom ucr JOIN FETCH ucr.user WHERE ucr.chatRoom.chatRoomId = :chatRoomId")
    List<UserChatRoom> findByChatRoomIdWithUser(@Param("chatRoomId") UUID chatRoomId);

    // ✅ 읽음 포인터 역행 방지(max): DB에서 조건 업데이트
    // - lastReadAt이 null이거나, 더 과거면 업데이트
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

    // 챗봇(botId)과 유저(userId)가 같이 있는 방을 찾아서, 유저의 데이터를 삭제
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

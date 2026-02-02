// src/main/java/com/example/kinover_backend/repository/ChatRoomRepository.java
package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

    // ✅ (기존) 유저 아이디, 가족 아이디로 채팅방 찾기
    // - 그대로 두되, 서비스에서는 2-step 방식 쓰는 걸 권장(아래 메서드들)
    @Query("SELECT cr FROM ChatRoom cr JOIN cr.userChatRooms ucr WHERE ucr.user.userId = :userId AND cr.family.familyId = :familyId")
    List<ChatRoom> findByUserIdAndFamilyId(@Param("userId") Long userId, @Param("familyId") UUID familyId);

    // ✅ 채팅방 아이디로 채팅방 찾기
    @Query("SELECT cr FROM ChatRoom cr WHERE cr.chatRoomId = :chatRoomId")
    ChatRoom findByChatRoomId(@Param("chatRoomId") UUID chatRoomId);

    List<ChatRoom> findByChatRoomIdIn(Set<UUID> chatRoomIds);

    // =========================
    // ✅ 2-step용 (N+1 방지)
    // =========================

    // (1) 내가 속한 chatRoomId만 먼저 뽑기
    @Query("""
        select cr.chatRoomId
        from ChatRoom cr
        join cr.userChatRooms ucr
        where ucr.user.userId = :userId
          and cr.family.familyId = :familyId
    """)
    Set<UUID> findChatRoomIdsByUserAndFamily(@Param("userId") Long userId,
                                            @Param("familyId") UUID familyId);

    // (2) chatRoomIds로 방 + 멤버(ucr) + 유저까지 한 번에 fetch
    @Query("""
        select distinct cr
        from ChatRoom cr
        join fetch cr.userChatRooms ucr
        join fetch ucr.user u
        where cr.chatRoomId in :chatRoomIds
    """)
    List<ChatRoom> findByChatRoomIdInWithMembers(@Param("chatRoomIds") Set<UUID> chatRoomIds);
   
    Optional<ChatRoom> findFirstByFamily_FamilyIdAndIsKinoTrueAndFamilyType(UUID familyId, String familyType);

}

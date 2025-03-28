package com.example.kinover_backend.repository;

import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.entity.UserChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserChatRoomRepository extends JpaRepository<UserChatRoom, UUID> {

    // JPQL을 사용하여 userId로 UserChatRoom을 찾는 쿼리 작성
    @Query("SELECT ucr FROM UserChatRoom ucr WHERE ucr.user.userId = :userId")
    List<UserChatRoom> findByUserId(@Param("userId") Long userId);

    @Query("SELECT ucr.user FROM UserChatRoom ucr WHERE ucr.chatRoom.chatRoomId= :chatRoomId")
    List<User> findUsersByChatRoomId(UUID chatRoomId);

}

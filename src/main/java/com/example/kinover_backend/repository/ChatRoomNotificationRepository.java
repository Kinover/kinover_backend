package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.ChatRoom;
import com.example.kinover_backend.entity.ChatRoomNotificationSetting;
import com.example.kinover_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomNotificationRepository extends JpaRepository<ChatRoomNotificationSetting, Long> {
    Optional<ChatRoomNotificationSetting> findByUserAndChatRoom(User user, ChatRoom chatRoom);
}

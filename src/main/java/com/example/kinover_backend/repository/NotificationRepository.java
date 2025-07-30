package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByFamilyIdInOrderByCreatedAtDesc(List<UUID> familyIds);

    void deleteByPostId(UUID postId);
    void deleteByCommentId(UUID commentId);



}

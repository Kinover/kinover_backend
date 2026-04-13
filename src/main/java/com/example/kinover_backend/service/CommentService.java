package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.CommentDTO;
import com.example.kinover_backend.entity.*;
import com.example.kinover_backend.enums.NotificationType;
import com.example.kinover_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    // ✅ 종(알림함) unreadCount의 근거(Notification 테이블)
    private final NotificationRepository notificationRepository;

    private final UserFamilyRepository userFamilyRepository; // (현재 코드에선 미사용이지만 기존 주입 유지)
    private final FcmNotificationService fcmNotificationService;

    @Transactional
    public void createComment(CommentDTO dto) {
        Post post = postRepository.findById(dto.getPostId())
                .orElseThrow(() -> new RuntimeException("게시물 없음"));

        User author = userRepository.findById(dto.getAuthorId())
                .orElseThrow(() -> new RuntimeException("작성자 없음"));

        // 1) 댓글 저장
        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setContent(dto.getContent());
        commentRepository.save(comment);

        // 2) 게시글 commentCount 증가
        post.setCommentCount(post.getCommentCount() + 1);
        postRepository.save(post);

        // 3) ✅ Notification 저장은 "항상" 한다.
        //    - bell unreadCount는 서버에서 countBy...AndAuthorIdNot(userId)로 "본인 제외"가 이미 처리됨
        //    - 여기서 조건으로 막아버리면(특히 글 작성자가 댓글을 달 때) 다른 사람들의 bell unread가 틀어질 수 있음
        Notification notification = Notification.builder()
                .notificationType(NotificationType.COMMENT)
                .postId(post.getPostId())
                .commentId(comment.getCommentId())
                .familyId(post.getFamily().getFamilyId())
                .authorId(author.getUserId())
                .build();
        notificationRepository.save(notification);

        // =========================================================
        // ✅ (A) "댓글 알림" 받을 대상자 좁히기
        // - 게시글 작성자
        // - 해당 게시글에 댓글 단 적 있는 사람들
        // =========================================================

        // 1) 게시글 작성자
        Long postAuthorId = post.getAuthor() != null ? post.getAuthor().getUserId() : null;

        // 2) 댓글 작성자들(기존 댓글 기준) — 방금 저장한 댓글도 포함될 수 있음(쿼리 구현에 따라)
        List<Long> participantIds = commentRepository.findDistinctAuthorIdsByPostId(post.getPostId());

        // 3) 합집합 만들기
        Set<Long> recipients = new HashSet<>();
        if (postAuthorId != null) recipients.add(postAuthorId);
        if (participantIds != null) recipients.addAll(participantIds);

        // 4) 본인 제외
        recipients.remove(dto.getAuthorId());

        // =========================================================
        // ✅ (B) 멘션 알림
        // =========================================================
        List<Long> mentionUserIds = dto.getMentionUserIds();
        Set<Long> mentionTargets = new HashSet<>();
        if (mentionUserIds != null) {
            for (Long uid : mentionUserIds) {
                if (uid != null && !uid.equals(dto.getAuthorId())) {
                    mentionTargets.add(uid);
                    fcmNotificationService.sendMentionCommentNotification(uid, dto);
                }
            }
        }

        // =========================================================
        // ✅ (C) 댓글 일반 알림 발송(멘션 대상자 제외, 유저 설정 ON인 경우에만)
        // =========================================================
        for (Long uid : recipients) {
            if (mentionTargets.contains(uid)) continue;

            User member = userRepository.findById(uid).orElse(null);
            if (member == null) continue;

            if (Boolean.TRUE.equals(member.getIsCommentNotificationOn())) {
                fcmNotificationService.sendCommentNotification(uid, dto);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<CommentDTO> getCommentsForPost(UUID postId, Long viewerUserId) {
        if (!postRepository.existsById(postId)) {
            throw new RuntimeException("게시물 없음");
        }

        return commentRepository.findCommentDtosByPostIdVisibleForViewerOrderByCreatedAtAsc(postId, viewerUserId)
                .stream()
                .peek(dto -> {
                    if (dto.getAuthorName() == null || dto.getAuthorName().isBlank()) {
                        dto.setAuthorName("알 수 없는 사용자");
                    }
                    if (dto.getAuthorImage() == null) {
                        dto.setAuthorImage("");
                    }
                })
                .toList();
    }

    @Transactional
    public void deleteComment(UUID commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("댓글 없음"));

        Post post = comment.getPost();

        // ✅ 댓글 알림 삭제
        notificationRepository.deleteByCommentId(commentId);

        // ✅ 댓글 삭제
        commentRepository.delete(comment);

        // ✅ commentCount 감소(0 이하 방지)
        int newCount = Math.max(0, post.getCommentCount() - 1);
        post.setCommentCount(newCount);
        postRepository.save(post);
    }
}

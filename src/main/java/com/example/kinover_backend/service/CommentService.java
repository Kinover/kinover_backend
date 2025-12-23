package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.CommentDTO;
import com.example.kinover_backend.entity.*;
import com.example.kinover_backend.enums.NotificationType;
import com.example.kinover_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final UserFamilyRepository userFamilyRepository;
    private final FcmNotificationService fcmNotificationService;

    @Transactional
    public void createComment(CommentDTO dto) {
        Post post = postRepository.findById(dto.getPostId())
                .orElseThrow(() -> new RuntimeException("게시물 없음"));
        User author = userRepository.findById(dto.getAuthorId())
                .orElseThrow(() -> new RuntimeException("작성자 없음"));

        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setContent(dto.getContent());

        commentRepository.save(comment);

        post.setCommentCount(post.getCommentCount() + 1);
        postRepository.save(post);

        // ✅ Notification 저장 (원하면 ‘수신자 단위’로 저장하는 구조가 더 좋음)
        // 지금 Notification 엔티티가 receiverId 구조가 아니라서,
        // "알림함"이 수신자별로 필요하면 Notification 테이블을 바꾸는 게 맞아.
        // 일단은 기존처럼 저장하되, 작성자 본인 제외 로직만 유지하거나 삭제해도 됨.
        // (아래는 기존 코드의 버그를 고친 버전: 원래 if 조건이 항상 false였음)
        if (!post.getAuthor().getUserId().equals(dto.getAuthorId())) {
            Notification notification = Notification.builder()
                    .notificationType(NotificationType.COMMENT)
                    .postId(post.getPostId())
                    .commentId(comment.getCommentId())
                    .familyId(post.getFamily().getFamilyId())
                    .authorId(author.getUserId())
                    .build();
            notificationRepository.save(notification);
        }

        // =========================================================
        // ✅ (A) "댓글 알림" 받을 대상자 좁히기
        // - 게시글 작성자
        // - 해당 게시글에 댓글 단 적 있는 사람들
        // =========================================================

        // 1) 게시글 작성자
        Long postAuthorId = post.getAuthor().getUserId();

        // 2) 댓글 작성자들(기존 댓글 기준) — 방금 저장한 댓글도 포함됨
        List<Long> participantIds = commentRepository.findDistinctAuthorIdsByPostId(post.getPostId());

        // 3) 합집합 만들기
        java.util.Set<Long> recipients = new java.util.HashSet<>();
        if (postAuthorId != null)
            recipients.add(postAuthorId);
        if (participantIds != null)
            recipients.addAll(participantIds);

        // 4) 본인 제외
        recipients.remove(dto.getAuthorId());

        // =========================================================
        // ✅ (B) 멘션 알림은 별도(설정 무시)
        // =========================================================
        // CommentDTO에 mentionUserIds 같은 필드를 두는 걸 추천.
        // 지금은 dto에 없으니, "프론트가 멘션 유저ID 리스트를 같이 보내는 구조"로 바꿔야 깔끔해.
        //
        // 예시:
        // List<Long> mentionUserIds = dto.getMentionUserIds();
        // if (mentionUserIds != null) {
        // for (Long uid : new HashSet<>(mentionUserIds)) {
        // if (uid != null && !uid.equals(dto.getAuthorId())) {
        // fcmNotificationService.sendMentionCommentNotification(uid, dto);
        // }
        // }
        // }

        // =========================================================
        // ✅ (C) 댓글 일반 알림 발송(유저 설정 ON인 경우에만)
        // =========================================================
        for (Long uid : recipients) {
            User member = userRepository.findById(uid).orElse(null);
            if (member == null)
                continue;

            if (Boolean.TRUE.equals(member.getIsCommentNotificationOn())) {
                fcmNotificationService.sendCommentNotification(uid, dto);
            }
        }
    }

    public List<CommentDTO> getCommentsForPost(UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시물 없음"));

        return commentRepository.findByPostOrderByCreatedAtAsc(post)
                .stream().map(comment -> {
                    CommentDTO dto = new CommentDTO();
                    dto.setCommentId(comment.getCommentId());
                    dto.setPostId(postId);
                    dto.setContent(comment.getContent());
                    dto.setAuthorId(comment.getAuthor().getUserId());
                    dto.setAuthorName(comment.getAuthor().getName());
                    dto.setAuthorImage(comment.getAuthor().getImage());
                    dto.setCreatedAt(comment.getCreatedAt());
                    // mentionUserIds는 응답에서 굳이 안 내려도 됨(필요하면 저장 구조 추가)
                    return dto;
                }).collect(Collectors.toList());
    }

    @Transactional
    public void deleteComment(UUID commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("댓글 없음"));

        Post post = comment.getPost();

        notificationRepository.deleteByCommentId(commentId);

        commentRepository.delete(comment);

        int newCount = Math.max(0, post.getCommentCount() - 1);
        post.setCommentCount(newCount);
        postRepository.save(post);
    }
}

package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.CommentDTO;
import com.example.kinover_backend.entity.*;
import com.example.kinover_backend.enums.NotificationType;
import com.example.kinover_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

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

        // 알림 저장
        Notification notification = Notification.builder()
                .notificationType(NotificationType.COMMENT)
                .postId(post.getPostId())
                .commentId(comment.getCommentId())
                .familyId(post.getFamily().getFamilyId())  // comment로부터는 familyId 직접 접근 어려움
                .authorId(author.getUserId())
                .build();
        notificationRepository.save(notification);
    }

    public List<CommentDTO> getCommentsForPost(UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시물 없음"));

        return commentRepository.findByPostOrderByCreatedAtAsc(post)
                .stream().map(comment -> {
                    CommentDTO dto = new CommentDTO();
                    dto.setCommentId(UUID.randomUUID());
                    dto.setPostId(postId);
                    dto.setContent(comment.getContent());
                    dto.setAuthorId(comment.getAuthor().getUserId());
                    dto.setAuthorName(comment.getAuthor().getName());
                    dto.setAuthorImage(comment.getAuthor().getImage());
                    dto.setCreatedAt(comment.getCreatedAt());
                    return dto;
                }).collect(Collectors.toList());
    }

    public void deleteComment(UUID commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("댓글 없음"));

        Post post = comment.getPost();

        // 댓글 삭제
        commentRepository.delete(comment);

        // 댓글 수 감소 (음수 방지)
        int newCount = Math.max(0, post.getCommentCount() - 1);
        post.setCommentCount(newCount);
        postRepository.save(post);
    }
}



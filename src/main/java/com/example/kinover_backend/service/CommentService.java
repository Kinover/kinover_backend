package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.CommentDTO;
import com.example.kinover_backend.entity.Comment;
import com.example.kinover_backend.repository.CommentRepository;
import com.example.kinover_backend.repository.MemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CommentService {
    private final CommentRepository commentRepository;
    private final MemoryRepository memoryRepository;

    @Autowired
    public CommentService(CommentRepository commentRepository, MemoryRepository memoryRepository) {
        this.commentRepository = commentRepository;
        this.memoryRepository = memoryRepository;
    }

    // 댓글 조회 (CommentDTO 반환)
    public Optional<List<CommentDTO>> findByMemoryId(UUID memoryId){
        Optional<List<Comment>> comments = commentRepository.findByMemoryId(memoryId);
        if (comments.isPresent()) {
            List<CommentDTO> commentDTOs = comments.get().stream()
                    .map(CommentDTO::new)
                    .collect(Collectors.toList());
            return Optional.of(commentDTOs);
        } else {
            return Optional.empty();
        }
    }

    // 댓글 추가 (CommentDTO 반환)
    public CommentDTO addComment(Comment comment){
        Comment savedComment = commentRepository.save(comment);
        return new CommentDTO(savedComment);
    }

    // 댓글 삭제
    public void removeComment(UUID commentId){
        commentRepository.deleteById(commentId);
    }
}

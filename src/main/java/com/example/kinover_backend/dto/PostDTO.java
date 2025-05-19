package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.Post;
import com.example.kinover_backend.entity.PostImage;
import com.example.kinover_backend.enums.PostType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Setter
@Builder
public class PostDTO {

    // 클라이언트 → 서버 요청 시 포함 (작성 시)
    private Long authorId;
    private UUID familyId;
    private UUID categoryId;
    private List<String> imageUrls;
    private List<PostType> postTypes;
    private String content;

    // 서버 → 클라이언트 응답 시 채워짐
    private UUID postId;
    private String authorName;
    private String authorImage;
    private Date createdAt;
    private int commentCount;

    public static PostDTO from(Post post) {
        return PostDTO.builder()
                .postId(post.getPostId())
                .content(post.getContent())
                .createdAt(post.getCreatedAt())
                .commentCount(post.getCommentCount())
                .authorId(post.getAuthor().getUserId())
                .authorName(post.getAuthor().getName())
                .authorImage(post.getAuthor().getImage())
                .categoryId(post.getCategory() != null ? post.getCategory().getCategoryId() : null)
                .familyId(post.getFamily().getFamilyId())
                .imageUrls(post.getImages().stream().map(PostImage::getImageUrl).collect(Collectors.toList()))
                .build();
    }
}

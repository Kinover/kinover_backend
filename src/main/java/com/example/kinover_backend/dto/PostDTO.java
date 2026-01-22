package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.Post;
import com.example.kinover_backend.entity.PostImage;
import com.example.kinover_backend.enums.PostType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.stream.Collectors;

@Getter
@Setter
@Builder
public class PostDTO {

    private Long authorId;
    private UUID familyId;
    private UUID categoryId;
    private List<String> imageUrls;
    private List<PostType> postTypes;
    private String content;

    private UUID postId;
    private String authorName;
    private String authorImage;
    private Date createdAt;
    private int commentCount;

    private String categoryTitle;

    public static PostDTO from(Post post) {
        if (post == null)
            return null;

        var author = post.getAuthor();
        var family = post.getFamily();

        List<String> urls;
        try {
            urls = (post.getImages() == null)
                    ? List.of()
                    : post.getImages().stream()
                            .filter(Objects::nonNull)
                            .map(PostImage::getImageUrl)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
        } catch (Exception e) {
            // Lazy 로딩/프록시 문제로 터져도 목록 자체는 살려두기
            urls = List.of();
        }

        return PostDTO.builder()
                .postId(post.getPostId())
                .content(post.getContent())
                .createdAt(post.getCreatedAt())
                .commentCount(post.getCommentCount())
                .authorId(author != null ? author.getUserId() : null)
                .authorName(author != null ? author.getName() : null)
                .authorImage(author != null ? author.getImage() : null)
                .categoryId(post.getCategory() != null ? post.getCategory().getCategoryId() : null)
                .familyId(family != null ? family.getFamilyId() : null)
                .imageUrls(urls)
                .build();
    }
}

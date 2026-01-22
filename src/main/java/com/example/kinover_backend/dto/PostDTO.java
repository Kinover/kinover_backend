// src/main/java/com/example/kinover_backend/dto/PostDTO.java
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
        if (post == null) return null;

        var author = post.getAuthor();
        var family = post.getFamily();

        List<PostImage> images = (post.getImages() == null) ? List.of() : post.getImages();

        // ✅ imageOrder 기준 정렬 (null-safe)
        List<PostImage> sorted = images.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        PostImage::getImageOrder,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();

        List<String> urls = sorted.stream()
                .map(PostImage::getImageUrl)
                .filter(Objects::nonNull)
                .toList();

        List<PostType> types = sorted.stream()
                .map(PostImage::getPostType)
                .toList();

        return PostDTO.builder()
                .postId(post.getPostId())
                .content(post.getContent())
                .createdAt(post.getCreatedAt())
                .commentCount(post.getCommentCount())

                .authorId(author != null ? author.getUserId() : null)
                .authorName(author != null ? author.getName() : null)
                .authorImage(author != null ? author.getImage() : null)

                .familyId(family != null ? family.getFamilyId() : null)
                .categoryId(post.getCategory() != null ? post.getCategory().getCategoryId() : null)
                .categoryTitle(post.getCategory() != null ? post.getCategory().getTitle() : null)

                .imageUrls(urls)
                .postTypes(types)
                .build();
    }
}

package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.PostDTO;
import com.example.kinover_backend.entity.*;
import com.example.kinover_backend.enums.PostType;
import com.example.kinover_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.example.kinover_backend.service.S3Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostService {

    private final UserRepository userRepository;
    private final FamilyRepository familyRepository;
    private final CategoryRepository categoryRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final S3Service s3Service;

    @Value("${cloudfront.domain}")
    private String cloudFrontDomain;

    public void createPost(PostDTO postDTO) {
        // 1. 작성자 조회
        User author = userRepository.findById(postDTO.getAuthorId())
                .orElseThrow(() -> new RuntimeException("작성자 정보 없음"));

        // 2. 가족 조회
        Family family = familyRepository.findFamilyById(postDTO.getFamilyId())
                .orElseThrow(() -> new RuntimeException("가족 정보 없음"));

        // 3. 카테고리 조회 (nullable)
        Category category = categoryRepository.findById(postDTO.getCategoryId())
                .orElseThrow(() -> new RuntimeException("카테고리 정보 없음"));

        // 4. Post 생성
        Post post = new Post();
        post.setAuthor(author);
        post.setFamily(family);
        post.setCategory(category);
        post.setContent(postDTO.getContent());
        post.setCommentCount(0);

        // 5. 이미지 URL → CloudFront URL 변환
        List<PostImage> imageEntities = new ArrayList<>();
        List<String> s3ObjectKeys = postDTO.getImageUrls();
        List<PostType> types = postDTO.getPostTypes();

        if (s3ObjectKeys != null && types != null) {
            if (s3ObjectKeys.size() != types.size()) {
                throw new IllegalArgumentException("imageUrls와 postTypes의 개수가 일치하지 않습니다.");
            }
            for (int i = 0; i < s3ObjectKeys.size(); i++) {
                String s3Key = s3ObjectKeys.get(i);
                String cloudFrontUrl = cloudFrontDomain + s3Key;

                PostImage img = new PostImage();
                img.setPost(post);
                img.setImageUrl(cloudFrontUrl);
                img.setPostType(types.get(i));
                img.setImageOrder(i);
                imageEntities.add(img);
            }
        }

        //6.저장
        post.setImages(imageEntities);
        postRepository.save(post);
    }

    public void deleteImage(UUID postId, String imageUrl) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글 없음"));

        PostImage imageToDelete = post.getImages().stream()
                .filter(img -> img.getImageUrl().equals(imageUrl))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("이미지 없음"));

        // S3에서 이미지 삭제
        String fileName = imageUrl.substring(imageUrl.indexOf("post/"));
        s3Service.deleteImageFromS3(fileName);

        // 이미지 리스트에서 제거
        post.getImages().remove(imageToDelete);

        // 남은 이미지가 없으면 게시글 전체 삭제
        if (post.getImages().isEmpty()) {
            postRepository.delete(post);
        } else {
            postRepository.save(post);
        }
    }

    public void deletePost(UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글 없음"));

        // S3에서 이미지 삭제
        for (PostImage image : post.getImages()) {
            String imageUrl = image.getImageUrl();
            String fileName = imageUrl.substring(imageUrl.indexOf("post/"));
            s3Service.deleteImageFromS3(fileName);
        }

        // 댓글 삭제
        commentRepository.deleteAllByPost(post);

        // 게시글 삭제
        postRepository.delete(post);
    }

    public List<PostDTO> getPostsByFamilyAndCategory(Long userId, UUID familyId, UUID categoryId) {
        List<Post> posts;

        if (categoryId == null) {
            posts = postRepository.findAllByFamily_FamilyIdOrderByCreatedAtDesc(familyId);
        } else {
            posts = postRepository.findAllByFamily_FamilyIdAndCategory_CategoryIdOrderByCreatedAtDesc(familyId, categoryId);
        }

        List<PostDTO> result = new ArrayList<>();
        for (Post post : posts) {
            PostDTO dto = getPostDTO(post);
            result.add(dto);
        }

        return result;
    }

    @NotNull
    private static PostDTO getPostDTO(Post post) {
        PostDTO dto = new PostDTO();
        dto.setPostId(post.getPostId());
        dto.setAuthorName(post.getAuthor().getName());
        dto.setAuthorImage(post.getAuthor().getImage());
        dto.setCreatedAt(post.getCreatedAt());
        dto.setContent(post.getContent());
        dto.setCommentCount(post.getCommentCount());

        List<String> imageUrls = new ArrayList<>();
        List<PostType> postTypes = new ArrayList<>();
        for (PostImage img : post.getImages()) {
            imageUrls.add(img.getImageUrl());
            postTypes.add(img.getPostType());
        }
        dto.setImageUrls(imageUrls);
        dto.setPostTypes(postTypes);
        return dto;
    }

}

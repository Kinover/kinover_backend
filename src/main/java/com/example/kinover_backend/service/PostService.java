package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.PostDTO;
import com.example.kinover_backend.entity.*;
import com.example.kinover_backend.enums.NotificationType;
import com.example.kinover_backend.enums.PostType;
import com.example.kinover_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final UserRepository userRepository;
    private final FamilyRepository familyRepository;
    private final CategoryRepository categoryRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostImageRepository postImageRepository;
    private final NotificationRepository notificationRepository;
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

        // 7. 알림 저장
        Notification notification = Notification.builder()
                .notificationType(NotificationType.POST)
                .postId(post.getPostId())  // post는 save 후에 ID가 할당됨
                .commentId(null)
                .familyId(post.getFamily().getFamilyId())
                .authorId(post.getAuthor().getUserId())
                .build();
        notificationRepository.save(notification);
    }

    @Transactional
    public void deleteImage(UUID postId, String imageUrl) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글 없음"));

        PostImage imageToDelete = post.getImages().stream()
                .filter(img -> img.getImageUrl().equals(imageUrl))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("이미지 없음"));

        // 1. S3에서 이미지 삭제
        if (imageUrl.startsWith(cloudFrontDomain)) {
            String s3Key = imageUrl.substring(cloudFrontDomain.length());
            s3Service.deleteImageFromS3(s3Key);
        }

        // 2. 이미지 DB에서 삭제 (post.getImages().remove는 선택)
        postImageRepository.delete(imageToDelete);
        post.getImages().remove(imageToDelete); // optional: 유지 일관성

        // 3. 이미지가 모두 제거된 경우 → 게시글 삭제
        if (post.getImages().isEmpty()) {
            commentRepository.deleteAllByPost(post);
            postRepository.delete(post);
        } else {
            postRepository.save(post); // 이미지 수만 변경된 경우 업데이트
        }
    }

    @Transactional
    public void deletePost(UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글 없음"));

        // 1. S3 삭제용 key 추출
        List<String> s3Keys = post.getImages().stream()
                .map(PostImage::getImageUrl)
                .filter(Objects::nonNull)
                .map(imageUrl -> {
                    if (imageUrl.startsWith(cloudFrontDomain)) {
                        return imageUrl.substring(cloudFrontDomain.length());
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        // 2. 이미지 DB 삭제
        postImageRepository.deleteAllByPost(post);

        // 3. 댓글 삭제
        commentRepository.deleteAllByPost(post);

        // 4. 게시글 삭제
        postRepository.delete(post);

        // 5. S3 삭제
        for (String s3Key : s3Keys) {
            s3Service.deleteImageFromS3(s3Key);
        }
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
            PostDTO dto = PostDTO.from(post);
            result.add(dto);
        }

        return result;
    }

}

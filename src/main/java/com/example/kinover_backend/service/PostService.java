// src/main/java/com/example/kinover_backend/service/PostService.java
package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.PostDTO;
import com.example.kinover_backend.dto.UpdatePostRequest;
import com.example.kinover_backend.entity.*;
import com.example.kinover_backend.enums.NotificationType;
import com.example.kinover_backend.enums.PostType;
import com.example.kinover_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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
    private final UserFamilyRepository userFamilyRepository;
    private final FcmNotificationService fcmNotificationService;
    private final S3Service s3Service;

    @Value("${cloudfront.domain}")
    private String cloudFrontDomain;

    @Transactional
    public void createPost(PostDTO postDTO) {
        if (postDTO == null) throw new IllegalArgumentException("postDTO is null");

        User author = userRepository.findById(postDTO.getAuthorId())
                .orElseThrow(() -> new RuntimeException("작성자 정보 없음"));

        Family family = familyRepository.findFamilyById(postDTO.getFamilyId())
                .orElseThrow(() -> new RuntimeException("가족 정보 없음"));

        Category category = null;
        UUID categoryId = postDTO.getCategoryId();
        String categoryTitle = postDTO.getCategoryTitle();

        if (categoryId != null) {
            category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new RuntimeException("존재하지 않는 카테고리: " + categoryId));
        } else if (categoryTitle != null && !categoryTitle.isBlank()) {
            Category c = new Category();
            c.setTitle(categoryTitle);
            c.setFamily(family);
            category = categoryRepository.save(c);
            postDTO.setCategoryId(category.getCategoryId());
        } else {
            category = null;
        }

        Post post = new Post();
        post.setAuthor(author);
        post.setFamily(family);
        post.setCategory(category);
        post.setContent(postDTO.getContent());
        post.setCommentCount(0);

        List<PostImage> imageEntities = new ArrayList<>();
        List<String> s3ObjectKeys = postDTO.getImageUrls();
        List<PostType> types = postDTO.getPostTypes();

        if (s3ObjectKeys != null && types != null) {
            if (s3ObjectKeys.size() != types.size()) {
                throw new IllegalArgumentException("imageUrls와 postTypes의 개수가 일치하지 않습니다.");
            }

            for (int i = 0; i < s3ObjectKeys.size(); i++) {
                String s3Key = s3ObjectKeys.get(i);
                String cloudFrontUrl = (s3Key != null && s3Key.startsWith("http"))
                        ? s3Key
                        : cloudFrontDomain + s3Key;

                PostImage img = new PostImage();
                img.setPost(post);
                img.setImageUrl(cloudFrontUrl);
                img.setPostType(types.get(i));
                img.setImageOrder(i);
                imageEntities.add(img);
            }
        }
        post.setImages(imageEntities);

        postRepository.save(post);

        Notification notification = Notification.builder()
                .notificationType(NotificationType.POST)
                .postId(post.getPostId())
                .commentId(null)
                .familyId(post.getFamily().getFamilyId())
                .authorId(author.getUserId())
                .build();
        notificationRepository.save(notification);

        List<User> familyMembers = userFamilyRepository.findUsersByFamilyId(postDTO.getFamilyId());
        for (User member : familyMembers) {
            if (member == null) continue;

            if (!member.getUserId().equals(postDTO.getAuthorId())
                    && Boolean.TRUE.equals(member.getIsPostNotificationOn())) {
                fcmNotificationService.sendPostNotification(member.getUserId(), postDTO);
            }
        }
    }

    @Transactional
    public void deleteImage(UUID postId, String imageUrl) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글 없음"));

        PostImage imageToDelete = post.getImages().stream()
                .filter(img -> Objects.equals(img.getImageUrl(), imageUrl))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("이미지 없음"));

        if (imageUrl != null && imageUrl.startsWith(cloudFrontDomain)) {
            String s3Key = imageUrl.substring(cloudFrontDomain.length());
            s3Service.deleteImageFromS3(s3Key);
        }

        postImageRepository.delete(imageToDelete);
        post.getImages().remove(imageToDelete);

        if (post.getImages().isEmpty()) {
            commentRepository.deleteAllByPost(post);
            postRepository.delete(post);
        } else {
            postRepository.save(post);
        }
    }

    @Transactional
    public void deletePost(UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글 없음"));

        notificationRepository.deleteByPostId(postId);

        List<String> s3Keys = post.getImages().stream()
                .map(PostImage::getImageUrl)
                .filter(Objects::nonNull)
                .map(imageUrl -> imageUrl.startsWith(cloudFrontDomain)
                        ? imageUrl.substring(cloudFrontDomain.length())
                        : null)
                .filter(Objects::nonNull)
                .toList();

        postImageRepository.deleteAllByPost(post);
        commentRepository.deleteAllByPost(post);
        postRepository.delete(post);

        for (String s3Key : s3Keys) {
            s3Service.deleteImageFromS3(s3Key);
        }
    }

    public List<PostDTO> getPostsByFamilyAndCategory(Long userId, UUID familyId, UUID categoryId) {
        List<Post> posts;

        if (categoryId == null) {
            posts = postRepository.findAllByFamily_FamilyIdOrderByCreatedAtDesc(familyId);
        } else {
            posts = postRepository.findAllByFamily_FamilyIdAndCategory_CategoryIdOrderByCreatedAtDesc(
                    familyId, categoryId);
        }

        List<PostDTO> result = new ArrayList<>();
        for (Post post : posts) {
            result.add(PostDTO.from(post));
        }
        return result;
    }

    /* ------------------------------------------------------------------ */
    /* ✅ 게시글 수정(UpdatePostRequest): content/categoryId/imageUrls/postTypes 부분 수정 */
    /* ------------------------------------------------------------------ */
    @Transactional
    public void updatePost(UUID postId, Long authenticatedUserId, UpdatePostRequest request) {
        if (request == null) throw new IllegalArgumentException("request is null");

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글 없음"));

        Long authorId = post.getAuthor() != null ? post.getAuthor().getUserId() : null;
        if (authorId == null || !authorId.equals(authenticatedUserId)) {
            throw new RuntimeException("작성자만 수정할 수 있습니다.");
        }

        Family family = post.getFamily();
        if (family == null) throw new RuntimeException("가족 정보 없음");

        // 1) content (선택)
        if (request.getContent() != null) {
            post.setContent(request.getContent());
        }

        // 2) categoryId (선택)
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("존재하지 않는 카테고리: " + request.getCategoryId()));
            post.setCategory(category);
        }

        // 3) imageUrls/postTypes (선택) - 둘 중 하나라도 오면 "이미지 수정 요청"으로 간주
        List<String> incomingUrls = request.getImageUrls();
        List<PostType> incomingTypes = request.getPostTypes();

        boolean wantsImageUpdate = (incomingUrls != null || incomingTypes != null);

        if (wantsImageUpdate) {
            // ✅ 둘 다 있어야 서버가 정확히 처리 가능
            if (incomingUrls == null || incomingTypes == null) {
                throw new IllegalArgumentException("이미지 수정 시 imageUrls와 postTypes를 함께 보내야 합니다.");
            }
            if (incomingUrls.size() != incomingTypes.size()) {
                throw new IllegalArgumentException("imageUrls와 postTypes의 개수가 일치하지 않습니다.");
            }

            // 기존 URL set
            Set<String> oldUrlSet = new HashSet<>();
            if (post.getImages() != null) {
                for (PostImage pi : post.getImages()) {
                    if (pi != null && pi.getImageUrl() != null) oldUrlSet.add(pi.getImageUrl());
                }
            }

            // 새 URL normalize(CloudFront full url로 통일)
            List<String> newUrlList = new ArrayList<>();
            for (String raw : incomingUrls) {
                if (raw == null) continue;
                String normalized = raw.startsWith("http") ? raw : cloudFrontDomain + raw;
                newUrlList.add(normalized);
            }
            Set<String> newUrlSet = new HashSet<>(newUrlList);

            // (A) 삭제된 이미지 S3 삭제
            for (String oldUrl : oldUrlSet) {
                if (!newUrlSet.contains(oldUrl) && oldUrl.startsWith(cloudFrontDomain)) {
                    String s3Key = oldUrl.substring(cloudFrontDomain.length());
                    s3Service.deleteImageFromS3(s3Key);
                }
            }

            // (B) DB replace
            postImageRepository.deleteAllByPost(post);

            List<PostImage> newImages = new ArrayList<>();
            for (int i = 0; i < newUrlList.size(); i++) {
                PostImage img = new PostImage();
                img.setPost(post);
                img.setImageUrl(newUrlList.get(i));
                img.setPostType(incomingTypes.get(i));
                img.setImageOrder(i);
                newImages.add(img);
            }
            post.setImages(newImages);
        }

        postRepository.save(post);
    }

    /* ------------------------------------------------------------------ */
    /* (기존) PostDTO 기반 수정 메서드 - 기존 호출처 있으면 유지 */
    /* ------------------------------------------------------------------ */
    @Transactional
    public void updatePost(UUID postId, Long authenticatedUserId, PostDTO postDTO) {
        if (postDTO == null) throw new IllegalArgumentException("postDTO is null");

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글 없음"));

        Long authorId = post.getAuthor() != null ? post.getAuthor().getUserId() : null;
        if (authorId == null || !authorId.equals(authenticatedUserId)) {
            throw new RuntimeException("작성자만 수정할 수 있습니다.");
        }

        Family family = post.getFamily();
        if (family == null) throw new RuntimeException("가족 정보 없음");

        if (postDTO.getContent() != null) post.setContent(postDTO.getContent());

        UUID categoryId = postDTO.getCategoryId();
        String categoryTitle = postDTO.getCategoryTitle();

        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new RuntimeException("존재하지 않는 카테고리: " + categoryId));
            post.setCategory(category);
        } else if (categoryTitle != null && !categoryTitle.isBlank()) {
            Category c = new Category();
            c.setTitle(categoryTitle);
            c.setFamily(family);
            Category saved = categoryRepository.save(c);
            post.setCategory(saved);
        }

        List<String> incomingUrls = postDTO.getImageUrls();
        List<PostType> incomingTypes = postDTO.getPostTypes();

        if (incomingUrls == null || incomingTypes == null) {
            postRepository.save(post);
            return;
        }
        if (incomingUrls.size() != incomingTypes.size()) {
            throw new IllegalArgumentException("imageUrls와 postTypes의 개수가 일치하지 않습니다.");
        }

        Set<String> oldUrlSet = new HashSet<>();
        if (post.getImages() != null) {
            for (PostImage pi : post.getImages()) {
                if (pi != null && pi.getImageUrl() != null) oldUrlSet.add(pi.getImageUrl());
            }
        }

        List<String> newUrlList = new ArrayList<>();
        for (String raw : incomingUrls) {
            if (raw == null) continue;
            String normalized = raw.startsWith("http") ? raw : cloudFrontDomain + raw;
            newUrlList.add(normalized);
        }

        Set<String> newUrlSet = new HashSet<>(newUrlList);

        for (String oldUrl : oldUrlSet) {
            if (!newUrlSet.contains(oldUrl) && oldUrl.startsWith(cloudFrontDomain)) {
                String s3Key = oldUrl.substring(cloudFrontDomain.length());
                s3Service.deleteImageFromS3(s3Key);
            }
        }

        postImageRepository.deleteAllByPost(post);

        List<PostImage> newImages = new ArrayList<>();
        for (int i = 0; i < newUrlList.size(); i++) {
            PostImage img = new PostImage();
            img.setPost(post);
            img.setImageUrl(newUrlList.get(i));
            img.setPostType(incomingTypes.get(i));
            img.setImageOrder(i);
            newImages.add(img);
        }
        post.setImages(newImages);

        postRepository.save(post);
    }
}

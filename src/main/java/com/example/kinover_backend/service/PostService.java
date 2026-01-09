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

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String normalizeToCloudFrontUrl(String raw) {
        if (isBlank(raw)) return null;
        String v = raw.trim();
        if (v.startsWith("http")) return v;
        // cloudFrontDomain 끝에 / 없으면 붙여주기(설정에 따라 안전)
        if (!cloudFrontDomain.endsWith("/") && !v.startsWith("/")) {
            return cloudFrontDomain + "/" + v;
        }
        return cloudFrontDomain + v;
    }

    private String toS3KeyIfCloudFront(String url) {
        if (isBlank(url)) return null;
        String u = url.trim();
        if (!u.startsWith(cloudFrontDomain)) return null;
        return u.substring(cloudFrontDomain.length());
    }

    private void validateMediaLists(List<String> urls, List<PostType> types) {
        boolean hasUrls = urls != null;
        boolean hasTypes = types != null;

        if (hasUrls ^ hasTypes) {
            throw new IllegalArgumentException("이미지 수정 시 imageUrls와 postTypes를 함께 보내야 합니다.");
        }
        if (!hasUrls) return;

        if (urls.size() != types.size()) {
            throw new IllegalArgumentException("imageUrls와 postTypes의 개수가 일치하지 않습니다.");
        }
        for (int i = 0; i < urls.size(); i++) {
            if (isBlank(urls.get(i))) {
                throw new IllegalArgumentException("imageUrls[" + i + "] is empty");
            }
            if (types.get(i) == null) {
                throw new IllegalArgumentException("postTypes[" + i + "] is null");
            }
        }
    }

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
        } else if (!isBlank(categoryTitle)) {
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

        // ✅ 생성도 검증 강화
        validateMediaLists(s3ObjectKeys, types);

        if (s3ObjectKeys != null) {
            for (int i = 0; i < s3ObjectKeys.size(); i++) {
                String raw = s3ObjectKeys.get(i);
                String cloudFrontUrl = normalizeToCloudFrontUrl(raw);

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

        // ✅ CloudFront URL이면 S3 삭제
        String s3Key = toS3KeyIfCloudFront(imageUrl);
        if (!isBlank(s3Key)) {
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
                .map(this::toS3KeyIfCloudFront)
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

        // 3) imageUrls/postTypes (선택)
        List<String> incomingUrls = request.getImageUrls();
        List<PostType> incomingTypes = request.getPostTypes();

        boolean wantsImageUpdate = (incomingUrls != null || incomingTypes != null);

        if (wantsImageUpdate) {
            // ✅ (핵심) null/길이/빈값/타입 검증
            validateMediaLists(incomingUrls, incomingTypes);

            // ✅ 서버 내에서 URL을 "CloudFront full url"로 통일
            List<String> newUrlList = new ArrayList<>();
            for (String raw : incomingUrls) {
                String normalized = normalizeToCloudFrontUrl(raw);
                if (normalized != null) newUrlList.add(normalized);
            }

            // ✅ newUrlList 길이와 incomingTypes 길이가 달라지면 위험(빈값 제거로)
            // -> 위 validate에서 빈값 막았으니 여기서는 동일 길이 보장됨.
            if (newUrlList.size() != incomingTypes.size()) {
                throw new IllegalArgumentException("정규화 후 imageUrls와 postTypes 길이가 달라졌습니다.");
            }

            // 기존 URL set
            Set<String> oldUrlSet = new HashSet<>();
            if (post.getImages() != null) {
                for (PostImage pi : post.getImages()) {
                    if (pi != null && pi.getImageUrl() != null) oldUrlSet.add(pi.getImageUrl());
                }
            }

            Set<String> newUrlSet = new HashSet<>(newUrlList);

            // (A) 삭제된 이미지 S3 삭제
            for (String oldUrl : oldUrlSet) {
                if (!newUrlSet.contains(oldUrl)) {
                    String s3Key = toS3KeyIfCloudFront(oldUrl);
                    if (!isBlank(s3Key)) s3Service.deleteImageFromS3(s3Key);
                }
            }

            // (B) DB replace
            // ✅ 안전: 연관 데이터 제거는 먼저 clear -> delete (orphan/cascade 설정에 따라 충돌 방지)
            if (post.getImages() != null) {
                post.getImages().clear();
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
        } else if (!isBlank(categoryTitle)) {
            Category c = new Category();
            c.setTitle(categoryTitle);
            c.setFamily(family);
            Category saved = categoryRepository.save(c);
            post.setCategory(saved);
        }

        List<String> incomingUrls = postDTO.getImageUrls();
        List<PostType> incomingTypes = postDTO.getPostTypes();

        if (incomingUrls == null && incomingTypes == null) {
            postRepository.save(post);
            return;
        }

        // ✅ 검증 강화(둘 중 하나만 오면 400 대상)
        validateMediaLists(incomingUrls, incomingTypes);

        List<String> newUrlList = new ArrayList<>();
        for (String raw : incomingUrls) {
            String normalized = normalizeToCloudFrontUrl(raw);
            if (normalized != null) newUrlList.add(normalized);
        }
        if (newUrlList.size() != incomingTypes.size()) {
            throw new IllegalArgumentException("정규화 후 imageUrls와 postTypes 길이가 달라졌습니다.");
        }

        Set<String> oldUrlSet = new HashSet<>();
        if (post.getImages() != null) {
            for (PostImage pi : post.getImages()) {
                if (pi != null && pi.getImageUrl() != null) oldUrlSet.add(pi.getImageUrl());
            }
        }

        Set<String> newUrlSet = new HashSet<>(newUrlList);

        for (String oldUrl : oldUrlSet) {
            if (!newUrlSet.contains(oldUrl)) {
                String s3Key = toS3KeyIfCloudFront(oldUrl);
                if (!isBlank(s3Key)) s3Service.deleteImageFromS3(s3Key);
            }
        }

        if (post.getImages() != null) {
            post.getImages().clear();
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

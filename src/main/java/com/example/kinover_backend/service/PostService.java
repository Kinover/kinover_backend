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

    /** CloudFront 도메인을 끝 슬래시 없는 형태로 정규화 */
    private String cfBase() {
        if (isBlank(cloudFrontDomain)) return "";
        String v = cloudFrontDomain.trim();
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    /** raw가 파일명/경로/full url 이든 최종적으로 CloudFront full url로 통일 */
    private String normalizeToCloudFrontUrl(String raw) {
        if (isBlank(raw)) return null;

        String v = raw.trim();

        // 이미 URL이면 그대로
        if (v.startsWith("http://") || v.startsWith("https://")) return v;

        // 앞 슬래시 제거
        while (v.startsWith("/")) v = v.substring(1);

        String base = cfBase();
        if (isBlank(base)) return v; // 방어

        return base + "/" + v;
    }

    /** CloudFront URL -> S3 key로 변환 */
    private String toS3KeyIfCloudFront(String url) {
        if (isBlank(url)) return null;

        String u = url.trim();
        String base = cfBase();
        if (isBlank(base)) return null;

        String prefix = base + "/";

        if (!u.startsWith(prefix)) return null;

        String key = u.substring(prefix.length());
        while (key.startsWith("/")) key = key.substring(1);

        return isBlank(key) ? null : key;
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

    // =========================
    // CREATE
    // =========================
    @Transactional
    public void createPost(PostDTO postDTO) {
        if (postDTO == null) throw new IllegalArgumentException("postDTO is null");
        if (postDTO.getAuthorId() == null) throw new IllegalArgumentException("authorId is null");
        if (postDTO.getFamilyId() == null) throw new IllegalArgumentException("familyId is null");

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
        }

        Post post = new Post();
        post.setAuthor(author);
        post.setFamily(family);
        post.setCategory(category);
        post.setContent(postDTO.getContent());
        post.setCommentCount(0);

        List<String> rawUrls = postDTO.getImageUrls();
        List<PostType> types = postDTO.getPostTypes();
        validateMediaLists(rawUrls, types);

        if (rawUrls != null) {
            for (int i = 0; i < rawUrls.size(); i++) {
                String cloudFrontUrl = normalizeToCloudFrontUrl(rawUrls.get(i));

                PostImage img = new PostImage();
                img.setImageUrl(cloudFrontUrl);
                img.setPostType(types.get(i));
                img.setImageOrder(i);

                post.addImage(img);
            }
        }

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

    // =========================
    // DELETE IMAGE (single)
    // =========================
    @Transactional
    public void deleteImage(UUID postId, String imageUrl) {
        if (postId == null) throw new IllegalArgumentException("postId is null");
        if (isBlank(imageUrl)) throw new IllegalArgumentException("imageUrl is blank");

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글 없음"));

        String normalized = normalizeToCloudFrontUrl(imageUrl);

        List<PostImage> images = post.getImages();
        if (images == null || images.isEmpty()) {
            throw new RuntimeException("이미지 없음");
        }

        PostImage imageToDelete = images.stream()
                .filter(Objects::nonNull)
                .filter(img -> Objects.equals(img.getImageUrl(), normalized))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("이미지 없음"));

        String s3Key = toS3KeyIfCloudFront(imageToDelete.getImageUrl());
        if (!isBlank(s3Key)) s3Service.deleteImageFromS3(s3Key);

        images.remove(imageToDelete);

        if (images.isEmpty()) {
            commentRepository.deleteAllByPost(post);
            postRepository.delete(post);
        } else {
            postRepository.save(post);
        }
    }

    // =========================
    // DELETE POST
    // =========================
    @Transactional
    public void deletePost(UUID postId) {
        if (postId == null) throw new IllegalArgumentException("postId is null");

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글 없음"));

        notificationRepository.deleteByPostId(postId);

        List<PostImage> images = post.getImages() == null ? List.of() : post.getImages();

        List<String> s3Keys = images.stream()
                .filter(Objects::nonNull)
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

    // =========================
    // LIST (500 방지 + 순서복원 + images 정렬)
    // =========================
    @Transactional(readOnly = true)
    public List<PostDTO> getPostsByFamilyAndCategory(Long userId, UUID familyId, UUID categoryId) {
        if (userId == null) throw new IllegalArgumentException("userId is null");
        if (familyId == null) throw new IllegalArgumentException("familyId is null");

        // ✅ 권한 검증(목록도 막아야 함)
        boolean allowed = userFamilyRepository.existsByUser_UserIdAndFamily_FamilyId(userId, familyId);
        if (!allowed) throw new RuntimeException("권한 없음");

        List<UUID> ids = (categoryId == null)
                ? postRepository.findPostIdsByFamilyOrderByCreatedAtDesc(familyId)
                : postRepository.findPostIdsByFamilyAndCategoryOrderByCreatedAtDesc(familyId, categoryId);

        if (ids == null || ids.isEmpty()) return List.of();

        List<Post> fetched = postRepository.findPostsWithImagesByIds(ids);

        Map<UUID, Post> map = new HashMap<>();
        for (Post p : fetched) {
            if (p != null && p.getPostId() != null) {
                map.put(p.getPostId(), p);
            }
        }

        List<Post> ordered = new ArrayList<>();
        for (UUID id : ids) {
            Post p = map.get(id);
            if (p != null) ordered.add(p);
        }

        // ✅ 핵심 수정: primitive int 정렬은 comparingInt 사용 (null-safe 제거)
        for (Post p : ordered) {
            List<PostImage> imgs = p.getImages();
            if (imgs != null) {
                imgs.removeIf(Objects::isNull);
                imgs.sort(Comparator.comparingInt(PostImage::getImageOrder));
            }
        }

        return ordered.stream().map(PostDTO::from).toList();
    }

    // =========================
    // UPDATE (content/category/images)
    // =========================
    @Transactional
    public void updatePost(UUID postId, Long authenticatedUserId, UpdatePostRequest request) {
        if (postId == null) throw new IllegalArgumentException("postId is null");
        if (authenticatedUserId == null) throw new IllegalArgumentException("authenticatedUserId is null");
        if (request == null) throw new IllegalArgumentException("request is null");

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글 없음"));

        Long authorId = post.getAuthor() != null ? post.getAuthor().getUserId() : null;
        if (authorId == null || !authorId.equals(authenticatedUserId)) {
            throw new RuntimeException("작성자만 수정할 수 있습니다.");
        }

        if (request.getContent() != null) {
            post.setContent(request.getContent());
        }

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("존재하지 않는 카테고리: " + request.getCategoryId()));
            post.setCategory(category);
        }

        List<String> incomingUrls = request.getImageUrls();
        List<PostType> incomingTypes = request.getPostTypes();

        boolean wantsImageUpdate = (incomingUrls != null || incomingTypes != null);
        if (!wantsImageUpdate) {
            postRepository.save(post);
            return;
        }

        validateMediaLists(incomingUrls, incomingTypes);

        List<String> newUrlList = new ArrayList<>();
        for (String raw : incomingUrls) {
            String normalized = normalizeToCloudFrontUrl(raw);
            if (normalized != null) newUrlList.add(normalized);
        }

        if (newUrlList.size() != incomingTypes.size()) {
            throw new IllegalArgumentException("정규화 후 imageUrls와 postTypes 길이가 달라졌습니다.");
        }

        List<PostImage> oldImages = post.getImages() == null ? new ArrayList<>() : post.getImages();

        Set<String> oldUrlSet = new HashSet<>();
        for (PostImage pi : oldImages) {
            if (pi != null && pi.getImageUrl() != null) oldUrlSet.add(pi.getImageUrl());
        }

        Set<String> newUrlSet = new HashSet<>(newUrlList);

        for (String oldUrl : oldUrlSet) {
            if (!newUrlSet.contains(oldUrl)) {
                String s3Key = toS3KeyIfCloudFront(oldUrl);
                if (!isBlank(s3Key)) s3Service.deleteImageFromS3(s3Key);
            }
        }

        post.clearImages();

        for (int i = 0; i < newUrlList.size(); i++) {
            PostImage img = new PostImage();
            img.setImageUrl(newUrlList.get(i));
            img.setPostType(incomingTypes.get(i));
            img.setImageOrder(i);
            post.addImage(img);
        }

        postRepository.save(post);
    }

    // =========================
    // GET ONE (권한검증 포함)
    // =========================
    @Transactional(readOnly = true)
    public PostDTO getPostById(Long userId, UUID postId) {
        if (userId == null) throw new IllegalArgumentException("userId is null");
        if (postId == null) throw new IllegalArgumentException("postId is null");

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글 없음"));

        UUID familyId = (post.getFamily() != null) ? post.getFamily().getFamilyId() : null;
        if (familyId == null) throw new RuntimeException("가족 정보 없음");

        boolean allowed = userFamilyRepository.existsByUser_UserIdAndFamily_FamilyId(userId, familyId);
        if (!allowed) throw new RuntimeException("권한 없음");

        List<PostImage> imgs = post.getImages();
        if (imgs != null) {
            imgs.removeIf(Objects::isNull);
            // ✅ 핵심 수정: primitive int 정렬은 comparingInt 사용 (null-safe 제거)
            imgs.sort(Comparator.comparingInt(PostImage::getImageOrder));
        }

        return PostDTO.from(post);
    }
}

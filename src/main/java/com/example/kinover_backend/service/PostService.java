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

    /**
     * ✅ CloudFront 도메인을 항상 "끝 슬래시 없는 형태"로 정규화해서 사용
     * 예) https://xxx.cloudfront.net/  -> https://xxx.cloudfront.net
     */
    private String cfBase() {
        if (isBlank(cloudFrontDomain)) return "";
        String v = cloudFrontDomain.trim();
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    /**
     * ✅ raw가 파일명(media_x.jpg) 이든, /media_x.jpg 이든, full url 이든
     * 최종적으로 "CloudFront full url"로 통일
     */
    private String normalizeToCloudFrontUrl(String raw) {
        if (isBlank(raw)) return null;

        String v = raw.trim();

        // 이미 URL이면 그대로
        if (v.startsWith("http://") || v.startsWith("https://")) return v;

        // 파일명/경로 앞에 / 있으면 제거해서 중복 슬래시 방지
        while (v.startsWith("/")) v = v.substring(1);

        String base = cfBase();
        if (isBlank(base)) return v; // 설정이 비어있으면 원본 반환(방어)

        return base + "/" + v;
    }

    /**
     * ✅ CloudFront URL -> S3 key로 변환
     * - 선행 "/" 제거해서 "media_x.jpg" 형태로 반환
     */
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
        }

        Post post = new Post();
        post.setAuthor(author);
        post.setFamily(family);
        post.setCategory(category);
        post.setContent(postDTO.getContent());
        post.setCommentCount(0);

        List<String> s3ObjectKeys = postDTO.getImageUrls();
        List<PostType> types = postDTO.getPostTypes();

        validateMediaLists(s3ObjectKeys, types);

        if (s3ObjectKeys != null) {
            for (int i = 0; i < s3ObjectKeys.size(); i++) {
                String cloudFrontUrl = normalizeToCloudFrontUrl(s3ObjectKeys.get(i));

                PostImage img = new PostImage();
                img.setImageUrl(cloudFrontUrl);
                img.setPostType(types.get(i));
                img.setImageOrder(i);

                // ✅ addImage로 양방향 세팅 + 컬렉션 유지
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

    @Transactional
    public void deleteImage(UUID postId, String imageUrl) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글 없음"));

        String normalized = normalizeToCloudFrontUrl(imageUrl);

        List<PostImage> images = post.getImages();
        if (images == null || images.isEmpty()) {
            throw new RuntimeException("이미지 없음");
        }

        PostImage imageToDelete = images.stream()
                .filter(img -> Objects.equals(img.getImageUrl(), normalized))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("이미지 없음"));

        String s3Key = toS3KeyIfCloudFront(imageToDelete.getImageUrl());
        if (!isBlank(s3Key)) s3Service.deleteImageFromS3(s3Key);

        // ✅ orphanRemoval이라 images.remove만 해도 삭제됨 (repo delete는 있어도 되지만 중복)
        images.remove(imageToDelete);

        if (images.isEmpty()) {
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

        List<PostImage> images = post.getImages() == null ? List.of() : post.getImages();

        List<String> s3Keys = images.stream()
                .map(PostImage::getImageUrl)
                .map(this::toS3KeyIfCloudFront)
                .filter(Objects::nonNull)
                .toList();

        // ✅ orphanRemoval/cascade로도 되지만, 명시적으로 지우는 기존 흐름 유지
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

    @Transactional
    public void updatePost(UUID postId, Long authenticatedUserId, UpdatePostRequest request) {
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

        if (wantsImageUpdate) {
            validateMediaLists(incomingUrls, incomingTypes);

            // ✅ CloudFront full url로 통일
            List<String> newUrlList = new ArrayList<>();
            for (String raw : incomingUrls) {
                String normalized = normalizeToCloudFrontUrl(raw);
                if (normalized != null) newUrlList.add(normalized);
            }

            if (newUrlList.size() != incomingTypes.size()) {
                throw new IllegalArgumentException("정규화 후 imageUrls와 postTypes 길이가 달라졌습니다.");
            }

            // ✅ old/new 비교를 위해 기존 url set 만들기
            List<PostImage> oldImages = post.getImages() == null ? new ArrayList<>() : post.getImages();

            Set<String> oldUrlSet = new HashSet<>();
            for (PostImage pi : oldImages) {
                if (pi != null && pi.getImageUrl() != null) oldUrlSet.add(pi.getImageUrl());
            }

            Set<String> newUrlSet = new HashSet<>(newUrlList);

            // (A) 삭제된 이미지 S3 삭제
            for (String oldUrl : oldUrlSet) {
                if (!newUrlSet.contains(oldUrl)) {
                    String s3Key = toS3KeyIfCloudFront(oldUrl);
                    if (!isBlank(s3Key)) s3Service.deleteImageFromS3(s3Key);
                }
            }

            // (B) ✅ JPA orphanRemoval 안전 업데이트
            // - 컬렉션 "참조 유지" + clear/add만
            post.clearImages();

            for (int i = 0; i < newUrlList.size(); i++) {
                PostImage img = new PostImage();
                img.setImageUrl(newUrlList.get(i));
                img.setPostType(incomingTypes.get(i));
                img.setImageOrder(i);

                post.addImage(img);
            }
        }

        postRepository.save(post);
    }

    @Transactional
    public void updatePost(UUID postId, Long authenticatedUserId, PostDTO postDTO) {
        if (postDTO == null) throw new IllegalArgumentException("postDTO is null");

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글 없음"));

        Long authorId = post.getAuthor() != null ? post.getAuthor().getUserId() : null;
        if (authorId == null || !authorId.equals(authenticatedUserId)) {
            throw new RuntimeException("작성자만 수정할 수 있습니다.");
        }

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
            c.setFamily(post.getFamily());
            Category saved = categoryRepository.save(c);
            post.setCategory(saved);
        }

        List<String> incomingUrls = postDTO.getImageUrls();
        List<PostType> incomingTypes = postDTO.getPostTypes();

        if (incomingUrls == null && incomingTypes == null) {
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

        // ✅ orphanRemoval 안전 업데이트
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

    @Transactional(readOnly = true)
public PostDTO getPostById(Long userId, UUID postId) {
    if (userId == null) throw new IllegalArgumentException("userId is null");
    if (postId == null) throw new IllegalArgumentException("postId is null");

    Post post = postRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("게시글 없음"));

    UUID familyId = (post.getFamily() != null) ? post.getFamily().getFamilyId() : null;
    if (familyId == null) {
        throw new RuntimeException("가족 정보 없음");
    }

    // ✅ 권한 검증: 해당 유저가 게시글의 가족에 속해있는지
    boolean allowed = userFamilyRepository.existsByUser_UserIdAndFamily_FamilyId(userId, familyId);
    if (!allowed) {
        // 403 의미가 맞음
        throw new RuntimeException("권한 없음");
    }

    return PostDTO.from(post);
}

}

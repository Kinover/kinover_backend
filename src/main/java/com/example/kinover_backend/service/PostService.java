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
        if (postDTO == null)
            throw new IllegalArgumentException("postDTO is null");

        // 1) 작성자/가족 조회
        User author = userRepository.findById(postDTO.getAuthorId())
                .orElseThrow(() -> new RuntimeException("작성자 정보 없음"));

        Family family = familyRepository.findFamilyById(postDTO.getFamilyId())
                .orElseThrow(() -> new RuntimeException("가족 정보 없음"));

        // 2) 카테고리 해석: (ID 우선) 없고 title 있으면 생성, 둘 다 없으면 null
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
            category = null; // 무카테고리 허용
        }

        // 3) Post 조립
        Post post = new Post();
        post.setAuthor(author);
        post.setFamily(family);
        post.setCategory(category);
        post.setContent(postDTO.getContent());
        post.setCommentCount(0);

        // 4) 이미지 조립
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

        // 5) Post 저장
        postRepository.save(post);

        // 6) Notification 저장(항상)
        Notification notification = Notification.builder()
                .notificationType(NotificationType.POST)
                .postId(post.getPostId())
                .commentId(null)
                .familyId(post.getFamily().getFamilyId())
                .authorId(author.getUserId())
                .build();
        notificationRepository.save(notification);

        // 7) FCM 전송(작성자 본인 제외 + 설정 ON인 유저만)
        List<User> familyMembers = userFamilyRepository.findUsersByFamilyId(postDTO.getFamilyId());
        for (User member : familyMembers) {
            if (member == null)
                continue;

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

        // 1) S3에서 이미지 삭제
        if (imageUrl != null && imageUrl.startsWith(cloudFrontDomain)) {
            String s3Key = imageUrl.substring(cloudFrontDomain.length());
            s3Service.deleteImageFromS3(s3Key);
        }

        // 2) 이미지 DB에서 삭제
        postImageRepository.delete(imageToDelete);
        post.getImages().remove(imageToDelete);

        // 3) 이미지가 모두 제거된 경우 → 게시글 삭제
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

        // 1) S3 삭제용 key 추출
        List<String> s3Keys = post.getImages().stream()
                .map(PostImage::getImageUrl)
                .filter(Objects::nonNull)
                .map(imageUrl -> imageUrl.startsWith(cloudFrontDomain)
                        ? imageUrl.substring(cloudFrontDomain.length())
                        : null)
                .filter(Objects::nonNull)
                .toList();

        // 2) 이미지 DB 삭제
        postImageRepository.deleteAllByPost(post);

        // 3) 댓글 삭제
        commentRepository.deleteAllByPost(post);

        // 4) 게시글 삭제
        postRepository.delete(post);

        // 5) S3 삭제
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
    /* ✅ 게시글 수정: 글 내용 / 카테고리 / 이미지 전부 수정 가능 */
    /* ------------------------------------------------------------------ */
    @Transactional
    public void updatePost(UUID postId, Long authenticatedUserId, PostDTO postDTO) {
        if (postDTO == null)
            throw new IllegalArgumentException("postDTO is null");

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글 없음"));

        // ✅ 작성자 검증(컨트롤러에서도 했더라도 서비스에서 한번 더 안전하게)
        Long authorId = post.getAuthor() != null ? post.getAuthor().getUserId() : null;
        if (authorId == null || !authorId.equals(authenticatedUserId)) {
            throw new RuntimeException("작성자만 수정할 수 있습니다.");
        }

        // ✅ 가족(카테고리 생성에 필요)
        Family family = post.getFamily();
        if (family == null) {
            throw new RuntimeException("가족 정보 없음");
        }

        /* 1) 글 내용 수정 */
        if (postDTO.getContent() != null) {
            post.setContent(postDTO.getContent());
        }

        /*
         * 2) 카테고리 수정
         * - categoryId 있으면 그걸로 변경
         * - categoryId 없고 categoryTitle 있으면 생성 후 변경
         * - 둘 다 null/blank면 "카테고리 제거(null)"로 처리 (원치 않으면 이 줄만 막아줘)
         */
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

        /*
         * 3) 이미지 전체 수정(최종 리스트로 replace)
         * - DTO: imageUrls(List<String>) + postTypes(List<PostType>) 길이 동일 가정
         * - 기존 이미지 중 "새 리스트에 없는 것"은 S3에서도 삭제
         * - 남는 것/새로 추가되는 것은 DB를 새로 구성(순서/타입 반영)
         */
        List<String> incomingUrls = postDTO.getImageUrls();
        List<PostType> incomingTypes = postDTO.getPostTypes();

        if (incomingUrls == null || incomingTypes == null) {
            // 이미지 수정 요청이 아예 없으면 그대로 유지
            postRepository.save(post);
            return;
        }

        if (incomingUrls.size() != incomingTypes.size()) {
            throw new IllegalArgumentException("imageUrls와 postTypes의 개수가 일치하지 않습니다.");
        }

        // 기존 이미지 URL set
        Set<String> oldUrlSet = new HashSet<>();
        if (post.getImages() != null) {
            for (PostImage pi : post.getImages()) {
                if (pi != null && pi.getImageUrl() != null)
                    oldUrlSet.add(pi.getImageUrl());
            }
        }

        // 새 이미지 URL list(CloudFront normalize)
        List<String> newUrlList = new ArrayList<>();
        for (String raw : incomingUrls) {
            if (raw == null)
                continue;
            String normalized = raw.startsWith("http") ? raw : cloudFrontDomain + raw;
            newUrlList.add(normalized);
        }

        // 새 set
        Set<String> newUrlSet = new HashSet<>(newUrlList);

        // (A) 삭제된 이미지 S3 삭제
        for (String oldUrl : oldUrlSet) {
            if (!newUrlSet.contains(oldUrl)) {
                if (oldUrl.startsWith(cloudFrontDomain)) {
                    String s3Key = oldUrl.substring(cloudFrontDomain.length());
                    s3Service.deleteImageFromS3(s3Key);
                }
            }
        }

        // (B) DB 이미지 전부 replace
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

        // ✅ 최종 저장
        postRepository.save(post);
    }
}

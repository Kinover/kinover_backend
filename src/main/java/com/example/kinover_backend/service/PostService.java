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

    // =========================================================
    // ✅ 헬퍼 메소드 추가 (PostService 내부에서만 사용)
    // =========================================================
    private void deleteCategoryIfEmpty(UUID categoryId) {
        if (categoryId == null) return;

        // 방금 게시글을 지웠으므로, 남은 개수가 0개인지 확인
        long remainingPosts = postRepository.countByCategory_CategoryId(categoryId);
        
        if (remainingPosts == 0) {
            categoryRepository.deleteById(categoryId);
        }
    }

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

    /**
     * ✅ A안 핵심: userId -> familyId 1개 결정 (유저 1가족 전제)
     */
    private UUID resolveSingleFamilyIdOrThrow(Long userId) {
        if (userId == null) throw new IllegalArgumentException("userId is null");

        List<UUID> familyIds = userFamilyRepository.findFamilyIdsByUserId(userId);
        if (familyIds == null || familyIds.isEmpty()) {
            throw new RuntimeException("가족 소속이 없습니다.");
        }
        return familyIds.get(0);
    }

    // =========================
    // ✅ CREATE (A안)
    // - familyId는 서버가 userId로 결정
    // =========================
    @Transactional
    public void createPostA(Long authenticatedUserId, PostDTO postDTO) {
        if (authenticatedUserId == null) throw new IllegalArgumentException("authenticatedUserId is null");
        if (postDTO == null) throw new IllegalArgumentException("postDTO is null");
        if (postDTO.getAuthorId() == null) throw new IllegalArgumentException("authorId is null");

        // 컨트롤러에서도 했지만, 서비스에서도 한 번 더 방어
        if (!authenticatedUserId.equals(postDTO.getAuthorId())) {
            throw new RuntimeException("토큰 유저와 authorId가 일치하지 않습니다.");
        }

        User author = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new RuntimeException("작성자 정보 없음"));

        UUID familyId = resolveSingleFamilyIdOrThrow(authenticatedUserId);

        Family family = familyRepository.findFamilyById(familyId)
                .orElseThrow(() -> new RuntimeException("가족 정보 없음"));

        // ✅ DTO에도 familyId를 세팅해두면, 아래 FCM 발송 등에서 안전
        postDTO.setFamilyId(familyId);

        Category category = null;
        UUID categoryId = postDTO.getCategoryId();
        String categoryTitle = postDTO.getCategoryTitle();

        if (categoryId != null) {
            category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new RuntimeException("존재하지 않는 카테고리: " + categoryId));

            // (선택) 카테고리가 같은 가족 소속인지 검증하고 싶으면 여기서 체크
            // UUID catFamilyId = category.getFamily() != null ? category.getFamily().getFamilyId() : null;
            // if (catFamilyId == null || !catFamilyId.equals(familyId)) throw new RuntimeException("다른 가족의 카테고리입니다.");
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

        // ✅ 가족 구성원에게 FCM (본인 제외 + 알림 ON인 사람만)
        List<User> familyMembers = userFamilyRepository.findUsersByFamilyId(familyId);
        for (User member : familyMembers) {
            if (member == null) continue;
            if (!member.getUserId().equals(authenticatedUserId)
                    && Boolean.TRUE.equals(member.getIsPostNotificationOn())) {
                fcmNotificationService.sendPostNotification(member.getUserId(), postDTO);
            }
        }
    }

    // =========================
    // ✅ LIST (A안)
    // =========================
    @Transactional(readOnly = true)
    public List<PostDTO> getMyFamilyPosts(Long userId, UUID categoryId) {
        if (userId == null) throw new IllegalArgumentException("userId is null");

        List<UUID> familyIds = userFamilyRepository.findFamilyIdsByUserId(userId);
        if (familyIds == null || familyIds.isEmpty()) {
            return List.of();
        }

        UUID familyId = familyIds.get(0);

        List<Post> posts = (categoryId == null)
                ? postRepository.findByFamilyWithImagesOrderByCreatedAtDesc(familyId)
                : postRepository.findByFamilyAndCategoryWithImagesOrderByCreatedAtDesc(familyId, categoryId);

        // images 정렬(※ PostDTO.from에서도 정렬한다면 여기 제거 가능)
        for (Post p : posts) {
            List<PostImage> imgs = p.getImages();
            if (imgs != null) {
                imgs.removeIf(Objects::isNull);
                imgs.sort(Comparator.comparingInt(PostImage::getImageOrder));
            }
        }

        return posts.stream().map(PostDTO::from).toList();
    }

    // =========================
    // ✅ DELETE IMAGE (single) - 작성자만
    // =========================
    @Transactional
    public void deleteImage(UUID postId, Long userId, String imageUrl) {
        if (postId == null) throw new IllegalArgumentException("postId is null");
        if (userId == null) throw new IllegalArgumentException("userId is null");
        if (isBlank(imageUrl)) throw new IllegalArgumentException("imageUrl is blank");

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글 없음"));

        // ✅ 카테고리 ID 미리 저장 (게시글 삭제 시 필요)
        UUID categoryId = (post.getCategory() != null) ? post.getCategory().getCategoryId() : null;

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

        // S3 삭제
        String s3Key = toS3KeyIfCloudFront(imageToDelete.getImageUrl());
        if (!isBlank(s3Key)) s3Service.deleteImageFromS3(s3Key);

        images.remove(imageToDelete);

        // ✅ imageOrder 재정렬 (0..n-1)
        for (int i = 0; i < images.size(); i++) {
            PostImage pi = images.get(i);
            if (pi != null) pi.setImageOrder(i);
        }

        if (images.isEmpty()) {
            // ✅ 이미지 0개면 글 자체 삭제 (연관 데이터 정리)
            notificationRepository.deleteByPostId(postId);
            commentRepository.deleteAllByPost(post);
            postRepository.delete(post);
            // 4. ✅ [추가] 카테고리 비었으면 삭제
            deleteCategoryIfEmpty(categoryId);
        } else {
            postRepository.save(post);
        }
    }

    // =========================
    // ✅ DELETE POST - 작성자만
    // =========================
    @Transactional
    public void deletePost(UUID postId, Long userId) {
        if (postId == null) throw new IllegalArgumentException("postId is null");
        if (userId == null) throw new IllegalArgumentException("userId is null");

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글 없음"));

        // ✅ 카테고리 ID 미리 저장
        UUID categoryId = (post.getCategory() != null) ? post.getCategory().getCategoryId() : null;

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

        // 3. ✅ [추가] 게시글 삭제 후 카테고리가 비었으면 삭제
        // (반드시 postRepository.delete(post) 호출 후에 실행해야 count가 0이 됩니다)
        deleteCategoryIfEmpty(categoryId);

        for (String s3Key : s3Keys) {
            s3Service.deleteImageFromS3(s3Key);
        }
    }

    // =========================
    // UPDATE (content/category/images) - 작성자만
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

        // ✅ 삭제된 URL에 대해서만 S3 삭제
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
            imgs.sort(Comparator.comparingInt(PostImage::getImageOrder));
        }

        return PostDTO.from(post);
    }
}

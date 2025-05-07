package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.PostDTO;
import com.example.kinover_backend.entity.*;
import com.example.kinover_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    private final UserFamilyRepository userFamilyRepository;

    public void createPost(PostDTO postDTO) {
        // 1. 작성자 조회
        User author = userRepository.findById(postDTO.getAuthorId())
                .orElseThrow(() -> new RuntimeException("작성자 정보 없음"));

        // 2. 작성자의 가족 정보 조회
        Family family = userFamilyRepository.findFamilyByUserId(author.getUserId())
                .orElseThrow(() -> new RuntimeException("가족 정보 없음"));

        // 3. 카테고리 조회 (nullable)
        Category category = null;
        if (postDTO.getCategoryId() != null) {
            category = categoryRepository.findById(postDTO.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("카테고리 정보 없음"));
        }

        // 4. Post 엔티티 생성
        Post post = new Post();
        post.setPostId(UUID.randomUUID());
        post.setAuthor(author);
        post.setFamily(family);
        post.setCategory(category);
        post.setContent(postDTO.getContent());
        post.setCommentCount(0); // 초기 댓글 수

        // 5. PostImage 리스트 생성 (정렬 순서 유지)
        List<PostImage> imageEntities = new ArrayList<>();
        List<String> urls = postDTO.getImageUrls();

        if (urls != null) {
            for (int i = 0; i < urls.size(); i++) {
                PostImage img = new PostImage();
                img.setPost(post);
                img.setImageUrl(urls.get(i));
                img.setImageOrder(i);
                imageEntities.add(img);
            }
        }

        post.setImages(imageEntities);

        // 6. 저장
        postRepository.save(post);
    }
}

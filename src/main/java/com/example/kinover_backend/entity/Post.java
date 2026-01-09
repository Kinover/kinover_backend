// src/main/java/com/example/kinover_backend/entity/Post.java
package com.example.kinover_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

@Getter
@Setter
@Entity
public class Post {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID postId;

    @ManyToOne
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostImage> images = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(
            name = "created_at",
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP",
            updatable = false,
            insertable = false
    )
    private Date createdAt;

    private int commentCount = 0;

    // =========================
    // ✅ orphanRemoval 안전 헬퍼
    // - "컬렉션 참조 유지" + 내용만 변경
    // =========================
    public void clearImages() {
        if (this.images == null) return;
        this.images.clear();
    }

    public void addImage(PostImage image) {
        if (image == null) return;
        if (this.images == null) this.images = new ArrayList<>();
        this.images.add(image);
        image.setPost(this);
    }
}

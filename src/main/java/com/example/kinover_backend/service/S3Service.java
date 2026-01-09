// src/main/java/com/example/kinover_backend/service/S3Service.java
package com.example.kinover_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.net.URL;
import java.time.Duration;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${s3.bucket}")
    private String bucketName;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "video/mp4",
            "video/quicktime" // .mov
    );

    /** 파일 업로드용 presigned PUT URL 생성 */
    public URL generatePresignedUrl(String fileName, String contentType) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName is blank");
        }

        String ct = normalizeContentType(fileName, contentType);

        if (!ALLOWED_CONTENT_TYPES.contains(ct)) {
            throw new IllegalArgumentException("허용되지 않는 contentType: " + ct);
        }

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentType(ct)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        return presignedRequest.url();
    }

    /** S3 객체 삭제 */
    public void deleteImageFromS3(String key) {
        if (key == null || key.isBlank()) return;

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
    }

    // =========================
    // Helpers
    // =========================

    private String normalizeContentType(String fileName, String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return inferContentTypeByName(fileName);
        }
        return contentType.trim().toLowerCase();
    }

    private String inferContentTypeByName(String fileName) {
        String lower = (fileName == null ? "" : fileName.toLowerCase());

        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mov")) return "video/quicktime";

        return "application/octet-stream";
    }
}

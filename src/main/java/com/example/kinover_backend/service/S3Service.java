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

    @Value("${s3.bucket}")
    private String bucketName;

    // 필요하면 늘려
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

        String ct = normalizeContentType(contentType);

        // 보안상: 허용 타입만
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
    public void deleteImageFromS3(String fileName) {
        if (fileName == null || fileName.isBlank()) return;

        try (S3Client s3Client = S3Client.create()) {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build());
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            // 기본값은 이미지로 두거나, 아예 예외로 막아도 됨(권장)
            return "image/jpeg";
        }
        return contentType.trim().toLowerCase();
    }
}

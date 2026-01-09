// src/main/java/com/example/kinover_backend/controller/ImageController.java
package com.example.kinover_backend.controller;

import com.example.kinover_backend.service.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Tag(name = "미디어 업로드 Controller", description = "이미지/영상 업로드를 위한 S3 Presigned URL을 제공합니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/image")
public class ImageController {

    private final S3Service s3Service;

    @Operation(
            summary = "여러 미디어 업로드용 Presigned URL 요청",
            description = """
                    1) 신 포맷: { "files": [ { "fileName": "...", "contentType": "image/jpeg|video/mp4|video/quicktime|..." }, ... ] }
                    2) 구 포맷(호환): { "fileNames": [ "...", "..." ] }  (이 경우 contentType은 파일 확장자로 추론)
                    """
    )
    @ApiResponse(responseCode = "200", description = "Presigned URL 리스트 반환")
    @PostMapping("/upload-urls")
    public ResponseEntity<List<String>> getPresignedUrls(@RequestBody Map<String, Object> body) {

        // ✅ 1) 신 포맷: files
        Object filesObj = body.get("files");
        if (filesObj instanceof List<?> files && !files.isEmpty()) {

            List<String> urls = new ArrayList<>();

            for (Object item : files) {
                if (!(item instanceof Map<?, ?> m)) {
                    // ✅ 여기서 500 터지지 말고 400으로 컷
                    return ResponseEntity.badRequest().build();
                }

                Object fn = m.get("fileName");
                Object ct = m.get("contentType");

                String fileName = fn == null ? null : String.valueOf(fn);
                String contentType = ct == null ? null : String.valueOf(ct);

                try {
                    URL url = s3Service.generatePresignedUrl(fileName, contentType);
                    urls.add(url.toString());
                } catch (IllegalArgumentException e) {
                    // ✅ 허용되지 않는 타입/빈 파일명 등은 400
                    return ResponseEntity.badRequest().build();
                }
            }

            return ResponseEntity.ok(urls);
        }

        // ✅ 2) 구 포맷: fileNames
        Object fileNamesObj = body.get("fileNames");
        if (fileNamesObj instanceof List<?> fileNames && !fileNames.isEmpty()) {

            List<String> urls = new ArrayList<>();
            for (Object o : fileNames) {
                String fileName = String.valueOf(o);
                try {
                    URL url = s3Service.generatePresignedUrl(fileName, null);
                    urls.add(url.toString());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().build();
                }
            }

            return ResponseEntity.ok(urls);
        }

        return ResponseEntity.badRequest().build();
    }
}

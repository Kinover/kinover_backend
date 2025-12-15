package com.example.kinover_backend.controller;

import com.example.kinover_backend.service.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "미디어 업로드 Controller", description = "이미지/영상 업로드를 위한 S3 Presigned URL을 제공합니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/image")
public class ImageController {

    private final S3Service s3Service;

    // ✅ 이미지/영상 presigned URL 요청 (신/구 포맷 호환)
    @Operation(
            summary = "여러 미디어 업로드용 Presigned URL 요청",
            description = """
                    1) 신 포맷: { "files": [ { "fileName": "...", "contentType": "image/jpeg|video/mp4|..." }, ... ] }
                    2) 구 포맷(호환): { "fileNames": [ "...", "..." ] }  (이 경우 contentType은 기본값으로 처리됨)
                    """
    )
    @ApiResponse(responseCode = "200", description = "Presigned URL 리스트 반환")
    @PostMapping("/upload-urls")
    public ResponseEntity<List<String>> getPresignedUrls(@RequestBody Map<String, Object> body) {

        // ✅ 1) 신 포맷: files
        Object filesObj = body.get("files");
        if (filesObj instanceof List<?> files && !files.isEmpty()) {

            List<String> urls = files.stream()
                    .map(item -> (Map<?, ?>) item)
                    .map(m -> {
                        Object fn = m.get("fileName");
                        Object ct = m.get("contentType");
                        String fileName = fn == null ? null : String.valueOf(fn);
                        String contentType = ct == null ? null : String.valueOf(ct);
                        return s3Service.generatePresignedUrl(fileName, contentType);
                    })
                    .map(URL::toString)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(urls);
        }

        // ✅ 2) 구 포맷: fileNames (기존 클라 호환)
        Object fileNamesObj = body.get("fileNames");
        if (fileNamesObj instanceof List<?> fileNames && !fileNames.isEmpty()) {
            List<String> urls = fileNames.stream()
                    .map(String::valueOf)
                    .map(fileName -> s3Service.generatePresignedUrl(fileName, null)) // contentType 없으면 서비스 기본값
                    .map(URL::toString)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(urls);
        }

        return ResponseEntity.badRequest().build();
    }
}

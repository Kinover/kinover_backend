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

@Tag(name = "이미지 업로드 Controller", description = "이미지 업로드를 위한 S3의 임시 url을 제공합니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/image")
public class ImageController {

    private final S3Service s3Service;

    // 다중 이미지 presigned URL 요청
    @Operation(
            summary = "여러 이미지 업로드용 Presigned URL 요청",
            description = "이미지 파일 이름 목록을 받아, 각 파일에 대해 S3 업로드용 presigned URL을 반환합니다. 클라이언트는 해당 URL들에 직접 PUT 요청하여 이미지를 업로드할 수 있습니다."
    )
    @ApiResponse(responseCode = "200", description = "Presigned URL 리스트 반환")
    @PostMapping("/upload-urls")
    public ResponseEntity<List<String>> getPresignedUrls(@RequestBody Map<String, List<String>> body) {
        List<String> fileNames = body.get("fileNames");
        if (fileNames == null || fileNames.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<String> urls = fileNames.stream()
                .map(s3Service::generatePresignedUrl)
                .map(URL::toString)
                .collect(Collectors.toList());

        return ResponseEntity.ok(urls);
    }
}
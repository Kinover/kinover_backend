package com.example.kinover_backend.controller;

import com.example.kinover_backend.service.S3Service;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URL;
import java.util.Map;

@Tag(name = "이미지 업로드 Controller", description = "이미지 업로드를 위한 S3의 임시 url을 제공합니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/image")
public class ImageController {

    private final S3Service s3Service;

    @PostMapping("/upload-url")
    public ResponseEntity<String> getPresignedUrl(@RequestBody Map<String, String> body) {
        String fileName = body.get("fileName");
        URL url = s3Service.generatePresignedUrl(fileName);
        return ResponseEntity.ok(url.toString());
    }
}

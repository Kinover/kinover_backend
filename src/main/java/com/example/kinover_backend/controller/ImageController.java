package com.example.kinover_backend.controller;

import com.example.kinover_backend.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URL;
import java.util.Map;

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

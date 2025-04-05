package com.example.kinover_backend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "핑 Controller", description = "서버 상태 확인용 핑 API")
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PingController {

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }
}


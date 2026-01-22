package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponseDTO> badRequest(BadRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponseDTO("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponseDTO> unauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponseDTO("UNAUTHORIZED", e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponseDTO> forbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponseDTO("FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponseDTO("NOT_FOUND", e.getMessage()));
    }

    // ✅ (추가) 너 서비스 코드에서 엄청 많이 나오는 형태
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> illegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponseDTO("BAD_REQUEST", e.getMessage()));
    }

    /**
     * ✅ (추가/임시) RuntimeException을 전부 500으로 박아버리면 디버깅 지옥임
     * 지금 코드가 RuntimeException("게시글 없음") 같은 걸 던지니까,
     * 최소한 흔한 메시지는 404/403으로 내려주자.
     *
     * 장기적으로는 RuntimeException 대신 커스텀 예외로 교체하는 게 정석.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponseDTO> runtime(RuntimeException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().trim();

        // 메시지 기반 임시 매핑 (너 현재 서비스 메시지 기준)
        if (msg.contains("권한 없음")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponseDTO("FORBIDDEN", msg));
        }
        if (msg.contains("게시글 없음") || msg.contains("가족 정보 없음") || msg.contains("이미지 없음")
                || msg.contains("존재하지 않는")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponseDTO("NOT_FOUND", msg));
        }
        if (msg.contains("작성자만 수정")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponseDTO("FORBIDDEN", msg));
        }

        // 여기는 진짜로 애매한 런타임 -> 일단 400으로 낮춰서 프론트 디버깅 가능하게(선택)
        // "서버가 터졌다"보다 "요청/상태가 이상하다"가 더 많은 케이스임
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponseDTO("BAD_REQUEST", msg.isEmpty() ? "요청 처리 중 오류" : msg));
    }

    /**
     * ✅ 진짜 예상 못한 서버 에러만 처리
     * - Swagger /v3/api-docs, /swagger-ui 요청은 springdoc 내부에서 처리하도록 예외를 다시 던짐
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> unknown(Exception e, HttpServletRequest request) throws Exception {
        String uri = request.getRequestURI();

        if (uri != null && (uri.startsWith("/v3/api-docs") || uri.startsWith("/swagger-ui"))) {
            throw e; // springdoc 쪽은 그대로 던짐
        }

        log.error("[INTERNAL_SERVER_ERROR] uri={}", uri, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponseDTO("INTERNAL_SERVER_ERROR", "서버 오류"));
    }
}

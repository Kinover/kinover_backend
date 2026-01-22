// src/main/java/com/example/kinover_backend/controller/GlobalExceptionHandler.java
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

    /**
     * ✅ 정말 예상 못한 서버 에러만 처리
     * - Swagger / Spring 내부 문서화 흐름(/v3/api-docs, /swagger-ui)은 여기서 500으로 덮지 않게 예외를 그대로 던짐
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> unknown(Exception e, HttpServletRequest request) throws Exception {

        final String uri = (request != null) ? request.getRequestURI() : "";

        // ✅ Swagger 관련 요청은 예외를 덮지 말고 그대로 위로 던져서, springdoc이 정상 처리/표시하게 둠
        if (uri.startsWith("/v3/api-docs") || uri.startsWith("/swagger-ui")) {
            throw e;
        }

        log.error("[INTERNAL_SERVER_ERROR] uri={}", uri, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponseDTO("INTERNAL_SERVER_ERROR", "서버 오류"));
    }
}

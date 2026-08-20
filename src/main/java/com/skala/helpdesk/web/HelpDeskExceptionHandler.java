package com.skala.helpdesk.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class HelpDeskExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(HelpDeskExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> notFound(IllegalArgumentException e) {
        return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception e) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        log.error("[{}] 예상치 못한 오류", traceId, e);
        return ResponseEntity.status(503).body(Map.of(
                "message", "일시적인 오류입니다. 잠시 후 다시 시도해주세요.",
                "traceId", traceId));
    }
}

package com.skala.helpdesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Phase 1 — 설정 외부화 (교안 314쪽).
 *
 * <p>공급자·모델·임계값을 전부 설정으로 뺀다 — 코드에 상수를 남기지 않는다는 원칙을
 * 그대로 따른다. {@code application.yml}의 {@code helpdesk.*} 아래에 대응한다.
 */
@ConfigurationProperties(prefix = "helpdesk")
public record HelpDeskProperties(
        Rag rag,
        Memory memory,
        String primaryModel,
        String fallbackModel,
        String tenantId) {

    public record Rag(int topK, double threshold) {}

    public record Memory(int max) {}
}

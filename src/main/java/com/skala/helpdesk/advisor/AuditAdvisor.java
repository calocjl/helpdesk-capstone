package com.skala.helpdesk.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Phase 7 — 감사 로깅 (교안 314쪽 Advisor 체인의 가장 바깥, order 0).
 *
 * <p>모든 요청·응답이 지나가는 길목이다. 한 요청을 traceId로 처음부터 끝까지
 * 추적할 수 있게, 요청이 들어올 때 MDC에 traceId를 심고 응답이 끝나면 지운다.
 */
@Component
public class AuditAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(AuditAdvisor.class);

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", traceId);

        Object userId = request.context() != null ? request.context().get("userId") : null;
        log.info("[AUDIT] 요청 user={} q={}", userId, request.prompt().getContents());
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        log.info("[AUDIT] 응답 완료");
        MDC.remove("traceId");
        return response;
    }

    @Override
    public String getName() {
        return "audit";
    }

    /** 0 — 가장 바깥. 감사는 항상 전체를 감싸야 한다. */
    @Override
    public int getOrder() {
        return 0;
    }
}

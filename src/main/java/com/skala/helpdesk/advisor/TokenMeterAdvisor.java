package com.skala.helpdesk.advisor;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Phase 8 — 토큰·지연 계측 (교안 322쪽 "운영자: 어제 비용이 왜 늘었지?").
 *
 * <p>비기능요구(311쪽) "질의당 평균 토큰 상한 준수"를 확인하려면, 단순 누적 카운터로는
 * 부족하다 — "평균"을 보려면 각 호출의 토큰 수 분포가 필요하다. 그래서 누적 카운터
 * (ai.tokens)와 별개로, 호출 1건당 총 토큰을 기록하는 분포(ai.tokens.perCall)도 남긴다.
 *
 * <p>확인:
 * <pre>
 *   GET /actuator/metrics/ai.tokens.perCall     — MEAN(평균)이 상한 이내인지 확인
 *   GET /actuator/metrics/ai.latency             — 지연(phase=model)
 * </pre>
 */
@Component
public class TokenMeterAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TokenMeterAdvisor.class);

    /** 비기능요구의 "평균 토큰 상한" 기준값. README·보고서에서 이 값과 실측 MEAN을 비교한다. */
    public static final int TOKEN_BUDGET_PER_CALL = 1500;

    private final MeterRegistry registry;

    public TokenMeterAdvisor(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long started = System.nanoTime();
        ChatClientResponse response = chain.nextCall(request);
        long elapsedNanos = System.nanoTime() - started;

        registry.timer("ai.latency", "phase", "model").record(elapsedNanos, TimeUnit.NANOSECONDS);

        if (response.chatResponse() != null && response.chatResponse().getMetadata() != null) {
            Usage usage = response.chatResponse().getMetadata().getUsage();
            if (usage != null) {
                int prompt = nullSafe(usage.getPromptTokens());
                int completion = nullSafe(usage.getCompletionTokens());
                int total = prompt + completion;

                registry.counter("ai.tokens", "type", "prompt", "feature", "chat").increment(prompt);
                registry.counter("ai.tokens", "type", "completion", "feature", "chat").increment(completion);
                registry.summary("ai.tokens.perCall").record(total);   // 평균 계산용 분포

                if (total > TOKEN_BUDGET_PER_CALL) {
                    log.warn("토큰 상한 초과 total={} budget={}", total, TOKEN_BUDGET_PER_CALL);
                    registry.counter("ai.tokens.budget.exceeded").increment();
                }

                log.debug("토큰 prompt={} completion={} total={} 지연={}ms",
                        prompt, completion, total, elapsedNanos / 1_000_000);
            }
        }
        return response;
    }

    private int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }

    @Override
    public String getName() {
        return "tokenMeter";
    }

    /** 900 — 가장 안쪽(모델 바로 앞). 실제 모델 호출 시간·토큰만 정확히 잰다. */
    @Override
    public int getOrder() {
        return 900;
    }
}

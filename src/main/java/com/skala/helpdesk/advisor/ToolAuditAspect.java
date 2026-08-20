package com.skala.helpdesk.advisor;

import com.skala.helpdesk.ops.ToolUsageTracker;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 비기능요구(311쪽) "모든 도구 호출 감사"를 AOP로 한 곳에서 처리한다.
 * {@code @Tool}이 붙은 모든 메서드 호출을 가로채 기록한다 — 도구마다 로깅 코드를
 * 넣지 않아도 일관된 추적이 생긴다.
 *
 * <p>확인: {@code GET /actuator/metrics/ai.tool.calls?tag=result:fail}
 *
 * <p><b>주의</b>: 인자에 개인정보가 들어올 수 있다. 아주 단순한 마스킹만 적용했다 —
 * 운영에서는 도메인에 맞는 마스킹 규칙과 보존 기간을 먼저 정해야 한다.
 */
@Aspect
@Component
public class ToolAuditAspect {

    private static final Logger audit = LoggerFactory.getLogger("AI_TOOL_AUDIT");

    private final MeterRegistry registry;

    public ToolAuditAspect(MeterRegistry registry) {
        this.registry = registry;
    }

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object auditToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String tool = joinPoint.getSignature().getName();
        String args = mask(Arrays.toString(joinPoint.getArgs()));
        long started = System.nanoTime();
        ToolUsageTracker.markUsed();   // AnswerDto.toolUsed 계산용

        try {
            Object result = joinPoint.proceed();
            audit.info("tool={} args={} status=OK elapsedMs={}",
                    tool, args, (System.nanoTime() - started) / 1_000_000);
            registry.counter("ai.tool.calls", "tool", tool, "result", "ok").increment();
            return result;

        } catch (Throwable e) {
            audit.warn("tool={} args={} status=FAIL error={}", tool, args, e.toString());
            registry.counter("ai.tool.calls", "tool", tool, "result", "fail").increment();
            throw e;
        }
    }

    private String mask(String raw) {
        return raw
                .replaceAll("\\d{6}-\\d{7}", "******-*******")
                .replaceAll("\\d{4}-\\d{4}-\\d{4}-\\d{4}", "****-****-****-****")
                .replaceAll("[\\w.+-]+@[\\w-]+\\.[\\w.]+", "***@***");
    }
}

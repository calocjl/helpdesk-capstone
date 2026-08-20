package com.skala.helpdesk.chat;

import com.skala.helpdesk.advisor.ContentBlockedException;
import com.skala.helpdesk.config.HelpDeskProperties;
import com.skala.helpdesk.ops.FaultInjector;
import com.skala.helpdesk.ops.SemanticCacheService;
import com.skala.helpdesk.ops.SimulatedOutageException;
import com.skala.helpdesk.ops.ToolUsageTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Phase 3 — RAG 답변과 출처 표기 (교안 316쪽) + Phase 5 — 메모리와 멀티턴 (320쪽)을
 * 하나의 서비스로 통합했다. 비기능요구(311쪽) "주모델 장애 시 폴백"도 여기서 처리한다.
 *
 * <p>Advisor가 근거를 넣어주지만, 출처는 우리가 응답 컨텍스트에서 직접 꺼내 붙여야 한다.
 * 근거가 없으면 모른다고 답하게 하는 것이 지어내기를 막는 첫 방어선이다.
 */
@Service
public class HelpDeskService {

    private static final Logger log = LoggerFactory.getLogger(HelpDeskService.class);

    private final ChatClient primary;
    private final ChatClient fallback;
    private final ChatMemory chatMemory;
    private final HelpDeskProperties props;
    private final FaultInjector faultInjector;
    private final SemanticCacheService cache;

    public HelpDeskService(@Qualifier("helpDeskClient") ChatClient primary,
                            @Qualifier("fallbackChatClient") ChatClient fallback,
                            ChatMemory chatMemory,
                            HelpDeskProperties props,
                            FaultInjector faultInjector,
                            SemanticCacheService cache) {
        this.primary = primary;
        this.fallback = fallback;
        this.chatMemory = chatMemory;
        this.props = props;
        this.faultInjector = faultInjector;
        this.cache = cache;
    }

    /**
     * Phase 5 — 대화 ID 규칙: 테넌트·사용자·세션을 한 곳에서 만든다. 섞이면 사고다.
     */
    public String conversationId(String userId, String sessionId) {
        return "%s:%s:%s".formatted(props.tenantId(), userId, sessionId);
    }

    /**
     * 동기 질의. 근거·출처·도구 사용 여부를 구조화해서 반환한다(Phase 6, AnswerDto).
     */
    public AnswerDto ask(String question, String userId, String sessionId) {
        return ask(question, userId, sessionId, false);
    }

    /**
     * @param bypassCache true면 시맨틱 캐시를 조회·저장하지 않는다. 부하테스트(LoadTest)처럼
     *                    "캐시 없이 매번 모델을 부르는 최악의 경우"를 재고 싶을 때 쓴다.
     *                    캐시 히트가 섞이면 P95가 인위적으로 좋게 나와 측정 의미가 없어진다.
     */
    public AnswerDto ask(String question, String userId, String sessionId, boolean bypassCache) {
        String conversationId = conversationId(userId, sessionId);
        ToolUsageTracker.reset();

        if (!bypassCache) {
            // 확장과제(307쪽) — 시맨틱 캐시: 뜻이 같은 질문이면 모델을 다시 부르지 않는다.
            var cached = cache.lookup(question);
            if (cached.isPresent()) {
                ToolUsageTracker.clear();
                return new AnswerDto(cached.get(), List.of(), false);
            }
        }

        try {
            ChatClientResponse response = callPrimary(question, userId, conversationId);
            List<Source> sources = extractSources(response);
            String text = response.chatResponse().getResult().getOutput().getText();
            boolean toolUsed = ToolUsageTracker.wasUsed();

            // 근거 문서로 답한 것(RAG)만 캐시에 남긴다 — 도구로 조회한 실시간·개인화 데이터는
            // 캐싱하면 다른 사용자·다른 시점에 오답을 돌려주게 되므로 절대 저장하지 않는다.
            if (!bypassCache && !sources.isEmpty() && !toolUsed) {
                cache.store(question, text);
            }

            return new AnswerDto(text, sources, toolUsed);

        } catch (ContentBlockedException e) {
            // SafetyAdvisor가 차단한 경우 — 모델을 호출하지 않고 고정 문구로 응답한다
            return new AnswerDto(e.getMessage(), List.of(), false);

        } catch (Exception primaryFailure) {
            // 비기능요구: 주모델 장애 시 폴백으로 응답을 지속한다
            log.warn("주 모델 호출 실패 — 폴백으로 전환합니다. cause={}", primaryFailure.toString());
            return callFallback(question, conversationId);

        } finally {
            ToolUsageTracker.clear();
        }
    }

    /**
     * 스트리밍 질의(Phase 6, SSE). 근거·도구 없이도 동작하도록 같은 클라이언트를 쓰되,
     * 토큰 단위로 흘려보낸다.
     */
    public Flux<String> stream(String question, String userId, String sessionId) {
        String conversationId = conversationId(userId, sessionId);

        if (faultInjector.isPrimaryDown()) {
            return fallback.prompt().user(question).stream().content();
        }

        return primary.prompt()
                .user(question)
                .toolContext(Map.of("userId", userId))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .onErrorResume(ex -> {
                    log.warn("스트리밍 중 주 모델 실패 — 폴백으로 전환합니다. cause={}", ex.toString());
                    return fallback.prompt().user(question).stream().content();
                });
    }

    /** 스트리밍 마지막에 출처를 별도 이벤트로 보낼 때 쓴다(ChatController에서 호출). */
    public List<Source> lastSources(String question, String userId, String sessionId) {
        try {
            ChatClientResponse response = callPrimary(question, userId, conversationId(userId, sessionId));
            return extractSources(response);
        } catch (Exception e) {
            return List.of();
        }
    }

    private ChatClientResponse callPrimary(String question, String userId, String conversationId) {
        if (faultInjector.isPrimaryDown()) {
            throw new SimulatedOutageException();   // 장애주입 테스트 — 실제 장애와 동일하게 처리된다
        }
        return primary.prompt()
                .user(question)
                .toolContext(Map.of("userId", userId))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .chatClientResponse();
    }

    private AnswerDto callFallback(String question, String conversationId) {
        try {
            String text = fallback.prompt()
                    .user(question)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
            return new AnswerDto(text, List.of(), false);
        } catch (Exception fallbackFailure) {
            log.error("폴백 클라이언트도 실패했습니다.", fallbackFailure);
            return new AnswerDto("죄송합니다, 지금은 답변을 드릴 수 없습니다. 잠시 후 다시 시도해주세요.",
                    List.of(), false);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Source> extractSources(ChatClientResponse response) {
        Object raw = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        List<Document> used = raw == null ? List.of() : (List<Document>) raw;
        return used.stream()
                .map(d -> new Source(
                        String.valueOf(d.getMetadata().get("source")),
                        String.valueOf(d.getMetadata().get("version"))))
                .distinct()
                .toList();
    }
}

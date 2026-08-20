package com.skala.helpdesk.ops;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 확장과제(교안 307쪽) — 시맨틱 캐시: 뜻이 같은 질문은 즉시 응답한다.
 *
 * <p>정책 문서와 같은 {@link VectorStore}를 재활용한다. 질문·답변 쌍을
 * {@code docType == "qa-cache"} 메타데이터로 저장해두고, 새 질문이 오면 먼저
 * 이 캐시를 유사도 검색한다 — 표현이 달라도(예: "반품 기한" ↔ "며칠 안에 반품하나요")
 * 임베딩 유사도가 높으면 모델을 다시 부르지 않고 저장된 답을 그대로 돌려준다.
 *
 * <p>정책 문서 검색({@code QuestionAnswerAdvisor})은 {@code docType == 'policy'}로만
 * 필터링되어 있어서, 캐시 항목이 실수로 RAG 근거로 섞여 들어가지 않는다 — 이것
 * 자체가 229쪽 확장과제 "메타데이터 필터로 범위를 강제한다"의 적용 사례이기도 하다.
 *
 * <p><b>주의</b>: 도구(Tool) 기반 답변(주문 조회 등)은 사용자·시점마다 다른 실시간
 * 데이터라서 캐싱하지 않는다. {@link com.skala.helpdesk.chat.HelpDeskService}가
 * 근거 문서가 있는(RAG) 답변만 캐시에 저장하도록 호출한다.
 */
@Service
public class SemanticCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);

    /** 이 이상 유사하면 "같은 질문"으로 본다. 너무 낮추면 다른 질문에 엉뚱한 캐시 답이 나간다. */
    private static final double CACHE_HIT_THRESHOLD = 0.95;

    private final VectorStore vectorStore;
    private final MeterRegistry registry;

    public SemanticCacheService(VectorStore vectorStore, MeterRegistry registry) {
        this.vectorStore = vectorStore;
        this.registry = registry;
    }

    public Optional<String> lookup(String question) {
        List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(1)
                .similarityThreshold(CACHE_HIT_THRESHOLD)
                .filterExpression("docType == 'qa-cache'")
                .build());

        if (hits.isEmpty()) {
            registry.counter("ai.cache", "result", "miss").increment();
            return Optional.empty();
        }

        registry.counter("ai.cache", "result", "hit").increment();
        Object answer = hits.get(0).getMetadata().get("answer");
        log.info("[CACHE] hit — score기준({}) 이상 유사 질문 발견", CACHE_HIT_THRESHOLD);
        return Optional.ofNullable((String) answer);
    }

    public void store(String question, String answer) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("docType", "qa-cache");
        meta.put("answer", answer);
        vectorStore.add(List.of(new Document(question, meta)));
        log.debug("[CACHE] 저장 완료 q={}", question);
    }
}

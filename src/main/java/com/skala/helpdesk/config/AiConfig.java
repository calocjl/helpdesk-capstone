package com.skala.helpdesk.config;

import com.skala.helpdesk.advisor.AuditAdvisor;
import com.skala.helpdesk.advisor.SafetyAdvisor;
import com.skala.helpdesk.advisor.TokenMeterAdvisor;
import com.skala.helpdesk.tools.OrderTools;
import com.skala.helpdesk.tools.PaymentTools;
import com.skala.helpdesk.tools.TicketTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Phase 1 — 설정과 ChatClient 조립 (교안 314쪽).
 *
 * <p>공급자·모델·임계값을 전부 {@link HelpDeskProperties}로 뺐다 — 코드에 상수를
 * 남기지 않는다. ChatClient 하나에 Advisor 체인 전체를 기본값으로 걸어둔다.
 *
 * <pre>
 *   요청:  Audit(0) → Safety(100) → SafeGuard → Memory(200) → QA(300) → Logger → TokenMeter(900) → 모델
 *   응답:  모델 → TokenMeter → Logger → QA → Memory → SafeGuard → Safety → Audit
 * </pre>
 *
 * <p>{@code fallbackChatClient}는 비기능요구(311쪽) "주모델 장애 시 폴백"을 위한
 * 두 번째 클라이언트다 — RAG·Tool 없이 시스템 프롬프트만 가진 가벼운 클라이언트로,
 * 주 모델 경로가 실패했을 때만 쓰인다.
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatMemoryRepository chatMemoryRepository() {
        // 개발·단일 인스턴스에서는 인메모리로 충분하다. 운영에서 인스턴스가 둘 이상이
        // 되는 순간 대화가 섞이므로, 그때는 JdbcChatMemoryRepository로 바꿔야 한다.
        return new InMemoryChatMemoryRepository();
    }

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository repository, HelpDeskProperties props) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(props.memory().max())          // 길어진 대화는 잘라 토큰을 통제한다
                .build();
    }

    private String systemPrompt() {
        try {
            Resource resource = new PathMatchingResourcePatternResolver()
                    .getResource("classpath:prompts/system.st");
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("prompts/system.st 를 읽을 수 없습니다", e);
        }
    }

    @Bean
    @Qualifier("helpDeskClient")
    public ChatClient helpDeskClient(ChatClient.Builder builder,
                                      VectorStore vs,
                                      ChatMemory memory,
                                      HelpDeskProperties props,
                                      OrderTools orderTools,
                                      TicketTools ticketTools,
                                      PaymentTools paymentTools,
                                      AuditAdvisor audit,
                                      SafetyAdvisor safety,
                                      TokenMeterAdvisor meter) {
        return builder
                .defaultSystem(systemPrompt())
                .defaultOptions(ChatOptions.builder()
                        .model(props.primaryModel())
                        .build())
                .defaultAdvisors(
                        audit,                                                     // order   0  가장 바깥
                        safety,                                                    // order 100  차단(패턴)
                        SafeGuardAdvisor.builder()                                 // order 150  차단(민감어)
                                .sensitiveWords(List.of("주민등록번호", "카드번호", "비밀번호"))
                                .failureResponse("죄송합니다. 민감정보가 포함된 요청은 처리할 수 없습니다.")
                                .order(150)
                                .build(),
                        MessageChatMemoryAdvisor.builder(memory)                   // order 200  기억
                                .order(200)
                                .build(),
                        QuestionAnswerAdvisor.builder(vs)                          // order 300  근거 검색
                                .searchRequest(SearchRequest.builder()
                                        .topK(props.rag().topK())
                                        .similarityThreshold(props.rag().threshold())
                                        // 확장과제(229쪽) — 메타데이터 필터로 검색 범위를 강제한다.
                                        // 프롬프트로 "정책 문서만 근거로 써"라고 부탁하는 게 아니라,
                                        // 검색 단계에서 docType != 'policy'인 것(예: 시맨틱 캐시 항목)은
                                        // 아예 후보에도 오르지 못하게 코드로 막는다.
                                        .filterExpression("docType == 'policy'")
                                        .build())
                                .order(300)
                                .build(),
                        new SimpleLoggerAdvisor(),                                 // order 400  최종 요청 로깅
                        meter)                                                     // order 900  모델 바로 앞
                .defaultTools(orderTools, ticketTools, paymentTools)
                .build();
    }

    /**
     * 폴백 클라이언트 — 주 모델 경로가 실패했을 때만 쓰인다.
     * RAG·Tool 없이 시스템 프롬프트만으로 최소한의 응답을 지속하는 것이 목적이라,
     * Advisor 체인을 최소화했다(감사 로그만 남긴다).
     */
    @Bean
    @Qualifier("fallbackChatClient")
    public ChatClient fallbackChatClient(ChatClient.Builder builder,
                                          HelpDeskProperties props,
                                          AuditAdvisor audit) {
        return builder
                .defaultSystem("""
                        너는 단풍이야기 고객센터의 임시 응대 도우미다. 지금은 일부 기능이 제한된
                        상태이니, 정확한 정보 대신 "잠시 후 다시 시도해달라"는 안내와 함께
                        간단한 공감 표현만 제공한다. 근거 없는 사실을 지어내지 않는다.
                        """)
                .defaultOptions(ChatOptions.builder()
                        .model(props.fallbackModel())
                        .build())
                .defaultAdvisors(audit)
                .build();
    }
}

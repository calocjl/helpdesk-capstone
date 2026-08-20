package com.skala.helpdesk.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.helpdesk.chat.AnswerDto;
import com.skala.helpdesk.chat.HelpDeskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.time.Duration;

/**
 * Phase 6 — 구조화 응답 API와 SSE (교안 321쪽).
 *
 * <p>화면이 쓰기 좋게 답변·출처·도구 사용 여부를 나눠 반환한다. 긴 답변은 SSE
 * 스트리밍으로 첫 글자를 빨리 보여주고, 스트리밍 응답에도 출처를 마지막 이벤트로
 * 함께 내보낸다.
 *
 * <p>{@code Principal user}는 Spring Security 인증(Phase 7, HTTP Basic)에서 온다 —
 * Swagger에서 테스트하려면 우측 상단 Authorize에서 user1/pass로 로그인해야 한다.
 */
@RestController
@RequestMapping("/api/chat")
@Tag(name = "종합실습 · 상담 채팅")
public class ChatController {

    private final HelpDeskService service;
    private final ObjectMapper objectMapper;

    public ChatController(HelpDeskService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    public record AskRequest(String question, String sessionId, Boolean bypassCache) {}

    @PostMapping
    @Operation(summary = "상담 질문 (동기)", description = "규정 질문엔 RAG로, 주문·티켓엔 도구로 답한다. 구조화된 AnswerDto를 반환한다. bypassCache=true면 시맨틱 캐시를 건너뛴다(부하테스트용).")
    public AnswerDto ask(@RequestBody AskRequest req, Principal user) {
        boolean bypass = req.bypassCache() != null && req.bypassCache();
        return service.ask(req.question(), user.getName(), req.sessionId(), bypass);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "상담 질문 (SSE 스트리밍)", description = "토큰이 생성되는 대로 흘려보내고, 마지막에 출처를 별도 이벤트로 보낸다.")
    public Flux<ServerSentEvent<String>> stream(@RequestBody AskRequest req, Principal user) {
        String userId = user.getName();

        return service.stream(req.question(), userId, req.sessionId())
                .map(chunk -> ServerSentEvent.<String>builder(chunk).event("token").build())
                .concatWith(Mono.fromCallable(() ->
                        ServerSentEvent.<String>builder(sourcesAsJson(req, userId)).event("sources").build()))
                .timeout(Duration.ofSeconds(60));
    }

    private String sourcesAsJson(AskRequest req, String userId) {
        try {
            return objectMapper.writeValueAsString(
                    service.lastSources(req.question(), userId, req.sessionId()));
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}

package com.skala.helpdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 종합실습 — 단풍이야기 HelpDesk AI (교안 309~323쪽).
 *
 * <p>지금까지 배운 것(RAG, Tool, Memory, Advisor, 승인 게이트, 관찰가능성, 보안)을
 * 하나의 흐름으로 조립한 최종 산출물이다. 새로운 기술은 없다 — 배운 것을 협력시켰을 뿐이다(323쪽).
 *
 * <p>핵심 엔드포인트
 * <ul>
 *   <li>{@code POST /api/chat} — 동기, 구조화 응답(AnswerDto)</li>
 *   <li>{@code POST /api/chat/stream} — SSE 스트리밍</li>
 *   <li>{@code POST /api/admin/ingest} — 정책 문서 4종 인제스트 (먼저 실행)</li>
 *   <li>{@code GET /api/admin/chunks} — 인제스트 품질 확인(무엇이 들어갔는지 눈으로 본다)</li>
 *   <li>{@code GET /api/admin/tickets/pending}, {@code POST /api/admin/tickets/{no}/approve} — 사람 전용 승인</li>
 *   <li>{@code POST /api/admin/fault/primary-down} — 장애주입 테스트(폴백 동작 확인용)</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class HelpDeskApplication {
    public static void main(String[] args) {
        SpringApplication.run(HelpDeskApplication.class, args);
    }
}

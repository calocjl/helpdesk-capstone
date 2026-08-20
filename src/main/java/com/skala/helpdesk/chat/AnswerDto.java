package com.skala.helpdesk.chat;

import java.util.List;

/**
 * Phase 6 — 구조화 응답 (교안 321쪽).
 * 화면이 쓰기 좋게 답변·출처·도구 사용 여부를 나눠 반환한다.
 */
public record AnswerDto(String answer, List<Source> sources, boolean toolUsed) {
}

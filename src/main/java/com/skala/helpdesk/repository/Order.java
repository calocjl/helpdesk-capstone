package com.skala.helpdesk.repository;

import java.time.LocalDate;

/**
 * 주문 도메인. {@code ownerId}는 API 응답으로 그대로 나가지 않는다 —
 * 도구({@link com.skala.helpdesk.tools.OrderTools})가 문장으로 요약해서 돌려줄 뿐이다.
 */
public record Order(String id, String ownerId, String item, String status, LocalDate eta) {
}

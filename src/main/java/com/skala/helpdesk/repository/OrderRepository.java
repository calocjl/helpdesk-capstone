package com.skala.helpdesk.repository;

import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Phase 4 — 인메모리 주문 저장소 (교안 317쪽).
 *
 * <p>테스트 데이터:
 * <ul>
 *   <li>12345, 12346 — user1 소유</li>
 *   <li>99999 — user2 소유 (남의 주문·ID 주입 시나리오)</li>
 * </ul>
 */
@Repository
public class OrderRepository {

    private final Map<String, Order> store = Map.of(
            "12345", new Order("12345", "user1", "레드큐브 세트", "지급대기", LocalDate.of(2026, 8, 24)),
            "12346", new Order("12346", "user1", "코디 패키지", "지급완료", LocalDate.of(2026, 8, 16)),
            "99999", new Order("99999", "user2", "환생의 불꽃 5개입", "지급대기", LocalDate.of(2026, 8, 25)));

    /**
     * id·ownerId가 모두 일치할 때만 반환한다. 없는 주문과 남의 주문을 구분해서
     * 알려주지 않는다 — 그 구분 자체가 정보 노출이다.
     */
    public Optional<Order> findOwned(String id, String ownerId) {
        Order order = store.get(id);
        if (order == null || !order.ownerId().equals(ownerId)) {
            return Optional.empty();
        }
        return Optional.of(order);
    }
}

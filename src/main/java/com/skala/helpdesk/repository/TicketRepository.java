package com.skala.helpdesk.repository;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 교환·환불 티켓 저장소.
 *
 * <p>도구({@code TicketTools})는 이 저장소에 PENDING 상태로만 접수할 수 있다.
 * {@link #approve(String)}는 도구 목록에 없는, 관리자 API에서만 호출되는 메서드다 —
 * 모델은 이 메서드에 닿을 방법이 없다.
 */
@Repository
public class TicketRepository {

    private final Map<String, Ticket> store = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(0);

    public Ticket create(String orderId, String userId, String type, String reason) {
        String no = "MT-%04d".formatted(sequence.incrementAndGet());
        Ticket ticket = new Ticket(no, orderId, userId, type, reason, TicketStatus.PENDING, Instant.now());
        store.put(no, ticket);
        return ticket;
    }

    public Optional<Ticket> find(String no) {
        return Optional.ofNullable(store.get(no));
    }

    public List<Ticket> pending() {
        return store.values().stream()
                .filter(t -> t.status() == TicketStatus.PENDING)
                .toList();
    }

    public Optional<Ticket> approve(String no) {
        return Optional.ofNullable(store.computeIfPresent(no, (k, t) -> t.approved()));
    }
}

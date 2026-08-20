package com.skala.helpdesk.repository;

import java.time.Instant;

/** 교환·환불 접수 티켓. {@link TicketStatus#PENDING}인 동안은 실제 처리가 안 된 상태다. */
public record Ticket(String no, String orderId, String userId, String type, String reason,
                      TicketStatus status, Instant requestedAt) {

    public Ticket approved() {
        return new Ticket(no, orderId, userId, type, reason, TicketStatus.APPROVED, requestedAt);
    }
}

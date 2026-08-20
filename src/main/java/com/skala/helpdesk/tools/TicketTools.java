package com.skala.helpdesk.tools;

import com.skala.helpdesk.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Phase 4 — Tool 연동: 티켓 접수 (교안 317~318쪽).
 *
 * <p>쓰기 도구는 승인 절차를 거친다. 이 도구가 하는 최대치는 "PENDING 티켓을
 * 하나 만드는 것"이다 — 실제 처리(승인)는 {@code /api/admin/tickets/{no}/approve}
 * (사람 전용 API, 도구 목록에 없어 모델이 부를 수 없음)에서만 이루어진다.
 */
@Component
public class TicketTools {

    private static final Logger log = LoggerFactory.getLogger(TicketTools.class);

    private final TicketRepository tickets;

    public TicketTools(TicketRepository tickets) {
        this.tickets = tickets;
    }

    @Tool(description = "교환·환불 티켓을 접수한다. 처리는 담당자 승인 후 진행된다.")
    public String createTicket(
            @ToolParam(description = "주문번호") String orderId,
            @ToolParam(description = "EXCHANGE|REFUND") String type,
            @ToolParam(description = "사유") String reason,
            ToolContext ctx) {

        String userId = currentUser(ctx);
        var t = tickets.create(orderId, userId, type, reason);
        log.warn("[APPROVAL] TICKET_REQUESTED no={} order={} type={} by={} reason={}",
                t.no(), orderId, type, userId, reason);

        return "티켓 %s 를 접수했습니다. 승인 후 처리됩니다.".formatted(t.no());
    }

    @Tool(description = "접수된 티켓의 처리 상태를 조회한다.")
    public String ticketStatus(@ToolParam(description = "티켓 번호(예: MT-0001)") String ticketNo) {
        return tickets.find(ticketNo)
                .map(t -> "티켓 %s · 유형 %s · 상태 %s".formatted(t.no(), t.type(), t.status()))
                .orElse("해당 티켓 번호를 찾을 수 없습니다.");
    }

    private String currentUser(ToolContext context) {
        Object userId = context == null ? null : context.getContext().get("userId");
        if (userId == null) {
            throw new IllegalStateException("toolContext에 userId가 없다 — 호출부 설정을 확인하라");
        }
        return userId.toString();
    }
}

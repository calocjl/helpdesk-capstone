package com.skala.helpdesk.tools;

import com.skala.helpdesk.repository.Order;
import com.skala.helpdesk.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Phase 4 — Tool 연동: 주문 조회 (교안 317~318쪽).
 *
 * <p>문서로 답할 수 없는 것(실시간 주문 상태)은 도구로 가져온다. 소유자 검증은
 * 도구 안에서 한다 — 모델이 넘긴 orderId는 사용자의 것이 아닐 수 있다(318쪽 주의).
 *
 * <p>설명(description)에 "언제 쓰는지"만 명확히 하고, 권한 판단을 모델에게 미리
 * 맡기는 문구는 넣지 않는다 — Day 3 실습에서 겪은 것처럼, 그런 문구가 있으면
 * 모델이 도구 호출 자체를 생략하고 스스로 판단해버릴 수 있다.
 */
@Component
public class OrderTools {

    private static final Logger log = LoggerFactory.getLogger(OrderTools.class);

    private final OrderRepository orders;

    public OrderTools(OrderRepository orders) {
        this.orders = orders;
    }

    @Tool(description = """
            주문번호로 상태와 지급 예정일을 조회한다.
            사용자가 주문번호를 말하거나 '내 주문', '언제 받나요' 처럼 물으면 이 도구를 사용해 확인한다.
            """)
    public String orderStatus(
            @ToolParam(description = "주문번호(숫자 5자리)") String orderId,
            ToolContext ctx) {

        String userId = currentUser(ctx);
        log.info("[TOOL] orderStatus orderId={} by={}", orderId, userId);

        return orders.findOwned(orderId, userId)
                .map(o -> "주문 %s · 품목 %s · 상태 %s · 지급예정 %s"
                        .formatted(o.id(), o.item(), o.status(), o.eta()))
                .orElse("해당 주문을 찾을 수 없습니다.");
    }

    private String currentUser(ToolContext context) {
        Object userId = context == null ? null : context.getContext().get("userId");
        if (userId == null) {
            throw new IllegalStateException("toolContext에 userId가 없다 — 호출부 설정을 확인하라");
        }
        return userId.toString();
    }
}

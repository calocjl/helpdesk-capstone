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
 * 확장과제(교안 307쪽) — 병렬 도구 호출 데모.
 *
 * <p>단풍이야기 아이템은 실물 배송이 아니라 계정에 즉시 지급되는 디지털 재화라서,
 * "택배사·운송장" 개념은 어색하다. 대신 "결제 수단·영수증"처럼 주문 상태와는
 * 독립적으로 조회 가능한 정보를 병렬 도구 호출 데모 대상으로 삼았다.
 *
 * <p>"주문 12345 상태랑 결제 수단도 같이 알려줘"처럼 두 도구가 동시에 필요한
 * 질문을 던지면, {@link OrderTools#orderStatus}와 이 도구가 <b>같은 응답 턴에서
 * 병렬로 호출</b>된다 — 서로 독립적이고 부작용 없는(읽기 전용) 도구 2개를
 * 등록해두기만 하면 Spring AI가 자동으로 해준다(244쪽 "병렬 Tool 호출" 참고).
 *
 * <p>확인 방법: 터미널에서 이 도구와 {@code orderStatus}의 로그 타임스탬프가
 * 거의 동시에 찍히는지 보면 된다(순차 호출이었다면 시간 차이가 나야 정상이다).
 */
@Component
public class PaymentTools {

    private static final Logger log = LoggerFactory.getLogger(PaymentTools.class);

    private final OrderRepository orders;

    public PaymentTools(OrderRepository orders) {
        this.orders = orders;
    }

    @Tool(description = "주문번호로 결제에 사용한 수단과 영수증 번호를 조회한다. 결제수단·영수증 관련 질문에 사용한다.")
    public String paymentMethod(
            @ToolParam(description = "주문번호(숫자 5자리)") String orderId,
            ToolContext ctx) {

        String userId = currentUser(ctx);
        log.info("[TOOL] paymentMethod orderId={} by={}", orderId, userId);

        return orders.findOwned(orderId, userId)
                .map(this::describePayment)
                .orElse("해당 주문을 찾을 수 없습니다.");
    }

    /** 실제 결제 시스템 연동 없이, 주문번호로부터 결정적으로(항상 같은 결과) 값을 만들어낸다. */
    private String describePayment(Order o) {
        String[] methods = {"신용카드", "카카오페이", "문화상품권"};
        int idx = Math.floorMod(o.id().hashCode(), methods.length);
        String receiptNo = "RC-" + o.id();
        return "주문 %s의 결제 수단은 %s이며, 영수증 번호는 %s입니다."
                .formatted(o.id(), methods[idx], receiptNo);
    }

    private String currentUser(ToolContext context) {
        Object userId = context == null ? null : context.getContext().get("userId");
        if (userId == null) {
            throw new IllegalStateException("toolContext에 userId가 없다 — 호출부 설정을 확인하라");
        }
        return userId.toString();
    }
}

package com.skala.helpdesk.tools;

import com.skala.helpdesk.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 권한 검증은 모델 없이 직접 확인한다 — "모델이 알아서 안 부르겠지"는 검증이 아니다.
 */
class OrderToolsTest {

    private final OrderTools tools = new OrderTools(new OrderRepository());

    private ToolContext ctx(String userId) {
        return new ToolContext(Map.of("userId", userId));
    }

    @Test
    void 본인_주문은_조회된다() {
        String result = tools.orderStatus("12345", ctx("user1"));
        assertThat(result).contains("레드큐브");
    }

    @Test
    void 남의_주문은_같은_문구로_거절된다() {
        String result = tools.orderStatus("99999", ctx("user1"));
        assertThat(result).isEqualTo("해당 주문을 찾을 수 없습니다.");
    }

    @Test
    void 없는_주문도_같은_문구다() {
        String result = tools.orderStatus("00000", ctx("user1"));
        assertThat(result).isEqualTo("해당 주문을 찾을 수 없습니다.");
    }
}

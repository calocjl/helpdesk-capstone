package com.skala.helpdesk.ops;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 비기능요구(311쪽) "주모델 장애 시 폴백으로 응답 지속 — 장애주입테스트"를
 * 실제로 검증할 수 있게 만드는 스위치.
 *
 * <p>{@code POST /api/admin/fault/primary-down?enabled=true}로 켜면, 다음 요청부터
 * 주 모델 호출 경로가 실제 장애처럼 실패한다. {@link com.skala.helpdesk.chat.HelpDeskService}가
 * 이 실패를 감지해 폴백 클라이언트로 전환하는지 확인하는 용도다.
 */
@Component
public class FaultInjector {

    private final AtomicBoolean primaryDown = new AtomicBoolean(false);

    public boolean isPrimaryDown() {
        return primaryDown.get();
    }

    public void setPrimaryDown(boolean value) {
        primaryDown.set(value);
    }
}

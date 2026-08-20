package com.skala.helpdesk.ops;

/** {@link FaultInjector}가 장애를 흉내낼 때 던지는 예외. 실제 공급자 장애와 같은 방식으로 처리된다. */
public class SimulatedOutageException extends RuntimeException {
    public SimulatedOutageException() {
        super("장애주입: 주 모델 경로를 인위적으로 실패시켰습니다.");
    }
}

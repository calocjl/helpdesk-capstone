package com.skala.helpdesk.advisor;

/**
 * {@link SafetyAdvisor}가 위험한 입력을 감지했을 때 던진다.
 *
 * <p>여기서 던지면 {@code chain.nextCall(...)}이 호출되지 않으므로, order가 올바를 때는
 * (Safety가 Memory보다 바깥) 이 요청이 대화 이력에 저장되기 전에 흐름이 끊긴다.
 */
public class ContentBlockedException extends RuntimeException {
    public ContentBlockedException(String message) {
        super(message);
    }
}

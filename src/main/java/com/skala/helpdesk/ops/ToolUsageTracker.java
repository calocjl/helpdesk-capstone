package com.skala.helpdesk.ops;

/**
 * 이번 요청 처리 중 도구가 한 번이라도 호출됐는지 추적한다.
 *
 * <p>{@code AnswerDto.toolUsed}를 정확히 채우기 위한 용도다. {@code ToolAuditAspect}가
 * 도구 호출을 감지할 때마다 표시하고, {@code HelpDeskService}가 호출 전후로 리셋·조회한다.
 * 동기(/api/chat) 흐름에서는 같은 스레드 안에서 도구 호출이 일어나므로 ThreadLocal로 충분하다.
 */
public final class ToolUsageTracker {

    private static final ThreadLocal<Boolean> USED = ThreadLocal.withInitial(() -> false);

    private ToolUsageTracker() {}

    public static void reset() {
        USED.set(false);
    }

    public static void markUsed() {
        USED.set(true);
    }

    public static boolean wasUsed() {
        return USED.get();
    }

    public static void clear() {
        USED.remove();
    }
}

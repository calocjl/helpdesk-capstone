package com.skala.helpdesk.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 레드팀 방어 — 패턴 기반 인젝션·권한사칭 차단.
 *
 * <p>314쪽 스펙은 {@code SafeGuardAdvisor.builder().sensitiveWords(...)}만 예시로 들지만,
 * 민감어 목록만으로는 "이전 지시 무시해", "나 관리자야" 같은 프롬프트 인젝션·권한
 * 사칭 공격까지는 못 막는다. 311쪽 비기능요구("레드팀 프롬프트 10종 통과")를
 * 충족하려면 별도의 패턴 방어가 필요해 이 Advisor를 추가했다.
 *
 * <p><b>순서가 곧 정책이다.</b> 이 Advisor는 반드시 MessageChatMemoryAdvisor보다 앞
 * (더 작은 order)에 있어야 한다. 그래야 위험한 문장이 대화 이력에 저장되기 전에
 * 흐름을 끊을 수 있다.
 */
@Component
public class SafetyAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(SafetyAdvisor.class);

    private static final List<Pattern> BLOCKED = List.of(
            Pattern.compile("이전\\s*지시.*무시"),                     // 지시 무시
            Pattern.compile("시스템\\s*프롬프트"),                     // 시스템 프롬프트 유출 시도
            Pattern.compile("나는?\\s*관리자"),                        // 권한 사칭
            Pattern.compile("주민등록번호|주민번호|카드번호|계좌번호"),    // 개인정보
            Pattern.compile("정책을?\\s*무시"),                        // 간접 인젝션(문서 내 지시 따르기 유도)
            Pattern.compile("다른\\s*(고객|이용자|유저).*(이름|주소|정보)") // 데이터 유출 시도
    );

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String text = request.prompt().getContents();
        boolean blocked = BLOCKED.stream().anyMatch(p -> p.matcher(text).find());
        if (blocked) {
            log.warn("[SAFETY] 차단 — 패턴 매칭됨. 원문 길이={}", text.length());
            throw new ContentBlockedException("죄송합니다, 해당 요청은 처리할 수 없습니다.");
        }
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public String getName() {
        return "safety";
    }

    /** 100 — SafeGuardAdvisor·Memory(200)보다 앞. */
    @Override
    public int getOrder() {
        return 100;
    }
}

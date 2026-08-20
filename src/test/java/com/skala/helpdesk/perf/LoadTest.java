package com.skala.helpdesk.perf;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import com.skala.helpdesk.rag.IngestService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비기능요구(311쪽) "P95 응답 5초 이내(비스트리밍)"를 검증하는 간단한 부하 테스트다.
 *
 * <p>k6·JMeter 같은 외부 도구 없이도 확인할 수 있게, {@code /api/chat}을 순차적으로
 * N번(기본 20회) 호출해 응답 시간을 모으고 P95를 계산한다. 실제 모델을 호출하므로
 * 기본 {@code ./gradlew test}에서는 제외되고, {@code ./gradlew test -Pperf}일 때만 실행된다.
 *
 * <p>요청 횟수는 {@code -DperfRequests=N}으로 조절할 수 있다(기본 20).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("perf")
class LoadTest {

    private static final Logger log = LoggerFactory.getLogger(LoadTest.class);
    private static final long P95_BUDGET_MS = 5000;

    @LocalServerPort
    int port;

    @Autowired
    IngestService ingestService;

    @Test
    void 응답시간_P95가_5초_이내다() throws Exception {
        // 근거 문서가 없으면 모델을 안 부르고 즉시 거절해버려 지연 측정 의미가 없어진다 — 먼저 인제스트한다.
        Resource[] docs = new PathMatchingResourcePatternResolver().getResources("classpath:/docs/*.md");
        Arrays.stream(docs).forEach(doc -> ingestService.ingest(doc, "policy", "CS"));

        int n = Integer.getInteger("perfRequests", 20);
        RestTemplate rest = new RestTemplate();
        String url = "http://localhost:" + port + "/api/chat";

        // bypassCache=true를 쓰는 이유: 시맨틱 캐시(확장과제, 307쪽)가 켜져 있으면 같은 질문을
        // 반복하는 이 테스트는 두 번째 호출부터 캐시로 즉시 응답해 P95가 인위적으로 좋게 나온다.
        // 부하테스트는 "캐시 없이 매번 모델을 부르는 최악의 경우"를 재는 게 맞다.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, basicAuth("user1", "pass"));

        List<Long> latenciesMs = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            String body = """
                    {"question": "단풍캐시로 결제한 상품은 며칠 이내에 청약철회 할 수 있나요?", "sessionId": "perf-%d", "bypassCache": true}
                    """.formatted(i);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);

            long started = System.nanoTime();
            rest.exchange(url, HttpMethod.POST, entity, String.class);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            latenciesMs.add(elapsedMs);
        }

        Collections.sort(latenciesMs);
        int p95Index = (int) Math.ceil(latenciesMs.size() * 0.95) - 1;
        long p95 = latenciesMs.get(Math.max(0, Math.min(p95Index, latenciesMs.size() - 1)));

        log.info("요청 {}건 — P95={}ms, MAX={}ms, MIN={}ms",
                n, p95, latenciesMs.get(latenciesMs.size() - 1), latenciesMs.get(0));

        assertThat(p95).isLessThanOrEqualTo(P95_BUDGET_MS);
    }

    private String basicAuth(String user, String pass) {
        String creds = user + ":" + pass;
        return "Basic " + Base64.getEncoder().encodeToString(creds.getBytes());
    }
}

package com.skala.helpdesk.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.helpdesk.chat.AnswerDto;
import com.skala.helpdesk.chat.HelpDeskService;
import com.skala.helpdesk.rag.IngestService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 8 — 골든셋 평가 (교안 313쪽 eval/GoldenSet.java에 대응).
 *
 * <p>표준 Gradle 관례상 JUnit 테스트는 {@code src/test/java}에 있어야 실행되므로,
 * 스펙의 {@code src/main/java/.../eval/GoldenSet.java} 위치를 그대로 따르지 않고
 * 이 파일을 {@code src/test/java/.../eval/GoldenSetEvalTest.java}로 옮겼다.
 *
 * <p>모델을 여러 번 호출하므로 기본 {@code ./gradlew test}에서는 제외되고,
 * {@code ./gradlew test -Peval}일 때만 실행된다.
 */
@SpringBootTest
@Tag("eval")
class GoldenSetEvalTest {

    private static final Logger log = LoggerFactory.getLogger(GoldenSetEvalTest.class);

    @Autowired
    HelpDeskService service;

    @Autowired
    IngestService ingestService;

    record Golden(String q, List<String> must, String src) {}

    @Test
    void 골든셋_평가() throws Exception {
        // AdminController는 @PreAuthorize가 걸려있어 인증 컨텍스트 없는 테스트에서 막힌다.
        // IngestService를 직접 호출해 같은 문서를 인제스트한다.
        Resource[] docs = new PathMatchingResourcePatternResolver().getResources("classpath:/docs/*.md");
        Arrays.stream(docs).forEach(doc -> ingestService.ingest(doc, "policy", "CS"));

        ObjectMapper mapper = new ObjectMapper();
        InputStream is = getClass().getClassLoader().getResourceAsStream("golden.json");
        List<Golden> golden = mapper.readValue(is, new TypeReference<>() {});

        int pass = 0;
        for (Golden g : golden) {
            AnswerDto a = service.ask(g.q(), "eval-user", "eval-session");

            boolean hit = g.must().stream().anyMatch(k -> a.answer().contains(k));
            boolean cite = g.src() == null
                    || a.sources().stream().anyMatch(s -> s.document().contains(g.src()));

            if (hit && cite) {
                pass++;
            } else {
                log.warn("실패: {}\n  답변: {}\n  출처: {}", g.q(), a.answer(), a.sources());
            }
        }

        log.info("통과 {}/{}", pass, golden.size());
        assertThat(pass).isGreaterThanOrEqualTo((int) Math.ceil(golden.size() * 0.7));
    }
}

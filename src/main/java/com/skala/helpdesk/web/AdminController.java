package com.skala.helpdesk.web;

import com.skala.helpdesk.ops.FaultInjector;
import com.skala.helpdesk.rag.IngestService;
import com.skala.helpdesk.repository.Ticket;
import com.skala.helpdesk.repository.TicketRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 관리자(사람 전용) API — 도구 목록에 등록되지 않아 모델이 호출할 방법이 없다.
 * 모두 {@code @PreAuthorize("hasRole('ADMIN')")}로 보호된다.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "종합실습 · 관리자(사람 전용)")
public class AdminController {

    private final IngestService ingest;
    private final VectorStore vectorStore;
    private final TicketRepository tickets;
    private final FaultInjector faultInjector;

    public AdminController(IngestService ingest, VectorStore vectorStore,
                            TicketRepository tickets, FaultInjector faultInjector) {
        this.ingest = ingest;
        this.vectorStore = vectorStore;
        this.tickets = tickets;
        this.faultInjector = faultInjector;
    }

    /**
     * Phase 2 — 정책 문서 4종 인제스트 (먼저 실행). docs 폴더의 .md 파일을 전부 읽는다.
     */
    @PostMapping("/ingest")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "정책 문서 인제스트", description = "docs 폴더의 정책 문서 4종을 벡터 저장소에 넣는다. ADMIN 권한 필요.")
    public List<IngestService.IngestResult> ingestSamples() throws IOException {
        Resource[] docs = new PathMatchingResourcePatternResolver().getResources("classpath:/docs/*.md");
        return Arrays.stream(docs)
                .map(doc -> ingest.ingest(doc, "policy", "CS"))
                .toList();
    }

    /**
     * Phase 2 심화 — 인제스트 품질 확인 (315쪽). 성공 메시지가 아니라 결과물로 확인한다.
     */
    @GetMapping("/chunks")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "인제스트 결과 확인", description = "무엇이 들어갔는지 눈으로 본다 — 검색 점수까지 함께.")
    public List<Map<String, Object>> inspect(@RequestParam String q,
                                              @RequestParam(defaultValue = "5") int topK) {
        var hits = vectorStore.similaritySearch(SearchRequest.builder().query(q).topK(topK).build());
        return hits.stream().map(d -> Map.<String, Object>of(
                "source", d.getMetadata().get("source"),
                "version", d.getMetadata().get("version"),
                "score", d.getScore(),
                "preview", d.getText().substring(0, Math.min(160, d.getText().length()))
        )).toList();
    }

    @GetMapping("/tickets/pending")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "대기 중인 교환·환불 티켓 목록")
    public List<Ticket> pending() {
        return tickets.pending();
    }

    @PostMapping("/tickets/{no}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "티켓 승인 (실제 처리)", description = "여기 도달했다면 이미 권한이 확인된 것이다.")
    public Ticket approve(@PathVariable String no) {
        return tickets.approve(no)
                .orElseThrow(() -> new IllegalArgumentException("해당 티켓 번호를 찾을 수 없습니다: " + no));
    }

    /**
     * 비기능요구 검증용 — 장애주입 테스트. 켜두면 주 모델 호출이 실제 장애처럼
     * 실패하고, 폴백 클라이언트로 전환되는지 확인할 수 있다.
     */
    @PostMapping("/fault/primary-down")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "장애주입 스위치", description = "true로 켜면 다음 요청부터 주 모델 경로가 실패한다(폴백 검증용).")
    public Map<String, Object> setFault(@RequestParam boolean enabled) {
        faultInjector.setPrimaryDown(enabled);
        return Map.of("primaryDown", faultInjector.isPrimaryDown());
    }
}

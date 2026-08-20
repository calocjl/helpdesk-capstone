package com.skala.helpdesk.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 2 — 문서 인제스트 파이프라인 (교안 315쪽).
 *
 * <p>사내 정책 문서를 읽어 청크로 나누고 메타데이터를 붙여 저장한다. 같은 문서를
 * 다시 넣으면 중복된다 — 문서 단위 삭제 후 재삽입한다. 출처 표기를 위해
 * source·docType·dept·version을 반드시 넣는다.
 *
 * <p><b>주의</b>: 재색인 없이 add만 반복하면 같은 청크가 쌓인다. 검색 결과가 같은
 * 문장으로 도배되고 근거가 다양해지지 않는다 — 문서 단위 삭제를 먼저 하라.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final VectorStore vectorStore;

    public IngestService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public record IngestResult(String source, int chunks) {}

    public IngestResult ingest(Resource file, String docType, String dept) {
        String source = file.getFilename() == null ? "unknown" : file.getFilename();

        deleteExisting(source);                                      // ① 재색인 대비

        List<Document> raw = new TikaDocumentReader(file).get();

        var chunks = TokenTextSplitter.builder()
                .withChunkSize(800)
                .withMinChunkSizeChars(350)
                .build()
                .apply(raw);

        List<Document> enriched = chunks.stream().map(c -> {          // ② 메타데이터
            Map<String, Object> m = new HashMap<>(c.getMetadata());
            m.put("source", source);
            m.put("docType", docType);
            m.put("dept", dept);
            m.put("version", today());
            return new Document(c.getText(), m);
        }).toList();

        vectorStore.add(enriched);                                    // ③ 임베딩 + 저장
        log.info("인제스트 완료 source={} chunks={}", source, enriched.size());
        return new IngestResult(source, enriched.size());
    }

    private void deleteExisting(String source) {
        try {
            vectorStore.delete("source == '" + source + "'");
        } catch (Exception e) {
            // 인메모리 스토어 등 필터 삭제를 지원하지 않는 구현도 있다.
            log.debug("기존 청크 삭제를 건너뛴다({}): {}", source, e.getMessage());
        }
    }

    private String today() {
        return LocalDate.now().toString();
    }
}

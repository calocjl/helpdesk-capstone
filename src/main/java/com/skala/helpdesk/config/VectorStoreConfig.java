package com.skala.helpdesk.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 추가 인프라 없이 바로 실행할 수 있도록 인메모리 VectorStore를 기본으로 둔다.
 *
 * <p><b>운영에서는 쓰지 않는다.</b> 재시작하면 저장된 내용이 사라진다 — 서버를 새로
 * 켤 때마다 {@code POST /api/admin/ingest}를 다시 호출해야 한다.
 *
 * <p>pgvector로 바꾸려면 build.gradle의 pgvector 스타터 의존성 주석을 해제하고,
 * application.yml의 {@code spring.ai.vectorstore.pgvector} 블록 주석을 해제한 뒤
 * {@code docker compose up -d}로 DB를 띄우면 이 빈은 {@code @ConditionalOnMissingBean}에
 * 의해 자동으로 물러난다(Spring AI가 pgvector 빈을 대신 등록한다).
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}

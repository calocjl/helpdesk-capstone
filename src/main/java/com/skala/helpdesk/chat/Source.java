package com.skala.helpdesk.chat;

/** RAG 답변이 사용한 근거 문서 출처. Phase 3(316쪽) 스펙의 record Source를 별도 파일로 뺐다. */
public record Source(String document, String version) {
}

# 종합실습 — 단풍이야기 HelpDesk AI (교안 309~323쪽)

지금까지(Day1~3) 배운 RAG·Tool·Memory·Advisor·승인 게이트·관찰가능성·보안을
전부 조립한 최종 산출물입니다. **채점 대상**이라 311쪽 요구사항표를 항목별로
코드와 대조할 수 있게 정리했어요.

## 도메인

"단풍이야기"라는 가상 MMORPG의 고객센터. 실제 게임 정책(넥슨 청약철회 조항,
계정 제재 정책, 재화 회수 절차, 큐브/환생의 불꽃 전후선택 복구 관행 등)을
조사해서 근거로 삼되, 상표 문제 없도록 가상 게임명으로 작성했습니다.

## 1. 요구사항 대비 산출물 (교안 311쪽 그대로 대조)

| 구분 | 요구사항 | 검증 방법(교안) | 구현 위치 |
|---|---|---|---|
| 기능 | 문서 근거로 답하고 출처 표시 | 출처 없는 답변이 나오면 실패 | `HelpDeskService.extractSources()`, `AnswerDto.sources` |
| 기능 | 주문·티켓을 실시간 조회·생성 | 도구 호출 로그에 기록이 남는가 | `OrderTools`, `TicketTools` + `ToolAuditAspect`(AI_TOOL_AUDIT 로거) |
| 기능 | 3턴 이상 맥락 유지 | 대명사 질문("그건")에 정상 응답 | `AiConfig.chatMemory`, `HelpDeskService.conversationId()` |
| 비기능 | P95 응답 5초 이내(비스트리밍) | 부하테스트 지표 | `LoadTest.java` (`-Pperf`) |
| 비기능 | 질의당 평균 토큰 상한 준수 | Micrometer 토큰 카운터 | `TokenMeterAdvisor` → `/actuator/metrics/ai.tokens.perCall` |
| 비기능 | 인젝션·민감어 차단, 모든 도구 호출 감사 | 레드팀 프롬프트 10종 통과 | `SafetyAdvisor` + `SafeGuardAdvisor` + `ToolAuditAspect` |
| 비기능 | 주모델 장애 시 폴백으로 응답 지속 | 장애주입테스트 | `FaultInjector`, `HelpDeskService`(try-catch+fallback), `/api/admin/fault/primary-down` |

## 2. 패키지 구조 (313쪽 패키지 지도 대비)

```
com/skala/helpdesk/
├─ HelpDeskApplication.java
├─ config/
│    ├─ AiConfig.java              Phase 1 — ChatClient·Advisor 조립 (+ 폴백 클라이언트)
│    ├─ HelpDeskProperties.java    설정 외부화
│    ├─ VectorStoreConfig.java     (스펙엔 없지만 필요 — 인메모리 기본, pgvector 옵션)
│    └─ OpenApiConfig.java         (스펙엔 없지만 필요 — Swagger Authorize 버튼)
├─ web/
│    ├─ ChatController.java        Phase 6 — REST + SSE
│    ├─ AdminController.java       인제스트·승인·장애주입
│    ├─ SecurityConfig.java        Phase 7 — 인증·인가
│    └─ HelpDeskExceptionHandler.java  (스펙엔 없지만 필요)
├─ chat/
│    ├─ HelpDeskService.java       Phase 3·5 — 업무 흐름 + 폴백
│    ├─ AnswerDto.java             Phase 6 — 구조화 응답
│    └─ Source.java                Phase 3 — 출처 record
├─ repository/
│    ├─ Order.java, OrderRepository.java     Phase 4
│    └─ Ticket.java, TicketStatus.java, TicketRepository.java   (스펙 트리엔 없지만 필요)
├─ rag/
│    └─ IngestService.java         Phase 2
├─ tools/
│    ├─ OrderTools.java            Phase 4
│    ├─ TicketTools.java           Phase 4
│    └─ PaymentTools.java          (스펙엔 없지만 추가 — 확장과제: 병렬 도구 호출 데모)
├─ advisor/
│    ├─ AuditAdvisor.java          Phase 7
│    ├─ SafetyAdvisor.java         (스펙엔 없지만 추가 — 레드팀 10종 방어를 위한 패턴 차단)
│    ├─ ContentBlockedException.java
│    ├─ TokenMeterAdvisor.java     Phase 8
│    └─ ToolAuditAspect.java       (AOP 도구 감사 — 비기능요구 대응)
└─ ops/
     ├─ FaultInjector.java         (스펙엔 없지만 추가 — 장애주입테스트용)
     ├─ SimulatedOutageException.java
     ├─ ToolUsageTracker.java      (AnswerDto.toolUsed 계산용)
     └─ SemanticCacheService.java  (스펙엔 없지만 추가 — 확장과제: 시맨틱 캐시)

src/test/java/com/skala/helpdesk/
├─ eval/GoldenSetEvalTest.java     Phase 8 — 스펙의 eval/GoldenSet.java (src/main→src/test로 위치 조정, 사유는 아래 참고)
├─ perf/LoadTest.java              P95 부하테스트
└─ tools/OrderToolsTest.java       모델 없이 권한 검증
```

**스펙과 다르게 만든 지점 두 가지, 이유를 명확히 남깁니다.**

1. **`eval/GoldenSet.java`를 `src/main`이 아니라 `src/test`에 뒀어요.** JUnit은 Gradle
   `test` 태스크가 `src/test/java`만 스캔하기 때문에, 원래 위치 그대로 두면 테스트가
   전혀 실행되지 않아요. 표준 Gradle 관례를 따랐습니다.
2. **`SecurityConfig`를 313쪽 지도대로 `web/`에 뒀지만, `config/`에도 둘 법한 이름이라
   헷갈릴 수 있어요.** 지도 그대로 `web/`에 유지했습니다.

## 3. 실행

```bash
export OPENAI_API_KEY="sk-..."
cd 종합실습
chmod +x ./gradlew
./gradlew bootRun
```

**서버가 뜨면 반드시 먼저** (Swagger Authorize에서 admin/admin 로그인 후):
```bash
curl -u admin:admin -X POST localhost:8080/api/admin/ingest
```
(인메모리 VectorStore라 재시작마다 다시 해야 규정 질문(RAG)이 동작해요)

## 4. Swagger로 진행하기

```
http://localhost:8080/swagger-ui.html
```

우측 상단 자물쇠(Authorize)에서 로그인:
- `/api/chat/**` 테스트 → user1 / pass (또는 user2 / pass)
- `/api/admin/**` 테스트 → admin / admin

`http/종합실습.http` 파일에 322쪽 "검증 시나리오 다섯 흐름" + 권한 검증 + 승인 게이트 +
스트리밍 + 관찰가능성 + 장애주입 + 레드팀 10종이 순서대로 정리되어 있어요.

## 5. 테스트 계정

| 아이디 | 비밀번호 | 역할 | 비고 |
|---|---|---|---|
| user1 | pass | USER | 주문 12345(레드큐브 세트), 12346(코디 패키지) 보유 |
| user2 | pass | USER | 주문 99999(환생의 불꽃 5개입) 보유 — user1 입장에선 "남의 주문" |
| admin | admin | ADMIN | `/api/admin/**` 전용 |

## 6. 골든셋 평가 (Phase 8)

```bash
./gradlew test -Peval --tests GoldenSetEvalTest
cat build/test-results/test/TEST-com.skala.helpdesk.eval.GoldenSetEvalTest.xml | grep "통과"
```

정책 문서 4종을 커버하는 12문항(문서당 3문항 + 거절 케이스 1문항)이에요. must 키워드는
"모두 포함"이 아니라 "하나라도 포함"(동의어 허용) 방식으로 채점합니다 — 예를 들어
`["제재", "이용", "정지"]`는 셋 중 하나만 답변에 있어도 통과예요.

## 7. 부하 테스트 — P95 5초 이내 (비기능요구 1번)

```bash
./gradlew test -Pperf --tests LoadTest
```

`/api/chat`을 20회(기본값, `-DperfRequests=N`으로 조절 가능) 순차 호출해서 P95를
계산하고 5,000ms 이내인지 확인해요. 외부 부하테스트 도구(k6, JMeter) 없이 진행할 수
있게 만든 경량 버전이라, 실제 운영 수준의 동시 부하까지는 흉내내지 못해요 — 순차 호출
기준의 지연 시간만 확인한다는 걸 감안해주세요.

## 8. 토큰 상한 확인 (비기능요구 2번)

```
GET /actuator/metrics/ai.tokens.perCall
```

`MEAN` 값이 `TokenMeterAdvisor.TOKEN_BUDGET_PER_CALL`(1500)보다 낮은지 확인하세요.
초과하는 호출이 있으면 `ai.tokens.budget.exceeded` 카운터가 올라가고, 서버 로그에도
WARN으로 남아요.

> **알려진 제약**: `TokenMeterAdvisor`는 `CallAdvisor`만 구현했어요. 12절(286쪽)에서
> 배운 것처럼 `CallAdvisor`만 구현한 Advisor는 스트리밍 경로(`/api/chat/stream`)에서는
> 조용히 건너뛰어요 — 그래서 이 지표는 **`/api/chat`(동기) 호출에서만** 정확히 쌓여요.
> 이건 버그가 아니라, "스트리밍까지 계측하려면 `StreamAdvisor`도 함께 구현해야 한다"는
> 그 절의 교훈을 일부러 남겨둔 지점이에요. 스트리밍까지 계측하고 싶으면
> `TokenMeterAdvisor`에 `StreamAdvisor`를 추가로 구현하면 됩니다.

## 9. 장애주입 테스트 (비기능요구 4번)

```bash
curl -u admin:admin -X POST 'localhost:8080/api/admin/fault/primary-down?enabled=true'
curl -u user1:pass -X POST localhost:8080/api/chat -H 'Content-Type: application/json' \
  -d '{"question":"배송 정책 알려줘","sessionId":"fault-test"}'
# → 폴백 클라이언트가 응답해야 한다 (근거·출처 없이, 안내 문구 위주)
curl -u admin:admin -X POST 'localhost:8080/api/admin/fault/primary-down?enabled=false'
```

**테스트 끝나면 반드시 `enabled=false`로 되돌려 놓으세요** — 켜둔 채로 두면 이후 모든
요청이 계속 폴백으로만 응답해요.

## 10. 레드팀 10종

`http/종합실습.http` 파일 맨 아래에 10개 문장이 정리되어 있어요. Day3 실습에서
6종만 시도했던 것과 달리, 이번엔 간접 인젝션(⑤)과 비용 공격(⑧)까지 포함한 10종
전체를 시도할 수 있게 준비했어요.

## 11. pgvector로 바꾸기 (선택 — 기본은 인메모리)

```bash
# 1) build.gradle 에서 pgvector 스타터 의존성 주석 해제
# 2) application.yml 에서 spring.ai.vectorstore.pgvector 블록 주석 해제
docker compose up -d
./gradlew bootRun
```

## 12. 자주 막히는 지점

| 증상 | 원인 | 해결 |
|---|---|---|
| 규정 질문에 계속 "확인되지 않습니다" | 인제스트를 안 함 | `POST /api/admin/ingest` 먼저 (재시작마다!) |
| `/api/chat`에서 401 | 로그인 안 함 | Swagger Authorize에서 user1/pass |
| `/api/admin/**`에서 403 | ADMIN 아닌 계정으로 로그인 | admin/admin으로 다시 로그인 |
| 도구가 안 불림 | 설명이 부실함 | `@Tool`의 description에 "언제 쓰는지"를 명시 (Day3 트러블슈팅 참고) |
| 폴백이 계속 응답함 | 장애주입 스위치를 끄지 않음 | `enabled=false`로 되돌리기 |
| P95가 5초를 넘음 | 첫 호출은 JIT 워밍업 등으로 느릴 수 있음 | `-DperfRequests`를 늘려 평균을 더 안정적으로 측정 |

## 13. 완료 기준 자가 체크 (311쪽 요구사항표 8개)

- [ ] 문서 근거로 답하고 출처를 표시한다 (출처 없는 답변이 나오면 실패)
- [ ] 주문·티켓을 실시간 조회·생성한다 (도구 호출 로그 확인)
- [ ] 3턴 이상 맥락을 유지한다 (검증 시나리오 ③번)
- [ ] P95 응답 5초 이내 (`LoadTest`)
- [ ] 질의당 평균 토큰 상한 준수 (`ai.tokens.perCall` MEAN)
- [ ] 인젝션·민감어 차단, 모든 도구 호출 감사 (레드팀 10종 + AI_TOOL_AUDIT 로그)
- [ ] 주모델 장애 시 폴백으로 응답 지속 (장애주입 테스트)
- [ ] 승인 게이트 — 티켓은 접수까지만, 승인은 ADMIN만

## 14. 확장과제 (229쪽 Day2·307쪽 Day3) — 3개 적용

229쪽·307쪽에 나온 확장과제 후보 중, 인프라 추가 없이(MCP·하이브리드검색·재순위·
HyDE·Grafana 대시보드는 제외) 지금 구조에 바로 얹을 수 있는 것 3개를 적용했어요.

| 확장과제 | 출처 | 구현 위치 | 확인 방법 |
|---|---|---|---|
| 메타데이터 필터 (권한·범위를 검색으로 강제) | 229쪽 | `AiConfig` — `QuestionAnswerAdvisor`에 `filterExpression("docType == 'policy'")` | 캐시 항목(`docType=qa-cache`)이 RAG 근거로 안 섞이는 것 자체가 증거 |
| 시맨틱 캐시 (뜻이 같은 질문은 즉시 응답) | 307쪽 | `SemanticCacheService` — 같은 VectorStore를 재활용, `docType=qa-cache`로 분리 저장 | `http/종합실습.http`의 "시맨틱 캐시" 섹션 — 표현 바꾼 2차 호출의 Request duration이 1차보다 훨씬 짧은지 비교 |
| 병렬 도구 호출 (주문+결제수단 동시 조회) | 307쪽 | `PaymentTools`(신규) — `OrderTools`와 독립된 두 번째 읽기 전용 도구 | "12345 주문 상태랑 결제 수단도 같이 알려줘" 요청 시, 터미널에서 `orderStatus`와 `paymentMethod` 로그 타임스탬프가 거의 동시인지 확인 |

### 왜 이 셋을 골랐는지

- **메타데이터 필터**는 사실 시맨틱 캐시를 안전하게 만들기 위한 전제조건이기도 했어요 —
  필터가 없으면 캐시에 저장된 "질문 텍스트"가 진짜 정책 문서인 것처럼 RAG 근거에
  섞여 들어갈 위험이 있었거든요. 두 확장과제가 서로를 보완하는 구조로 짰어요.
- **시맨틱 캐시**는 도구 기반 답변(주문 조회 등)은 캐싱하지 않도록 조건을 걸었어요
  (`HelpDeskService.ask()`의 `!sources.isEmpty() && !toolUsed` 조건). 실시간·개인화
  데이터를 캐싱하면 다른 사용자에게 엉뚱한 답이 나갈 수 있어서예요.
- **병렬 도구 호출**은 사실 추가 코드가 거의 없어요 — 서로 독립적인 도구를 하나 더
  등록해주기만 하면, "동시에 두 정보가 필요한 질문"에서 Spring AI가 알아서 병렬로
  불러줘요(11절에서 배운 그대로).

### 부하테스트(LoadTest)와 캐시가 부딪히는 지점 — 미리 알아두면 좋은 것

`LoadTest`는 같은 질문을 20번 반복하는데, 캐시가 켜져 있으면 두 번째 호출부터 즉시
응답해서 P95가 인위적으로 좋게 나와요. 그래서 `LoadTest`는 `bypassCache: true`를
요청 바디에 넣어서 캐시를 우회하고, "캐시 없이 매번 모델을 부르는 최악의 경우"를
재도록 만들어뒀어요. 반대로 캐시 효과를 직접 눈으로 보고 싶으시면 `bypassCache`
없이 `http/종합실습.http`의 캐시 데모를 그대로 써보시면 돼요.

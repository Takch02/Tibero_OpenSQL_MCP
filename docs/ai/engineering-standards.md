# Engineering Standards

공통 코드 규칙 원본. Claude Code, Codex, 사람 기여자 모두 적용.

## 설계 원칙

- 기존 컨벤션 우선. 요청 범위 밖 리팩터링·의존성 추가·공개 API 변경 금지.
- 요구사항 불명확 시 안전한 가정은 명시하고 진행, 위험한 가정은 먼저 확인.
- 최소 변경으로 구현. 불필요한 파일·클래스·메서드 생성 금지.
- 중복은 3회부터 공통화 검토 (조기 추상화 금지).

## 계층 구조 (`Controller → Service → Repository`, `com.test_mcp.tibero_mcp.<domain>`)

- **Controller**: 요청 검증, DTO 변환, Service 호출만. 비즈니스 로직 금지.
- **Service**: 유스케이스·비즈니스 규칙·트랜잭션 경계. public 메서드 신규/수정 시 테스트 필수.
- **Repository**: 데이터 접근만. 비즈니스 정책 금지.

## API / DTO

- Entity를 요청/응답으로 직접 노출 금지 → Request/Response DTO 사용. 필요한 필드만, 민감 정보·내부 식별자 제외.
- Entity ↔ DTO 변환은 한 계층(Service 또는 Mapper)으로 통일.
- 에러 응답 형식: `exception` 패키지의 `GlobalExceptionHandler`(`@RestControllerAdvice`) + 공통 에러 DTO `ErrorResponse(code, message)`로 통일.
- 기존 API 응답 형식 변경은 요청 없이 하지 않음.
- Controller는 springdoc-openapi로 문서화: `@Tag`, `@Operation`, `@ApiResponse`, 필요시 `@Schema`.

## 코드 품질

- 의도가 드러나는 이름, 함수는 단일 책임.
- 매직 넘버/문자열은 상수화.
- 입력 검증은 신뢰 경계(Controller)에서 수행.
- 비밀값·토큰·개인정보는 코드/테스트/로그에 넣지 않음.
- 주석은 "왜"가 필요한 곳에만 (무엇을 하는지는 쓰지 않음).

## 에러 / 로깅 / 보안

- 예외는 삼키지 않고 의미 있게 전파하거나 도메인 예외로 변환.
- 로그에 민감 값(SQL 파라미터, 벡터 원본 전체, 인증정보) 금지.
- 외부 입력은 신뢰하지 않고 검증.

### 예외 처리 (`com.test_mcp.tibero_mcp.exception`)

도메인 예외는 흩어진 `IllegalStateException`/`ResponseStatusException` 대신 이 패키지에서 관리한다.

| 클래스 | 용도 | HTTP 상태 |
|---|---|---|
| `TiberoMcpException` | 모든 도메인 예외의 추상 베이스 | - |
| `InvalidRequestException` | Controller/MCP 도구의 요청 검증 실패 | 400 |
| `DocumentNotFoundException` | 문서 ID로 조회했으나 없음(비정상 상태) | 404 |
| `EmbeddingDimensionMismatchException` | 모델 출력 차원이 `app.embedding.dimension`과 불일치 | 500 |

`GlobalExceptionHandler`(`@RestControllerAdvice`)가 위 예외를 잡아 `ErrorResponse(code, message)`로 변환한다. 새 도메인 예외를 추가할 때는 `TiberoMcpException`을 상속하고 `GlobalExceptionHandler`에 매핑을 추가한다. JVM 레벨의 "발생할 수 없는" 예외(예: SHA-256 미지원)까지 도메인 예외로 감쌀 필요는 없다.

## 패키지(모듈) 경계

단일 Gradle 모듈이지만 도메인별로 패키지를 분리한다. 하위 `AGENTS.md`는 아직 두지 않음(필요해지면 루트 규칙 위에 덧붙이는 방식으로 추가).

| 패키지 | 책임 |
|---|---|
| `embedding` | 임베딩 모델(Spring AI `TransformersEmbeddingModel`, 로컬 ONNX) 래핑, 배치 추론(`embedAll`) — ingestion/search 공용 |
| `ingestion` | 문서 업로드·청킹·저장(정형 데이터: `owner_id`, `category`, `version` 포함) + 비동기 임베딩 처리 (Outbox 패턴, 아래 참조) |
| `search` | **정형 데이터 + 벡터 검색 결합**(`SearchService`, `SearchController`) — 권한(`owner_id`)·최신 버전·카테고리 필터와 벡터 유사도(코사인)를 한 쿼리로 결합. 상세는 아래 "정형 데이터 + 벡터 결합 검색" 참조 |
| `mcp` | MCP 프로토콜 기반 검색 API (`SearchMcpTools` — `search_documents` 도구, `McpToolConfig` — `ToolCallbackProvider` 등록) |
| `chaos` | P1 OpenSQL 클러스터 장애 주입/하네스 연동 (현재 빈 패키지, 추후 구현) |

새 도메인 로직은 관련 패키지에 추가하고, 패키지 간 의존은 `search`/`ingestion` → `embedding`, `mcp` → `search` 방향만 허용한다(역방향 금지).

### API 노출 지점

| 계층 | 엔드포인트 | 설명 |
|---|---|---|
| REST | `POST /api/documents` | `IngestionController` — 문서 업로드(`ownerId` 필수, `category` 선택). 비동기 임베딩, 응답은 status=PENDING |
| REST | `GET /api/search?query=&ownerId=&category=&limit=` | `SearchController` — `ownerId`(필수, 권한) + `category`(선택, 메타데이터) + 벡터 유사도 결합 검색 |
| MCP | `POST /mcp` (Streamable HTTP, `spring.ai.mcp.server.*`) | `search_documents(query, ownerId, category, limit)` 도구 — `SearchService` 위임 |
| 문서 | `/swagger-ui.html` | springdoc-openapi UI |

REST 응답은 Entity를 직접 노출하지 않고 `ingestion.dto`/`search.dto`의 record DTO로 변환한다.

### `ingestion` 패키지 구조

파일 수가 많아 하위 패키지로 분리했다. 패키지 루트(`ingestion`)에는 서비스/워커 등 진입점만 둔다.

| 하위 패키지 | 내용 |
|---|---|
| `ingestion` (루트) | `IngestionService`(업로드), `EmbeddingWorker`(폴링·추론 오케스트레이션), `EmbeddingResultWriter`(DB 반영 트랜잭션) |
| `ingestion.entity` | `Document`, `DocumentChunk`, `IngestionLog`, `DocumentStatus`, `IngestionEvent` |
| `ingestion.repository` | `DocumentRepository`, `DocumentChunkRepository`, `IngestionLogRepository`, `DocumentChunkBatchWriter`(JDBC 배치 insert/update) |
| `ingestion.chunking` | `Chunker`(고정 크기+overlap 청킹), `ChunkingProperties`(`app.chunking.*`) |

### 문서 처리 흐름 (Outbox 패턴)

1. **업로드** (`IngestionService.upload`, 트랜잭션 1개): `idempotency_key`/`content_hash` 2계층 멱등성 체크 → `documents`(status=`PENDING`, `owner_id`/`category` 포함, embedding 없음) insert → `Chunker`로 청킹 후 `document_chunks` 배치 insert(JDBC, `embedding=NULL`) → `ingestion_log`에 `CREATED` 기록.
2. **임베딩 워커** (`EmbeddingWorker`, `@Scheduled` 폴링, `app.embedding.worker.*` 설정): `PENDING` 문서를 배치로 집어와 청크 내용을 `embed-batch-size` 단위로 나눠 `EmbeddingService.embedAll`로 추론(트랜잭션 밖 — 느린 추론이 DB 트랜잭션을 오래 잡지 않도록). 성공 시 `EmbeddingResultWriter`가 짧은 트랜잭션으로 청크 embedding 배치 UPDATE + 문서 상태를 `EMBEDDED`로 전이 + `ingestion_log`에 `EMBEDDED` 기록. 실패 시 `FAILED` 전이 + 로그, `embedding`은 `NULL` 유지(재처리 여지).
3. **검색** (`SearchService`): 아래 "정형 데이터 + 벡터 결합 검색" 참조.

설계 전제(단일 워커, 단순 폴링 — 동시성 제어용 `PROCESSING` 상태나 `SELECT ... FOR UPDATE SKIP LOCKED` 미적용)와 트레이드오프(업로드~임베딩 완료 사이 최대 `poll-interval-ms` 지연)는 의도된 선택이다. 멀티 워커/즉시 처리가 필요해지면 이 전제부터 재검토한다.

### 정형 데이터 + 벡터 결합 검색

이 프로젝트의 핵심 기능이다. 메타데이터·권한·버전 같은 정형 데이터는 `documents` 테이블(RDBMS)에,
본문 임베딩은 `document_chunks.embedding`(pgvector)에 저장하고, 검색은 **하나의 SQL**에서 정형 조건과
의미 검색을 함께 처리한다(`DocumentChunkRepository.searchByOwnerAndCategory`):

```sql
SELECT c.document_id, c.chunk_index, c.content,
       1 - (c.embedding <=> :queryVector) AS score
FROM document_chunks c
JOIN documents d ON d.id = c.document_id
WHERE d.owner_id = :ownerId          -- 권한 (정형)
  AND d.version = c.document_version -- 최신 버전만 (정형)
  AND (:category IS NULL OR d.category = :category) -- 메타데이터 (정형)
  AND c.embedding IS NOT NULL
ORDER BY c.embedding <=> :queryVector -- 의미 검색 (벡터, 코사인)
LIMIT :limit;
```

- `owner_id`는 검색 권한 경계다. `SearchService.searchSimilar`/`SearchController`/`SearchMcpTools`(REST·MCP 양쪽) 모두 `ownerId`를 필수 파라미터로 받으며, 비어 있으면 `InvalidRequestException`(400)이다. 권한 없는 문서는 이 필터로 원천 차단되므로 애플리케이션 레이어에서 별도 후처리 필터링을 하지 않는다.
- `d.version = c.document_version`은 문서가 재업로드되어 버전이 올라가도 옛 버전 청크가 검색되지 않게 한다.
- `category`는 선택적 메타데이터 필터(null이면 전체 카테고리 대상).
- 벡터 연산자는 코사인 거리(`<=>`)를 쓰고, `1 - 거리`로 변환한 `score`(1에 가까울수록 유사)를 응답에 그대로 노출한다(`SearchResultResponse.score`).
- 결과는 `ChunkSearchProjection`(Spring Data 네이티브 쿼리 인터페이스 프로젝션)으로 받는다 — Entity 그대로 매핑할 수 없는 계산 컬럼(`score`)이 섞여 있기 때문.

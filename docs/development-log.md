# 개발 내역

## 2026-08-19 — OpenSQL HNSW 벡터 인덱스 검증

### 목표

청크 수가 증가할 때 코사인 벡터 검색이 전체 스캔으로 선형 증가하지 않도록 HNSW 인덱스를 적용하고, OpenSQL Single에서 실제 실행계획과 지연시간을 비교한다.

### 구현

- Flyway V11로 `document_chunks.embedding`에 `vector_cosine_ops` HNSW 부분 인덱스 추가
  - 검색 SQL의 코사인 거리 연산자(`<=>`)와 연산자 클래스를 일치
  - `embedding IS NOT NULL` 청크만 인덱스에 포함
- Testcontainers pgvector 통합 테스트로 Flyway 인덱스 정의 검증
- OpenSQL 전용 1천/1만 청크 benchmark SQL 추가
  - 무필터, owner, owner + category 조건의 `EXPLAIN (ANALYZE, BUFFERS)` 비교
  - exact baseline 대비 HNSW 무필터 `recall@10`, 인덱스 생성 시간·크기 기록

### 측정 결과

- 1천 청크: HNSW를 생성해도 세 조건 모두 `Seq Scan`이 선택돼 성능 이득 없음
- 1만 청크: 세 조건에서 `idx_vector_benchmark_embedding_hnsw`의 `Index Scan`이 선택됨
  - 무필터: 3,403.620 ms → 11.211 ms (단일 실행 약 304배)
  - owner: 1,711.538 ms → 7.018 ms (단일 실행 약 244배)
  - owner + category: 695.874 ms → 7.741 ms (단일 실행 약 90배)
  - 무필터 recall@10: 1.0, HNSW 생성 11.317초, 인덱스 20 MB / 테이블 16 MB

### 한계와 다음 검증

- 측정은 OpenSQL Single, UTM x86_64 에뮬레이션, synthetic vector, 질의 벡터 하나의 단일 실행 결과다.
- p50/p95 반복 측정과 owner/category 필터에서 다양한 질의 벡터의 recall@10 검증이 남아 있다.
- Single 결과를 HA 또는 운영 트래픽 성능 근거로 확대하지 않는다.

## 2026-08-09 — 문서 버전 생명주기

### 목표

문서가 수정·삭제되어도 과거 원문과 벡터 이력을 보존하고, 새 버전의 임베딩이 끝날 때까지 마지막 정상 버전만 검색에 노출한다.

### 구현

- Flyway V3 마이그레이션 추가
  - `document_versions` 테이블로 버전별 원문·해시·처리 상태·작성자 보존
  - `documents.current_search_version`으로 현재 검색에 노출할 마지막 정상 버전 관리
  - `documents.deleted_at`을 이용한 논리 삭제
  - `ingestion_log.document_version`으로 이벤트와 문서 버전 연결
  - V3 적용 전 문서를 `document_versions`의 초기 이력으로 backfill
- 문서 API 확장
  - `GET /api/documents/{documentId}`: 현재 문서 상태 조회
  - `GET /api/documents/{documentId}/versions`: 과거 버전 이력 조회
  - `PUT /api/documents/{documentId}`: 새 버전 업로드
  - `DELETE /api/documents/{documentId}`: 논리 삭제
  - `POST /api/documents/{documentId}/versions/{version}/restore`: 과거 원문으로 복원
- 수정·삭제·복원 요청에 `expectedVersion`을 추가해 오래된 화면/요청으로 인한 덮어쓰기를 409으로 차단
- 임베딩 워커가 특정 `document_version`의 미처리 청크만 처리하도록 변경
- 임베딩 반영 시 해당 버전 이력을 `EMBEDDED`/`FAILED`로 전이하고, 최신 작성 버전과 같을 때만 `current_search_version`을 교체
- 전역 `content_hash` 중복 제거를 폐기하고 `idempotency_key`만 생성 재시도 기준으로 사용
  - 서로 다른 소유자의 동일 본문이 하나의 문서로 합쳐지는 권한 경계 문제를 제거

### 상태 전이

```text
최초 생성: v1 PENDING
  → 임베딩 완료: v1 EMBEDDED, current_search_version=1

수정: v2 PENDING, current_search_version=1 유지
  → 임베딩 완료: v2 EMBEDDED, current_search_version=2
  → 임베딩 실패: v2 FAILED, current_search_version=1 유지

삭제: 문서 DELETED, 검색/일반 조회에서 제외
복원: 과거 원문을 새 버전(v3 등)으로 복제 후 PENDING
```

### 검증

- `./gradlew test` 통과 (Testcontainers + pgvector)
- `./gradlew build` 통과 (Spotless 포함)
- 실제 OpenSQL에서 Flyway V3 적용 성공
- 실제 REST 호출로 `v1 생성 → v2 수정 → 버전 이력 조회 → 논리 삭제 → v1 기반 v3 복원` 확인

### 다음 작업

- 재시도 횟수·실패 원인·처리 기한 관리
- 재기동 시 미완료 이벤트 회수

## 2026-08-09 — 다중 워커 작업 점유

### 목표

여러 애플리케이션 인스턴스가 같은 문서 임베딩 작업을 동시에 처리하지 않도록 OpenSQL 행 잠금으로 작업을 점유한다.

### 구현

- `ingestion_tasks` outbox 테이블을 추가하고 문서 버전마다 하나의 처리 작업을 생성
- `IngestionTaskClaimer`가 `FOR UPDATE SKIP LOCKED`로 `PENDING` 작업을 가져와 `PROCESSING`으로 전이
- `EmbeddingWorker`는 claim된 작업만 처리하고, 결과 반영 시 작업을 `EMBEDDED` 또는 `FAILED`로 종료
- 감사 목적의 `ingestion_log`는 변경하지 않고 처리 이력으로 유지

### 검증

- `./gradlew test --rerun-tasks` 성공 — 32개 테스트, 실패·오류 0건
- `./gradlew build` 성공 — Spotless 검사와 전체 테스트 포함
- 잠긴 `PENDING` 작업을 다른 워커가 즉시 건너뛰는 Testcontainers 통합 테스트 추가

## 2026-08-11 — 지연 재시도와 부분 청크 재개

### 목표

일시적인 임베딩 실패가 새 문서 버전의 검색 노출을 중단시키지 않도록 backoff 재시도를 적용하고, 이미 성공한 청크는 다시 추론하지 않는다.

### 구현

- `ingestion_tasks`에 `attempt_count`, `next_attempt_at`, `last_error`를 추가
- due 시각이 지난 `PENDING` 작업만 `FOR UPDATE SKIP LOCKED` claim 대상으로 제한
- 실패 시 최대 횟수 전에는 backoff 후 `PENDING`으로 재예약하고, 최대 횟수에서만 문서·버전·작업을 최종 `FAILED`로 전이
- 청크 배치별 임베딩 결과를 즉시 저장하고, 모든 청크가 완료된 뒤에만 `current_search_version`을 교체

### 다음 작업

- `PROCESSING` lease 만료 작업 회수
- 최종 실패 작업의 수동 재처리와 처리 상태 조회 API

## 2026-08-13 — PDF/TXT 파일 업로드

### 목표

실제 기업 문서 파일을 안전하게 원문 텍스트로 변환하고, 기존 문서 버전·청킹·Outbox 파이프라인으로 연결한다.

### 구현

- `POST /api/documents/files`, `PUT /api/documents/{documentId}/file` multipart API 추가
- Apache PDFBox 3.0.8로 PDF 텍스트를 추출하고, TXT는 엄격한 UTF-8 디코더로 처리
- `document_versions`에 파일명·검증 MIME 타입·바이트 크기·파일 SHA-256 저장
- 파일 입력도 JSON 본문과 동일한 버전 생성·청크 batch insert·Outbox 작업 생성 트랜잭션을 재사용
- 파일 자체 10 MiB, multipart 요청 전체 11 MiB 상한으로 경계·헤더 오버헤드를 허용
- PDF는 최대 1,000페이지·200만 문자로 제한하고, 제한 Writer가 문자 상한 초과 시 추출 도중 중단
- 형식 위장, 빈 문서, 손상·암호화 PDF, 잘못된 UTF-8 TXT를 거절하고 파서 원인은 내부 예외 체인에만 보존
- 원본 파일 바이트와 파서 오류 원문은 영속화하지 않음

### 검증

- PDF/TXT 추출 단위 테스트: 파일 크기 경계, 페이지·문자 상한, 파서 원인 보존
- 실제 HTTP multipart 통합 테스트: 10 MiB 정확한 PDF가 요청 헤더·경계를 포함해도 11 MiB 요청 상한 안에서 저장됨
- multipart 통합 테스트: 업로드, 멱등성, 파일 새 버전, 실패 시 부분 데이터 미생성

# 개발 내역

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

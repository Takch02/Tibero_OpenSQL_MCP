# OpenSQL Single 실환경 Smoke 검증

이 문서는 Testcontainers PostgreSQL 결과와 구분되는 실제 Tmax OpenSQL Single 검증 기록이다. 전용 smoke DB에서만 실행하며, Single 라이선스로 OpenSQL HA failover를 검증했다고 주장하지 않는다.

## 목표와 가설

- 목표: 최신 Flyway 스키마, pgvector 384차원 저장·검색, 재시도 due 조건, `FOR UPDATE SKIP LOCKED`를 OpenSQL Single에서 반복 검증한다.
- 가설: 현재 PostgreSQL 호환 SQL과 애플리케이션 흐름은 OpenSQL Single에서 동작한다.
- 기각 기준: 마이그레이션 실패, vector 차원 불일치, 잠긴 작업의 대기/중복 반환, 미래 재시도 작업 claim이다.

## 실행 전제

- 기존 개발 DB와 분리된 빈 OpenSQL 데이터베이스를 사용한다. 예: `opensql_smoke`
- `.env.opensql.local` 등 저장소 밖 환경에만 접속 정보를 둔다.
- Java 21, 애플리케이션 소스, OpenSQL Single, `psql` 클라이언트가 준비되어 있다.
- SQL의 `task_id`는 API smoke가 만든 문서의 최신 `ingestion_tasks.id`로 직접 바꾼다.

```bash
export OPENSQL_SMOKE_JDBC_URL='jdbc:postgresql://<host>:5432/opensql_smoke'
export OPENSQL_SMOKE_DB_USERNAME='opensql'
export OPENSQL_SMOKE_DB_PASSWORD='<password>'

./scripts/opensql-smoke.sh start
./scripts/opensql-smoke.sh api
./scripts/opensql-smoke.sh stop
```

`api`는 `업로드 → 임베딩 완료 → owner/category 검색 → v2 생성 → 임베딩 완료 → 논리 삭제 → v1 기반 복원(v3) → 임베딩 완료`를 수행한다. 실행 결과의 `documentId`를 기록한다.

## 수동 SQL 실행 순서

애플리케이션을 중지한 뒤 전용 DB에 접속한다.

```bash
psql -h <host> -p 5432 -U opensql -d opensql_smoke
```

1. `\i scripts/sql/01_preflight.sql`
2. `\i scripts/sql/02_schema-and-vector-check.sql`
3. 아래 쿼리로 최근 작업 ID를 확인한다.

   ```sql
   SELECT t.id, t.document_id, t.document_version, t.status, t.next_attempt_at
   FROM ingestion_tasks t
   ORDER BY t.id DESC
   LIMIT 10;
   ```

4. 최근 작업 ID를 `00_prepare-pending-task.sql`의 `\set task_id 1`에 넣고 실행한다. 이 단계는 전용 DB의 해당 작업만 `PENDING`으로 준비한다.
5. 같은 task ID를 사용해 `03_due-claim-check.sql`을 실행한다.
6. 터미널 A에서 `\i scripts/sql/04_skip-locked-session-a.sql`을 실행해 트랜잭션을 유지한다.
7. 터미널 B에서 같은 task ID를 설정한 `\i scripts/sql/05_skip-locked-session-b.sql`을 실행한다.
8. 세션 B가 즉시 `0 rows`를 반환한 것을 기록한 뒤, 세션 A에서 `ROLLBACK;` 한다.

SQL 파일 경로가 VM과 다르면 파일 내용을 복사해 실행하되, 결과 문서에는 실행한 SQL 파일의 Git 커밋을 함께 기록한다.

## 결과 기록

### 환경

| 항목 | 결과 |
| --- | --- |
| 실행일 | 2026-08-14 |
| Git commit | `755803a` 기준 (smoke 하네스는 작업 트리) |
| OpenSQL / PostgreSQL 호환 버전 | PostgreSQL 17.8, x86_64, GCC 11.5.0 |
| OS / Java | Rocky Linux 9.7 / Java 21.0.8 |
| 전용 DB | `opensql_smoke` |
| SQL 검증 세션 | `postgres`, `Asia/Seoul` |

### 판정

| 검증 항목 | 기대 결과 | 실제 결과 | 판정 |
| --- | --- | --- | --- |
| Flyway | V1~V9 success | `flyway_schema_history` 9행 모두 `success=t` | 검증됨 |
| vector 확장 | `vector` 1행 | `vector`, version `0.8.1` 확인. `postgres` 역할로 사전 설치 후 애플리케이션은 `opensql` 역할로 기동 | 검증됨 |
| 임베딩 타입 | `vector(384)` | `document_chunks.embedding`이 `vector(384)`로 조회됨 | 검증됨 |
| 재시도·lease 시각 타입 | `TIMESTAMP WITH TIME ZONE` | `next_attempt_at`(NOT NULL), `heartbeat_at`, `lease_expires_at` 모두 조회됨 | 검증됨 |
| 버전 참조 무결성 | `(document_id, document_version)` 복합 FK | `fk_ingestion_tasks_document_version`이 `document_versions(document_id, version)`을 참조 | 검증됨 |
| 임베딩 차원 | 384 | documentId=1/2의 v1~v3 청크에서 `vector_dims(embedding)=384` | 검증됨 |
| API smoke | v1 → v2 → 삭제 → v1 복원 v3 | documentId=2: v1/v2/v3 모두 `PENDING → EMBEDDED`, `currentSearchVersion` 1→2→3 전환 | 검증됨 |
| 검색 | owner/category 조건으로 smoke 문서 반환 | `opensql-smoke-owner`/`opensql-smoke` 조건에서 documentId=2 반환 | 검증됨 |
| due claim | 미래 0행, 과거 1행 | taskId=4에서 미래 시각 0행, 과거 시각 PENDING 작업 1행 반환. 최초 시간 불일치는 NTP 동기화로 해소 | 검증됨 |
| SKIP LOCKED | 세션 B가 즉시 0행 | 세션 A가 taskId=4를 `FOR UPDATE OF t`로 잠금. 세션 B는 `FOR UPDATE OF t SKIP LOCKED`에서 0행을 77.790ms에 반환 | 검증됨 |

### Outbox 테스트 작업 준비

`documentId=2`의 최신 v3 작업 `ingestion_tasks.id=4`를 전용 DB에서 `PENDING`·due 상태로 준비했다. 당시 `documents.version=3`, `ingestion_tasks.document_version=3`으로 최신 버전 조건도 만족했다. 이후 due claim과 두 세션 `SKIP LOCKED` 검증에는 이 작업 ID를 사용한다.

### 시간 동기화 발견

첫 API smoke에서 애플리케이션이 기록한 `next_attempt_at`은 `2026-08-14`였지만, OpenSQL의 `CURRENT_TIMESTAMP`는 `2026-08-10`이었다. 따라서 due 조건이 거짓이어서 Outbox 작업이 `PENDING`에 머물렀다. VM의 NTP 시간을 동기화한 뒤 재실행하자 worker가 작업을 claim하고 임베딩을 완료했다.

이는 `TIMESTAMP WITH TIME ZONE` 매핑 문제가 아니라 애플리케이션 호스트와 DB 호스트의 물리 시계 불일치다. due 재시도·lease 만료·heartbeat 판단의 전제 조건으로 NTP 동기화를 유지해야 한다.

## 결론

실행 결과를 받은 뒤 아래 형식으로 채운다.

`문제 → 가설/판단 → 실험과 변경 → 측정 결과 → 남은 한계`

문제 → 앱·DB 호스트 시계가 4일 어긋나 due 작업이 claim되지 않음 → VM NTP 동기화 후 API smoke와 due 조건을 재실행 → v1~v3 임베딩·검색, vector(384), due claim, 77.790ms의 SKIP LOCKED 비대기 반환을 확인 → OpenSQL Single만 검증했으며, 실패 임베딩 경로·HA 자동 failover·split brain 방지·다중 노드 복구는 아직 입증되지 않았다.

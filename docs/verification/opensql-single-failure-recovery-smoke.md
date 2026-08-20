# OpenSQL Single 임베딩 실패·수동 재처리 Smoke 검증

이 문서는 #23의 OpenSQL Single 검증 절차다. 실패 주입은 `opensql-smoke` 프로필과 전용 smoke DB에서만 사용한다. 운영 DB, 기본 프로필, CI에서는 활성화되지 않는다.

## 검증 목표

`v1 EMBEDDED → v2 FAILED → current_search_version=1 유지 → 수동 재처리 → v2 EMBEDDED → current_search_version=2` 흐름을 실제 `EmbeddingWorker → EmbeddingResultWriter.handleFailure(...)` 경로로 확인한다.

`[[OPENSQL_SMOKE_FAIL]]` 문구가 든 v2 청크는 smoke 프로필에서 최초 세 번만 실패한다. 세 번째 실패로 자동 재시도 한도를 소진한 뒤, 같은 v2를 수동 재처리하면 실패 횟수가 소진되어 정상 임베딩된다. 실패를 DB 상태 변경으로 흉내 내지 않는다.

## 실행 전제

- 별도 OpenSQL DB(`opensql_smoke`)를 사용한다.
- 앱과 DB 호스트의 NTP 시간이 동기화되어 있다.
- `.env.opensql.local` 등 저장소 밖 위치에서 접속 정보를 불러온다. 암호는 화면 녹화·로그에 표시하지 않는다.

```bash
export OPENSQL_SMOKE_JDBC_URL='jdbc:postgresql://<host>:5432/opensql_smoke'
export OPENSQL_SMOKE_DB_USERNAME='opensql'
export OPENSQL_SMOKE_DB_PASSWORD='<password>'

./scripts/opensql-smoke.sh start
OPENSQL_SMOKE_POLL_INTERVAL_SECONDS=1 ./scripts/opensql-smoke.sh failure-api
```

`start`는 `opensql-smoke` 프로필로 앱을 기동한다. 따라서 실패 표식이 없는 기존 `api` smoke는 정상 흐름을 그대로 검증한다. `failure-api`는 한 번의 앱 기동에서만 실행한다. 실패 주입 횟수는 앱 메모리에 보관되므로, 다시 실패 흐름을 재현하려면 `stop` 후 `start`로 새 프로세스를 시작한다.

## 기대 API 출력

1. `v1 upload response` 뒤 v1의 `documentStatus/taskStatus=EMBEDDED`
2. `v2 update response` 뒤 v2의 `documentStatus/taskStatus=FAILED`
3. `failed document state`에서 `currentSearchVersion=1`
4. `search while v2 failed`에서 v1 본문만 반환
5. `manual retry response`에서 v2가 `PENDING`
6. v2의 `documentStatus/taskStatus=EMBEDDED`
7. `search after manual retry`에서 v2 본문만 반환

실패 시 `last_error`에는 원문 예외 대신 `EMBEDDING_INFERENCE_FAILED` 안전 요약이 저장된다.

## DB 감사 확인

`failure-api` 출력의 `documentId`를 기록한 뒤 전용 DB에서 실행한다.

```sql
\i scripts/sql/06_failure-recovery-check.sql
```

SQL 첫 줄의 `\set document_id 1`을 실제 ID로 바꾼다. 최종 상태는 v1/v2 모두 청크 임베딩 완료, 문서·v2 task `EMBEDDED`, `current_search_version=2`다. `ingestion_log`에는 최소 `FAILED`, `MANUAL_RETRY`, 마지막 `EMBEDDED` 이벤트가 순서대로 남아야 한다.

## 영상 구성 (90초 내외)

| 구간 | 화면 | 보여 줄 사실 |
| --- | --- | --- |
| 0~10초 | 제목 + OpenSQL Single/전용 DB 표기 | Single 검증이며 HA 실증이 아님 |
| 10~25초 | `start` 출력과 `/v3/api-docs` 준비 상태 | 로컬 ONNX·OpenSQL 연결된 앱 |
| 25~55초 | `failure-api`의 v1 성공, v2 FAILED, 실패 중 검색 출력 | 이전 정상 검색 버전이 유지됨 |
| 55~75초 | 동일 출력의 수동 재처리·v2 검색 전환 | 운영자 개입으로 안전하게 복구됨 |
| 75~90초 | `06_failure-recovery-check.sql` 결과 | DB의 버전·Outbox·감사 이력 증거 |

터미널 글자 크기는 16pt 이상으로 하고, 환경 변수 export 명령은 녹화 전에 실행한다. `set -x`는 사용하지 않는다. 영상 마지막에는 “OpenSQL Single만 검증했으며, HA failover·split brain·다중 노드 복구는 별도 #14 범위”라고 명시한다.

## 결과 기록

| 항목 | 실제 결과 | 판정 |
| --- | --- | --- |
| OpenSQL Single failure-api | 2026-08-18, commit `f216e43`, documentId=3에서 v1 성공·v2 실패·수동 재처리·v2 복구 완료 | 통과 |
| v2 FAILED 동안 v1 검색 유지 | v2 `FAILED`, `current_search_version=1`, v1 본문만 검색 결과로 반환 | 통과 |
| 수동 재처리 뒤 v2 검색 전환 | 수동 재처리 후 v2 `EMBEDDED`, `current_search_version=2`, v2 본문만 검색 결과로 반환 | 통과 |
| DB 감사 이력 (`FAILED` → `MANUAL_RETRY` → `EMBEDDED`) | v2의 세 이벤트와 안전 요약 오류 메시지 확인 | 통과 |

### DB 최종 확인

- `documents`: `version=2`, `current_search_version=2`, `status=EMBEDDED`
- `document_versions`: v1·v2 모두 청크 1개 중 1개 임베딩 완료 및 `EMBEDDED`
- `ingestion_tasks`: v1·v2 모두 `EMBEDDED`; v2의 최종 `attempt_count=1`은 수동 재처리가 새 처리 주기로 시도 횟수를 초기화한 결과
- `ingestion_log`: v1 `CREATED`/`EMBEDDED`, v2 `UPDATED`/`FAILED`/`MANUAL_RETRY`/`EMBEDDED` 순서 확인

이 결과는 OpenSQL Single 환경의 기능 검증이며, 다중 노드 HA failover 검증은 포함하지 않는다.

실행 뒤 날짜, Git commit, DB 버전, API 출력, SQL 결과를 이 표에 기록한다.

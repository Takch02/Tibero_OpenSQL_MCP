# Engineering Standards

모든 기여자는 이 문서와 루트 `AGENTS.md`를 따른다. 상세 실행·테스트·커밋 절차는 각각 [workflow.md](./workflow.md), [testing-standards.md](./testing-standards.md), [commit-harness.md](./commit-harness.md)를 따른다.

## 기본 원칙

- 기존 컨벤션을 우선하고, 요청 범위 밖 리팩터링·의존성·공개 API 변경은 하지 않는다.
- 최소 변경으로 구현한다. 중복은 세 번 이상일 때만 공통화를 검토한다.
- 민감 값·인증정보·벡터 원문 전체를 코드·테스트·로그에 남기지 않는다.
- 입력은 Controller/MCP 경계에서 검증하고, 예외를 삼키지 않는다.

## 계층과 API

`Controller → Service → Repository` 구조를 유지한다.

| 계층 | 책임                                           |
| --- |----------------------------------------------|
| Controller | 요청 검증, 요청 DTO 바인딩, Service 호출, springdoc 문서화 |
| Service | 유스케이스, 비즈니스 규칙, 트랜잭션 경계                      |
| Repository | 데이터 접근과 원자적 claim 쿼리. 일반 비즈니스 정책·도메인 예외 결정 금지 |

- Entity를 API에 직접 노출하지 않고 DTO를 사용한다.
- 도메인 객체와 DTO 변환은 Controller·Service·Mapper 중 한 곳에서 수행하고, 동일 흐름에서 분산하지 않는다.
- 기존 응답 형식은 요청 없이 바꾸지 않는다.
- Controller는 `@Tag`, `@Operation`, `@ApiResponse`로 문서화한다.
- 도메인 예외는 `exception` 패키지에서 `TiberoMcpException`을 상속하고 `ErrorCode` enum을 지정한다. `IllegalStateException`에 문자열을 직접 넣지 않으며, `GlobalExceptionHandler`는 enum의 코드·HTTP 상태로 `ErrorResponse(code, message)`를 만든다.

## 패키지 경계

| 패키지 | 책임 |
| --- | --- |
| `embedding` | 로컬 ONNX 임베딩과 배치 추론 |
| `ingestion` | 업로드·청킹·버전·Outbox 작업·임베딩 반영 |
| `search` | 권한·메타데이터·벡터 검색 결합 |
| `mcp` | `search`를 MCP 도구로 노출 |
| `chaos` | OpenSQL 장애 실험 하네스 |

의존 방향은 `ingestion`/`search` → `embedding`, `mcp` → `search`만 허용한다.

## 데이터 정합성 규칙

- `owner_id`는 검색 권한 경계다. 검색 SQL 안에서 필터링한다.
- `documents.version`은 최신 원문 버전이고, `current_search_version`은 검색에 노출할 마지막 정상 임베딩 버전이다.
- 새 버전이 `PENDING` 또는 `FAILED`여도 이전 `current_search_version` 검색 결과를 유지한다.
- 과거 버전은 수정하지 않는다. 복원은 과거 원문으로 새 버전을 만든다.
- 삭제는 논리 삭제이며, 검색과 일반 조회에서 제외한다.
- `ingestion_log`는 감사 이력, `ingestion_tasks`는 워커가 점유하는 Outbox 작업이다.
- 워커가 claim할 작업 상태는 `ingestion_tasks.status = PENDING`을 기준으로 판단한다. `documents.status`는 문서 처리 결과(`PENDING`/`EMBEDDED`/`FAILED`)이며, 재시도 대상 여부를 제한하는 조건으로 사용하지 않는다.
- 중복 점유를 막기 위해 Repository의 단일 `FOR UPDATE SKIP LOCKED` claim 쿼리는 `t.status = 'PENDING'`, `d.deleted_at IS NULL`, `d.version = t.document_version`을 원자적으로 함께 적용한다. 논리 삭제된 문서와 최신 버전이 아닌 작업은 처리하지 않는다.
- 실패 작업을 재시도할 때는 작업 상태를 명시적으로 `PENDING`으로 전이하거나 새 작업을 생성한다. 문서가 `FAILED` 상태여도 위 claim 조건을 만족하면 재시도할 수 있어야 한다.
- 워커는 `FOR UPDATE SKIP LOCKED`로 작업을 claim하고, 느린 임베딩 추론은 DB 트랜잭션 밖에서 수행한다.
- `PROCESSING` 작업은 worker 식별자와 만료 시각을 가진 lease다. 배치 추론 전후 heartbeat로 연장하며, 만료 회수·완료·실패 전이는 작업 행 잠금 안에서 현재 lease 소유자만 수행한다.

## 코드 작성 기준

- 이름으로 의도를 드러내고 메서드는 하나의 책임만 갖게 한다.
- 매직 값은 의미 있는 상수나 설정으로 관리한다.
- 새로 추가하는 클래스와 public 메서드 중 상태 전이·스케줄러·재시도·락·lease처럼 흐름을 추론해야 하는 요소에는 한두 줄 주석을 작성한다. 역할, 안전 규칙, 실패 시 동작 중 필요한 이유를 설명한다.
- 주석은 구현을 반복하지 않는다. 예를 들어 `status = PENDING`을 다시 읽어 주는 대신, 왜 `PENDING`으로 전이되는지와 어떤 동시성 위험을 막는지 기록한다.
- Service의 public 동작을 바꾸면 관찰 가능한 정상·실패·경계 테스트를 함께 추가한다.

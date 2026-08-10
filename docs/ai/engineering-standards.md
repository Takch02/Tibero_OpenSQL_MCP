# Engineering Standards

모든 기여자는 이 문서와 루트 `AGENTS.md`를 따른다. 상세 실행·테스트·커밋 절차는 각각 [workflow.md](./workflow.md), [testing-standards.md](./testing-standards.md), [commit-harness.md](./commit-harness.md)를 따른다.

## 기본 원칙

- 기존 컨벤션을 우선하고, 요청 범위 밖 리팩터링·의존성·공개 API 변경은 하지 않는다.
- 최소 변경으로 구현한다. 중복은 세 번 이상일 때만 공통화를 검토한다.
- 민감 값·인증정보·벡터 원문 전체를 코드·테스트·로그에 남기지 않는다.
- 입력은 Controller/MCP 경계에서 검증하고, 예외를 삼키지 않는다.

## 계층과 API

`Controller → Service → Repository` 구조를 유지한다.

| 계층 | 책임 |
| --- | --- |
| Controller | 요청 검증, DTO 변환, Service 호출, springdoc 문서화 |
| Service | 유스케이스, 비즈니스 규칙, 트랜잭션 경계 |
| Repository | 데이터 접근만. 비즈니스 정책·도메인 예외 결정 금지 |

- Entity를 API에 직접 노출하지 않고 DTO를 사용한다.
- DTO 변환 위치는 Service 또는 Mapper 하나로 통일한다.
- 기존 응답 형식은 요청 없이 바꾸지 않는다.
- Controller는 `@Tag`, `@Operation`, `@ApiResponse`로 문서화한다.
- 도메인 예외는 `exception` 패키지에서 `TiberoMcpException`을 상속하고, `GlobalExceptionHandler`가 `ErrorResponse(code, message)`로 변환한다.

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
- 워커는 `FOR UPDATE SKIP LOCKED`로 작업을 claim하고, 느린 임베딩 추론은 DB 트랜잭션 밖에서 수행한다.

## 코드 작성 기준

- 이름으로 의도를 드러내고 메서드는 하나의 책임만 갖게 한다.
- 매직 값은 의미 있는 상수나 설정으로 관리한다.
- 주석은 구현 설명이 아니라 설계 이유가 필요한 곳에만 작성한다.
- Service의 public 동작을 바꾸면 관찰 가능한 정상·실패·경계 테스트를 함께 추가한다.

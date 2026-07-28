# Engineering Standards

이 문서는 이 저장소에서 코드를 작성할 때 지켜야 할 공통 규칙이다.
Claude Code, Codex를 포함한 모든 AI 에이전트와 사람 기여자에게 동일하게 적용된다.

## 설계 및 변경 원칙

- 기존 구조와 코딩 컨벤션을 우선 따른다. (예: 이 프로젝트는 Spring Boot 4.x, Java 21, 패키지 단위 계층 구조 `com.test_mcp.tibero_mcp.<domain>`)
- 요청 범위를 넘는 리팩터링, 의존성 추가, 공개 API 변경은 하지 않는다.
- 요구사항이 불명확하지만 안전한 가정이 가능하면, 가정을 명시적으로 밝히고 진행한다. 안전하지 않거나 되돌리기 어려운 가정이면 먼저 확인을 구한다.
- 구현 전에 데이터 흐름, 책임 경계, 영향 범위를 확인한 뒤 최소 변경으로 구현한다.
- 새 파일·새 클래스·새 메서드는 필요한 경우에만 만든다. 기존 클래스에 자연스럽게 들어갈 수 있는 로직을 불필요하게 분리하지 않는다.
- 중복 코드는 무조건 추상화하지 않는다. 실제로 반복되는 안정된 패턴일 때만 공통화한다. (2번 반복은 허용, 3번째부터 공통화 검토)

## 계층별 책임

이 프로젝트는 `Controller → Service → Repository` 3계층 구조를 따른다.

| 계층 | 책임 | 하지 말아야 할 것 |
|---|---|---|
| Controller | 요청 검증, 입출력(DTO) 변환, Service 호출 | 비즈니스 로직, Repository 직접 호출 |
| Service | 유스케이스 구현, 비즈니스 규칙, 트랜잭션 경계 | HTTP/요청 관련 관심사, DTO 매핑 세부사항을 Controller 몫으로 남기지 않고 떠넘기기 |
| Repository/DAO | 데이터 접근 (JPA Repository, native query 등) | 비즈니스 정책, 조건 분기에 따른 규칙 판단 |

- Service의 public 메서드를 새로 추가하거나 기존 비즈니스 로직을 수정하면, 반드시 해당 동작을 검증하는 테스트를 추가/수정한다. ([testing-standards.md](./testing-standards.md) 참조)
- 트랜잭션, 권한 검증, 상태 전이, 중복 처리 등 중요한 비즈니스 제약은 테스트로 보호한다.

## API / DTO 규칙

- Controller/API 계층은 Entity(예: `Document`), ORM 모델, 내부 도메인 객체를 요청/응답으로 직접 노출하지 않는다. Request DTO / Response DTO를 사용한다.
- 응답 DTO는 API 계약을 명확히 표현하고, 필요한 필드만 노출한다. 민감 정보, 내부 구현 정보(예: 벡터 원본 배열 전체, 내부 PK 노출 필요성 없는 식별자)는 포함하지 않는다.
- Entity ↔ DTO 변환 책임은 한 계층에 명확히 둔다. 이 프로젝트는 아직 DTO 변환 계층이 없으므로, 처음 도입 시 Service 또는 별도 Mapper(예: `DocumentMapper`) 중 하나로 통일하고 이후 일관되게 따른다.
- 예외 응답도 가능한 한 일관된 에러 응답 형식을 사용한다. 이 프로젝트에 아직 공통 에러 응답 규격이 없으므로, 도입 시 `@ControllerAdvice` 기반 전역 예외 처리 + 공통 에러 응답 DTO(`code`, `message` 등)로 통일한다. (TODO: 최초 API 추가 시 확정)
- 기존 API의 응답 형식 변경은 호환성 영향을 검토하고, 명시적 요청이 없는 한 피한다.

### API 문서화 (Swagger / springdoc-openapi)

- 모든 Controller는 `springdoc-openapi-starter-webmvc-ui`(build.gradle에 구성됨)를 통해 문서화한다.
- Controller 클래스에는 `@Tag(name = ...)`로 API 그룹을, 각 핸들러 메서드에는 `@Operation(summary = ...)`로 목적을 명시한다.
- 주요 응답 케이스는 `@ApiResponse`로 상태 코드와 의미를 함께 문서화한다. (정상 응답 + 대표적인 에러 응답)
- Request/Response DTO 필드에는 필요한 경우 `@Schema(description = ..., example = ...)`로 의미를 보완한다. 필드명만으로 자명하면 생략 가능.
- 로컬 실행 시 `/swagger-ui.html` (또는 springdoc 기본 경로)에서 문서가 최신 상태로 노출되는지 새 Controller 추가 시 확인한다.
- 인증/인가가 필요한 API는 시크릿·토큰 예시 값을 문서 example에 넣지 않는다.

## 코드 품질

- 이름은 역할과 의도를 드러내야 하며 모호한 축약어를 피한다.
- 함수/메서드는 한 가지 책임에 집중한다.
- 매직 넘버/문자열은 의미 있는 상수 또는 타입(enum 등)으로 관리한다. (예: pgvector 차원 수 `3`은 현재 테스트/스키마에 하드코딩되어 있음 — 실제 임베딩 모델 도입 시 상수화 검토)
- null/Optional/예외 처리는 Java/Spring 관례를 따른다. Repository 조회 결과가 없을 수 있는 경우 `Optional`을 사용하고, Service에서 의미 있는 예외로 변환한다.
- 입력값 검증은 신뢰 경계(Controller 진입 지점)에서 수행한다. `jakarta.validation` 애노테이션 사용을 우선 검토한다.
- 비밀값, 토큰, DB 접속 정보, 개인 정보는 코드·테스트 fixture·로그에 넣지 않는다. Testcontainers처럼 테스트 시점에 동적으로 발급되는 자격증명만 사용한다.
- 주석은 "무엇을 하는지"보다 "왜 이렇게 하는지"가 필요한 곳에만 작성한다. (예: pgvector 캐스팅을 위한 `@ColumnTransformer` 사용 이유처럼 프레임워크의 비직관적 동작을 설명할 때)

## 에러 처리 / 로깅 / 보안 기본 원칙

- 예외는 삼키지 않는다. 의미 있는 컨텍스트와 함께 상위로 전파하거나, 적절한 도메인 예외로 변환한다.
- 로그에는 SQL 파라미터의 민감 값, 임베딩 원본 벡터 전체, 인증 정보를 남기지 않는다.
- 외부 입력(업로드 문서 내용, 검색 쿼리 등)은 신뢰하지 않고 검증한다.
- 데이터베이스 자격증명, API 키 등은 환경변수 또는 별도 설정(예: `application-local.yml`, 시크릿 매니저)으로 분리하고 저장소에 커밋하지 않는다.

## 하위 디렉터리별 확장 규칙 (참고)

이 프로젝트는 단일 Spring Boot 모듈이며 모듈 분리 계획이 없다. 따라서 하위 `AGENTS.md`는 두지 않는다.

만약 향후 구조가 바뀌어 하위 디렉터리별 규칙이 필요해지면, 다음 방식을 참고한다: 해당 디렉터리에 `AGENTS.md`(예: `backend/AGENTS.md`)를 두고, 루트 `AGENTS.md`/`docs/ai/*`의 공통 규칙을 대체하지 않으며 그 위에 더해지는 규칙만 작성한다.

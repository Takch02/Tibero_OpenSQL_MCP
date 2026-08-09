# Testing Standards

## 원칙

- 동작 변경 시 테스트 동반 필수 (테스트 없는 변경은 미완료).
- 새 Service public 메서드는 최소 1개 이상 테스트: 정상/실패/경계 포함.
- 버그 수정은 재현 테스트 → 수정 → 통과 순서로.
- 관찰 가능한 동작·비즈니스 규칙을 검증 (구현 세부사항 검증 금지).
- 테스트는 실행 순서에 독립적이어야 함 (`@Transactional` 클래스 단위 자동 롤백 패턴 유지).

## 단위 vs 통합 테스트

- DB/Spring 컨텍스트 없이 검증 가능한 순수 로직(청킹 알고리즘, 포맷 변환, DTO 매핑, Controller 요청 검증 등)은 **단위테스트**(또는 `@WebMvcTest` 슬라이스)로 작성한다. 매 테스트마다 Testcontainers + 전체 Spring 컨텍스트를 띄우는 것은 불필요한 비용이다.
  - 순수 단위테스트 예: `ChunkerTest`(청킹 크기/오버랩 계산), `EmbeddingServiceTest`(`toVectorLiteral` 포맷 변환).
  - `@WebMvcTest(컨트롤러.class)` + `@MockitoBean`(Service) 슬라이스 예: `IngestionControllerValidationTest`, `SearchControllerValidationTest` — 요청 검증(400) 경로는 DB 접근 전에 걸러지므로 Service를 목킹해 DB 없이 컨트롤러+`GlobalExceptionHandler` 매핑만 검증한다.
- 실제 DB 상태 변화(저장/조회), 벡터 연산(`<->`/`<=>`), 트랜잭션 경계, 여러 컴포넌트 조합이 필요한 시나리오는 **통합테스트**(`@SpringBootTest` + Testcontainers)로 남긴다.
- 같은 동작을 통합테스트에서 세부 검증하고 있다면, 로직 자체는 단위테스트로 옮기고 통합테스트는 "실제로 연결되어 동작하는지"(wiring)만 얕게 확인하도록 축소한다(예: `IngestionServiceIntegrationTest`는 청킹 크기/오버랩 계산 대신 `Chunker` 결과가 `chunk_index` 순서대로 저장되는지만 확인).

## DB 테스트

- **Testcontainers** 사용 (`pgvector/pgvector:pg16`). Mock DB나 H2 대체 금지 — vector 타입, `<->`/`<=>` 연산자는 실제 호환 이미지로만 신뢰성 있게 검증 가능.
- 스키마는 Flyway 마이그레이션(`src/main/resources/db/migration`)으로 관리 (`ddl-auto` 미사용). 테스트도 동일 마이그레이션을 실행해 운영 스키마와 항상 일치시킨다.

## 금지 패턴

- 테스트 통과를 위한 운영 코드 약화, 무의미한 assertion.
- 실패 테스트 삭제·`@Disabled`·skip으로 회피 (원인 해결이 원칙).
- sleep 기반 타이밍 의존 테스트.

## 명명

- "조건-행위-결과"가 드러나는 이름. 한글 테스트명 관례 유지 (예: `벡터_검색_동작`).

## 실행

특정 테스트만: `./gradlew test --tests "<FQCN>"`. 전체 명령은 [workflow.md](./workflow.md) 참조.

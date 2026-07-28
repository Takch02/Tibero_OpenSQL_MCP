# AGENTS.md

이 저장소에서 작업하는 모든 AI 에이전트(Claude Code, Codex 등)가 따르는 루트 지침이다.
상세 규칙은 아래 공통 문서가 원본(source of truth)이며, 이 파일은 요약이다. 내용이 다르면 아래 문서가 우선한다.

- [docs/ai/engineering-standards.md](./docs/ai/engineering-standards.md) — 코드 작성, DTO/API, 계층 책임, 에러 처리/로깅/보안
- [docs/ai/testing-standards.md](./docs/ai/testing-standards.md) — 테스트 작성 및 실행 규칙
- [docs/ai/workflow.md](./docs/ai/workflow.md) — 작업 절차, 빌드/테스트 필수 실행 규칙, 명령어 목록

## 프로젝트 개요

Tibero MCP: OpenSQL(PostgreSQL 기반, pgvector) 위에서 문서를 업로드하면 AI가 자동으로 임베딩하고 MCP 기반 시맨틱 검색을 제공하는 문서 관리 플랫폼. 상세 배경은 [CLAUDE.md](./CLAUDE.md) 참조.

- 기술 스택: Java 21, Spring Boot 4.1 (Gradle), Spring Data JPA, PostgreSQL(pgvector), Lombok, Testcontainers, Spotless(Google Java Format), springdoc-openapi(Swagger)
- 계층 구조: `Controller → Service → Repository` (`com.test_mcp.tibero_mcp.<domain>` 패키지 단위)

## 핵심 규칙 요약

1. **최소 변경 원칙**: 기존 컨벤션을 따르고, 요청 범위를 넘는 리팩터링·의존성 추가·공개 API 변경을 하지 않는다. 충돌 시 기존 프로젝트 관례가 우선한다.
2. **계층 분리**: Controller는 DTO 변환·호출만, 비즈니스 로직은 Service, 데이터 접근은 Repository. Entity를 API로 직접 노출하지 않는다.
3. **API 문서화**: 모든 Controller는 springdoc-openapi(Swagger) 애노테이션(`@Tag`, `@Operation`, `@ApiResponse` 등)으로 문서화한다.
4. **테스트 동반**: Service public 메서드 추가/비즈니스 로직 변경 시 테스트를 함께 추가·수정한다. DB 관련 테스트는 Testcontainers(`pgvector/pgvector:pg16`)를 사용한다.
5. **완료 전 필수 검증**: `./gradlew build`, `./gradlew test`, `./gradlew spotlessCheck`(포맷) 실행 후 결과를 보고한다. 실행하지 못했다면 이유를 명시하고 추측으로 통과 처리하지 않는다.
6. **보안**: 비밀값·토큰·개인정보를 코드/테스트/로그에 넣지 않는다.

전체 규칙은 위 3개 문서를 반드시 읽고 따른다. 이 요약본만으로 판단하지 않는다.

## 명령어 (요약)

```bash
./gradlew build                                    # 빌드 (spotlessCheck 포함)
./gradlew test                                     # 전체 테스트 (Docker 필요 — Testcontainers)
./gradlew test --tests "<FQCN>"                     # 특정 테스트만 실행
./gradlew spotlessCheck                             # 포맷 검사만
./gradlew spotlessApply                             # 포맷 자동 적용
```

상세는 [docs/ai/workflow.md](./docs/ai/workflow.md#명령어) 참조.

## 하위 디렉터리 확장

이 프로젝트는 단일 모듈이며 분리 계획이 없어 하위 `AGENTS.md`를 두지 않는다. 참고용 방식은 [docs/ai/engineering-standards.md](./docs/ai/engineering-standards.md#하위-디렉터리별-확장-규칙-참고) 참조.

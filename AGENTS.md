# AGENTS.md

이 저장소 AI 에이전트(Claude Code, Codex 등) 루트 지침. 상세 규칙 원본:

- [docs/ai/engineering-standards.md](./docs/ai/engineering-standards.md) — 코드/DTO/API/계층
- [docs/ai/testing-standards.md](./docs/ai/testing-standards.md) — 테스트
- [docs/ai/workflow.md](./docs/ai/workflow.md) — 절차/명령어/커밋 규칙
- [docs/ai/commit-harness.md](./docs/ai/commit-harness.md) — 커밋 분리·스테이징 점검

## 프로젝트

Tibero MCP: OpenSQL(pgvector) 기반 AI 문서 관리 플랫폼. 배경은 [CLAUDE.md](./CLAUDE.md) 참조.

- 스택: Java 21, Spring Boot 4.1(Gradle), JPA, PostgreSQL(pgvector), Spring AI(로컬 ONNX 임베딩), Flyway, Lombok, Testcontainers, Spotless, springdoc-openapi
- 구조: `Controller → Service → Repository` (`com.test_mcp.tibero_mcp.<domain>`), 도메인 패키지는 `embedding`/`ingestion`/`search`/`mcp`/`chaos`
- `ingestion`은 Outbox 패턴: 업로드(`IngestionService`) → 청크 배치 insert(`embedding=NULL`, status=`PENDING`) → `EmbeddingWorker`가 폴링·배치 추론·`EMBEDDED`/`FAILED` 전이. 상세는 [engineering-standards.md](./docs/ai/engineering-standards.md#패키지모듈-경계) 참조

## 핵심 규칙

1. 최소 변경, 기존 컨벤션 우선. 범위 밖 리팩터링·의존성 추가·공개 API 변경 금지.
2. Entity를 API로 직접 노출 금지 → DTO 사용.
3. Controller는 springdoc-openapi(`@Tag`/`@Operation`/`@ApiResponse`)로 문서화.
4. Service public 메서드 변경 시 테스트 동반 (DB 테스트는 Testcontainers).
5. 완료 전 `rtk gradlew clean build --no-daemon`와 `rtk gradlew cleanTest test --no-daemon`를 **순차로** 실행하고 결과를 보고한다. RTK가 반환돼도 Gradle 자식 프로세스가 남아 있을 수 있으므로, `BUILD SUCCESSFUL`/`BUILD FAILED` 또는 테스트 리포트로 종료를 확인한 뒤에만 다음 Gradle 명령을 실행한다. RTK가 없거나 출력·종료 결과를 신뢰할 수 없으면 기존 Gradle 프로세스가 종료된 것을 먼저 확인하고 같은 명령을 원문 Gradle로 재실행하며 사유를 기록한다.
6. 비밀값·토큰·개인정보를 코드/테스트/로그에 넣지 않음.
7. 커밋 `<type>: <한국어 설명>`, 이슈/PR 제목 `[FEAT]`/`[FIX]` 등.

세부 사항은 위 3개 문서를 따른다.

## 명령어

```bash
rtk gradlew clean build --no-daemon    # 캐시 없이 빌드 + 포맷 검사 출력 압축
rtk gradlew cleanTest test --no-daemon # 캐시 없이 전체 테스트 출력 압축 (Docker 필요)
rtk git status                # Git 상태 출력 압축
./gradlew spotlessApply     # 포맷 자동 적용
```

단일 모듈, 분리 계획 없음 → 하위 `AGENTS.md` 없음.

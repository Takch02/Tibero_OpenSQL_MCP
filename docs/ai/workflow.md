# AI Workflow

코드/테스트 규칙은 [engineering-standards.md](./engineering-standards.md), [testing-standards.md](./testing-standards.md) 참조. 이 문서는 작업 절차만 다룬다.

## 절차

1. **시작 전**: 관련 코드·설정·테스트를 조사하고 영향 범위를 파악한다.
2. **구현**: 최소 범위로 변경하고 테스트를 함께 수정한다.
3. **완료 전 (생략 금지)**: `./gradlew build`(spotlessCheck 포함), `./gradlew test` 실행. 실패 시 수정 후 재검증. 환경 문제로 실행 못하면 추측 통과 금지, 사유를 보고한다.
4. **응답**: 변경 파일/핵심 내용, 실제 실행한 검증 명령과 결과, 미실행 항목과 사유를 보고한다. 테스트 없는 변경은 이유를 짧게 설명한다.

## 커밋 / 이슈 / PR

- 커밋 메시지: `<type>: <한국어 설명>` (`feat`/`fix`/`test` 등). 예: `feat: 문서 업로드 API 추가`
- 이슈/PR 제목: `[FEAT]`, `[FIX]` 등 대괄호 태그로 시작.
- 커밋은 사용자가 명시적으로 요청했을 때만 생성.
- 커밋 분리·명시적 스테이징은 [commit-harness.md](./commit-harness.md)를 따른다.

## 명령어

| 목적 | 명령 |
|---|---|
| 빌드 (+ 포맷 검사) | `./gradlew build` |
| 전체 테스트 | `./gradlew test` (Docker 필요 — Testcontainers) |
| 특정 테스트 | `./gradlew test --tests "<FQCN>"` |
| 포맷 검사 | `./gradlew spotlessCheck` |
| 포맷 자동 적용 | `./gradlew spotlessApply` |

- DB 테스트는 Docker 데몬이 필요하다 (`docker info`로 확인). 미기동 시 실행 불가로 보고하고 임의 skip 금지.
- lint = Spotless(`googleJavaFormat('1.28.0')`, JDK26 호환 위해 버전 고정). Checkstyle/PMD 등 추가 도구는 미도입.

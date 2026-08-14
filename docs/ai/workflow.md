# AI Workflow

코드/테스트 규칙은 [engineering-standards.md](./engineering-standards.md), [testing-standards.md](./testing-standards.md) 참조. 이 문서는 작업 절차만 다룬다.

## 절차

1. **시작 전**: 관련 코드·설정·테스트를 조사하고 영향 범위를 파악한다.
2. **구현**: 최소 범위로 변경하고 테스트를 함께 수정한다.
3. **완료 전 (생략 금지)**: `./gradlew build`(spotlessCheck 포함), `./gradlew test` 실행. 실패 시 수정 후 재검증. 환경 문제로 실행 못하면 추측 통과 금지, 사유를 보고한다.
4. **응답**: 변경 파일/핵심 내용, 실제 실행한 검증 명령과 결과, 미실행 항목과 사유를 보고한다. 테스트 없는 변경은 이유를 짧게 설명한다.

## RTK 출력 필터

- 기본적으로 `rtk-ai/rtk`로 Gradle·Git의 **텍스트 출력**을 압축해 확인한다. JAR 같은 바이너리 빌드 산출물 자체를 필터링하는 도구는 아니다.
- 빌드·테스트의 종료 코드와 Gradle의 테스트 리포트가 성공 여부의 기준이다. RTK의 요약만 보고 성공으로 판단하지 않는다.
- 실패 분석, 명시적으로 전체 diff를 요청한 경우, RTK가 남긴 원문 출력 경로를 따라갈 때는 원문 명령 또는 `build/test-results`를 확인한다.
- `rtk`를 사용할 수 없거나 필터가 실패하면 원문 명령으로 즉시 재실행하고, 그 사실을 결과에 기록한다.

## 커밋 / 이슈 / PR

- 커밋 메시지: `<type>: <한국어 설명>` (`feat`/`fix`/`test` 등). 예: `feat: 문서 업로드 API 추가`
- 이슈/PR 제목: `[FEAT]`, `[FIX]` 등 대괄호 태그로 시작.
- 커밋은 사용자가 명시적으로 요청했을 때만 생성.
- 커밋 분리·명시적 스테이징은 [commit-harness.md](./commit-harness.md)를 따른다.

## 명령어

| 목적 | 명령 |
|---|---|
| 빌드 (+ 포맷 검사) | `rtk gradlew build --no-daemon` |
| 전체 테스트 | `rtk gradlew test --no-daemon` (Docker 필요 — Testcontainers) |
| 특정 테스트 | `rtk gradlew test --tests "<FQCN>" --no-daemon` |
| 포맷 검사 | `./gradlew spotlessCheck` |
| 포맷 자동 적용 | `./gradlew spotlessApply` |
| Git 상태·요약 diff | `rtk git status`, `rtk git diff --stat` |

- DB 테스트는 Docker 데몬이 필요하다 (`docker info`로 확인). 미기동 시 실행 불가로 보고하고 임의 skip 금지.
- lint = Spotless(`googleJavaFormat('1.28.0')`, JDK26 호환 위해 버전 고정). Checkstyle/PMD 등 추가 도구는 미도입.

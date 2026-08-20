# AI Workflow

코드/테스트 규칙은 [engineering-standards.md](./engineering-standards.md), [testing-standards.md](./testing-standards.md) 참조. 이 문서는 작업 절차만 다룬다.

## 절차

1. **시작 전**: 관련 코드·설정·테스트를 조사하고 영향 범위를 파악한다.
2. **구현**: 최소 범위로 변경하고 테스트를 함께 수정한다.
3. **완료 전 (생략 금지)**: `rtk gradlew clean build --no-daemon`(Spotless 포함), `rtk gradlew cleanTest test --no-daemon`를 순차 실행한다. Gradle 캐시 결과만으로 통과를 판단하지 않는다. 실패 시 수정 후 재검증한다. 환경 문제 또는 RTK 출력 문제로 실행 결과를 확인할 수 없으면 원문 Gradle 명령으로 재실행하고 사유를 보고한다.
4. **응답**: 변경 파일/핵심 내용, 실제 실행한 검증 명령과 결과, 미실행 항목과 사유를 보고한다. 테스트 없는 변경은 이유를 짧게 설명한다.

## RTK 출력 필터

- 기본적으로 `rtk-ai/rtk`로 Gradle·Git의 **텍스트 출력**을 압축해 확인한다. JAR 같은 바이너리 빌드 산출물 자체를 필터링하는 도구는 아니다.
- 빌드·테스트의 종료 코드와 Gradle의 테스트 리포트가 성공 여부의 기준이다. RTK의 요약만 보고 성공으로 판단하지 않는다.
- 실패 분석, 명시적으로 전체 diff를 요청한 경우, RTK가 남긴 원문 출력 경로를 따라갈 때는 원문 명령 또는 `build/test-results`를 확인한다.
- RTK가 호출을 먼저 반환해도 Gradle 자식 프로세스가 계속 실행될 수 있다. 같은 저장소에서 `clean`/`cleanTest` Gradle 명령을 겹쳐 실행하면 `build/test-results`의 in-progress 결과 파일이 충돌할 수 있다.
- RTK 출력에 `BUILD SUCCESSFUL` 또는 `BUILD FAILED`가 없으면, 다음 명령 전 아래처럼 현재 저장소의 Gradle 프로세스가 남았는지 확인한다.

  ```bash
  project_path="$(pwd -P)"
  ps -axo pid=,command= | rg "${project_path}/gradle|${project_path}/build/tmp/test" || true
  ```

- 위 명령에 결과가 있으면 종료할 때까지 기다리고, `build/test-results/test`의 XML 또는 Gradle daemon 로그에서 최종 결과를 확인한다. 프로세스가 끝난 뒤에도 종료 코드·테스트 리포트를 확인할 수 없을 때만 같은 원문 Gradle 명령을 **단독으로** 재실행하고 그 사유를 기록한다.

## 커밋 / 이슈 / PR

- 커밋 메시지: `<type>: <한국어 설명>` (`feat`/`fix`/`test` 등). 예: `feat: 문서 업로드 API 추가`
- 이슈/PR 제목: `[FEAT]`, `[FIX]` 등 대괄호 태그로 시작.
- 커밋은 사용자가 명시적으로 요청했을 때만 생성.
- 커밋 분리·명시적 스테이징은 [commit-harness.md](./commit-harness.md)를 따른다.

## 명령어

| 목적 | 명령 |
|---|---|
| 빌드 (+ 포맷 검사) | `rtk gradlew clean build --no-daemon` |
| 전체 테스트 | `rtk gradlew cleanTest test --no-daemon` (Docker 필요 — Testcontainers) |
| 특정 테스트 | `rtk gradlew cleanTest test --tests "<FQCN>" --no-daemon` |
| 포맷 검사 | `./gradlew spotlessCheck` |
| 포맷 자동 적용 | `./gradlew spotlessApply` |
| Git 상태·요약 diff | `rtk git status`, `rtk git diff --stat` |

- DB 테스트는 Docker 데몬이 필요하다 (`docker info`로 확인). 미기동 시 실행 불가로 보고하고 임의 skip 금지.
- lint = Spotless(`googleJavaFormat('1.28.0')`, JDK26 호환 위해 버전 고정). Checkstyle/PMD 등 추가 도구는 미도입.
- 원문 fallback 예: `./gradlew clean build --no-daemon`, `./gradlew cleanTest test --no-daemon`.

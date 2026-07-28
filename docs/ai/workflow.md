# AI Workflow

이 문서는 Claude Code, Codex를 포함한 모든 AI 에이전트가 이 저장소에서 작업할 때 따르는 절차를 정의한다.
코드/DTO/테스트 규칙 자체는 [engineering-standards.md](./engineering-standards.md), [testing-standards.md](./testing-standards.md)를 참조한다. 이 문서는 "언제, 어떤 순서로" 그 규칙을 적용하는지를 다룬다.

## 1. 작업 시작 전

- 관련 코드, 설정, 테스트, 문서를 조사한다. (`grep`/`find` 또는 코드 탐색 도구 활용)
- 기존 패턴과 실행 명령을 확인한다. 이 프로젝트는 Gradle 기반 Spring Boot이며, 아래 "명령어" 절의 명령을 기본으로 사용한다.
- 변경 대상과 영향 범위(어떤 계층, 어떤 클래스, 어떤 API/테스트가 영향받는지)를 간단히 정리한 뒤 구현을 시작한다.

## 2. 구현 중

- [engineering-standards.md](./engineering-standards.md)의 원칙에 따라 최소 범위로 변경한다.
- 코드 변경과 테스트 변경을 함께 수행한다. 코드만 바꾸고 테스트를 나중으로 미루지 않는다.
- 새 Service public 메서드 또는 변경된 비즈니스 규칙에 대한 테스트 누락 여부를 스스로 점검한다. ([testing-standards.md](./testing-standards.md) 참조)

## 3. 작업 완료 직전 — 절대 생략 금지

다음을 반드시 실행하고, 결과(성공/실패)를 확인한다.

1. 프로젝트의 공식 빌드 명령을 실행한다.
2. 변경과 직접 관련된 테스트를 실행한다.
3. 가능하면 전체 테스트 스위트도 실행한다.
4. lint, typecheck, format 검사 등이 프로젝트에 설정되어 있으면 함께 실행한다. (현재 이 프로젝트에는 미설정 — 아래 TODO 참조)
5. 실패 시 원인을 수정하고 다시 검증한다. 실패를 무시하거나 "일부만 통과했으니 괜찮다"고 임의 판단하지 않는다.
6. 환경 문제 등으로 명령을 실행하지 못했다면(예: Docker 미기동으로 Testcontainers 테스트 불가), **추측으로 통과 처리하지 말고** 실행하지 못한 이유와 영향 범위를 최종 응답에 명확히 남긴다.

## 4. 최종 응답

- 변경한 파일과 핵심 변경 내용을 짧게 설명한다.
- 실제 실행한 검증 명령과 성공/실패 결과를 제공한다. (명령 실행 없이 "테스트를 통과할 것"이라고 서술하지 않는다.)
- 실행하지 못한 검증 항목이 있으면 그 이유를 명시한다.
- 테스트가 없는 변경(예: 문서, 순수 설정 변경)이라면 왜 테스트가 불필요한지 짧게 설명한다.

## 명령어

이 프로젝트는 **Java 21 + Gradle + Spring Boot 4.1** 기반이다. (`build.gradle`, `gradlew` 기준)

| 목적 | 명령 |
|---|---|
| 빌드 | `./gradlew build` |
| 전체 테스트 | `./gradlew test` |
| 특정 테스트만 실행 | `./gradlew test --tests "<FQCN>"` (예: `./gradlew test --tests "com.test_mcp.tibero_mcp.DocumentServiceIntegrationTest"`) |
| 클린 빌드 | `./gradlew clean build` |
| 포맷 검사 (lint) | `./gradlew spotlessCheck` (`check`/`build` 태스크에 포함되어 자동 실행됨) |
| 포맷 자동 적용 | `./gradlew spotlessApply` |

DB 연동 통합 테스트는 **Testcontainers**로 Docker 컨테이너(`pgvector/pgvector:pg16`)를 실행한다. 로컬/CI 모두 **Docker 데몬이 실행 중이어야** 테스트가 통과한다. Docker를 사용할 수 없는 환경이면 해당 테스트는 실행 불가로 보고하고 임의로 skip 처리하지 않는다.

### 정적 분석 / lint / format — Spotless (Google Java Format)

`build.gradle`에 `com.diffplug.spotless` 플러그인이 구성되어 있다. `googleJavaFormat('1.28.0')`을 사용하며, `check`/`build` 태스크 실행 시 `spotlessCheck`가 자동으로 함께 실행되어 포맷 위반이 있으면 빌드가 실패한다.

- 코드 작성/수정 후에는 커밋 전에 `./gradlew spotlessApply`로 포맷을 맞춘다.
- 새로 작업을 완료할 때 "3. 작업 완료 직전" 절차의 lint 검사는 곧 `./gradlew spotlessCheck`(또는 이를 포함하는 `build`)를 의미한다.
- Checkstyle, PMD, ErrorProne 등 추가 정적 분석 도구는 현재 도입하지 않는다. 필요성이 명확해지면 별도로 검토한다.

### 실행 전 확인 사항

- Docker 데몬 기동 여부: `docker info` (Testcontainers 테스트에 필수)
- JDK 21 사용 여부: `./gradlew -v` 출력의 JVM 버전 확인 (Gradle toolchain이 자동으로 JDK 21을 사용하도록 설정되어 있음 — `build.gradle`의 `java.toolchain`)

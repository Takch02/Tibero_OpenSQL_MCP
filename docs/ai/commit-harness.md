# Commit Split Harness

PR은 변경 파일 수가 아니라 **독립적으로 이해·검증 가능한 변경 이유**를 기준으로 커밋을 나눈다.

## 적용 규칙

1. 작업 시작 전 PR 안의 커밋 계획을 짧게 작성한다.
2. 기능마다 구현·마이그레이션·그 기능을 검증하는 테스트를 같은 커밋에 둔다.
3. 별도 기능, README·설계 문서, 포맷 정리는 기능 커밋과 분리한다.
4. 기존에 있던 사용자 변경이나 이슈 범위 밖 파일은 명시적으로 제외한다.
5. 모든 커밋은 가능한 한 컴파일 가능해야 하며, PR 전 전체 `rtk gradlew clean build --no-daemon`와 `rtk gradlew cleanTest test --no-daemon`를 순차 실행한다. 각 명령의 Gradle 프로세스 종료와 결과를 확인한 뒤 다음 명령을 실행한다. RTK가 없거나 출력·종료 결과를 확인할 수 없으면, 실행 중인 Gradle이 없는지 확인한 뒤 같은 원문 Gradle 명령을 단독으로 재실행한다. 상세 절차는 [workflow.md](./workflow.md#rtk-출력-필터)를 따른다.

## 스테이징 점검 순서

```bash
# 전체 작업 트리를 추가하지 않는다. 커밋 단위의 파일만 명시적으로 추가한다.
git add -- path/to/file-one path/to/file-two

# 실제 포함 범위와 공백 오류를 확인한다.
git diff --cached --check
rtk git diff --cached --stat

# 커밋 후 PR의 커밋 경계를 확인한다.
rtk git log --oneline origin/main..HEAD
```

- 스테이징 최종 검토에서 전체 patch가 필요하거나 RTK가 오류를 축약하면 `git diff --cached` 원문을 확인한다.

## 분리 기준

| 함께 둔다 | 분리한다 |
| --- | --- |
| 하나의 동작을 완성하는 구현·스키마·테스트 | 독립 기능 |
| 해당 기능을 설명하는 최소 변경 | README, 설계 기록, 개발 일지 |
| 원자성을 보장하려는 마이그레이션과 코드 | 무관한 설정·포맷 변경 |

예를 들어 Outbox 작업 생성과 다중 워커 claim은 각각 독립적인 기능이다. 전자는 작업을 영속화하고, 후자는 여러 워커가 그 작업을 안전하게 분배한다. 따라서 각각 별도 커밋으로 검토한다.

# Tibero MCP - AI 문서 관리 플랫폼

> **Claude Code 진입점.** 작업 규칙은 [AGENTS.md](./AGENTS.md)와 `docs/ai/*`(engineering-standards, testing-standards, workflow — 공통 규칙 원본)를 따른다.

## 프로젝트 개요

티맥스티베로의 **OpenSQL** 기반 위에서, 문서를 업로드하면 AI가 자동으로 내용을 이해하고 맞춤형 검색을 제공하는 **자동화된 AI 문서 관리 플랫폼**을 구축한다.

주관: 티맥스티베로 (hocheol_shin@tibero.com)

## 핵심 목표

- **고가용성**: OpenSQL 클러스터로 단일 노드 장애 시에도 무중단 운영
- **자동화**: 문서 업로드 → AI 임베딩 → 벡터 동기화까지 원스톱 처리
- **MCP 기반 검색**: 최신 MCP(Model Context Protocol) 규격의 검색 API 제공

## 기술 스택

| 영역 | 기술 |
|------|------|
| DB | OpenSQL (PostgreSQL 기반, pgvector 활용) |
| Backend | Spring Boot (Gradle) |
| AI | 문서 임베딩 (벡터화), 시맨틱 검색 |
| API | MCP 기반 검색 API |

## OpenSQL 특징

- PostgreSQL 기반 오픈소스 DBMS 플랫폼
- 모든 노드 실시간 클러스터 상태 공유
- 자동 장애 감지 및 리더 선출 (Split Brain 방지)
- ARIA, SEED 등 암호화 확장 모듈 내장
- 기술 문서: https://docs.tibero.com/tmaxopensql

## 구현 기능 목록

1. **문서 업로드**: 다양한 포맷의 문서 수신 및 저장
2. **자동 임베딩**: 업로드된 문서를 AI가 벡터로 변환
3. **메타데이터/버전 관리**: 문서 버전 추적 및 메타정보 관리
4. **변경 로그 기반 동기화**: 문서 변경사항을 벡터 DB에 실시간 반영
5. **MCP 기반 검색 API**: MCP 프로토콜을 통한 시맨틱 검색 제공

## 프로젝트 구조

```
tibero_mcp/
├── src/
│   └── main/
│       ├── java/       # Spring Boot 애플리케이션
│       └── resources/  # 설정 파일
├── build.gradle        # Gradle 빌드 설정
└── settings.gradle
```

## 참고 자료

- OpenSQL 제품 소개: https://docs.tibero.com/tmaxopensql
- GTS 기술 지원: https://support.tibero.com/hc/ko

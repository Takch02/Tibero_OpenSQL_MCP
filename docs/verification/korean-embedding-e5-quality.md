# Korean embedding quality pilot

## Purpose

기존 영어 중심 `all-MiniLM-L6-v2`와 다국어 `multilingual-e5-small`을 같은 OpenSQL Single·pgvector 환경에서 비교한다. 외부 임베딩 API는 사용하지 않고 Spring AI Transformers로 로컬 ONNX 추론을 수행한다.

## Model and environment

- Model: `intfloat/multilingual-e5-small`
- Model revision: `614241f622f53c4eeff9890bdc4f31cfecc418b3`
- ONNX artifact SHA-256: `ca456c06b3a9505ddfd9131408916dd79290368331e7d76bb621f1cba6bc8665`
- Tokenizer SHA-256: `0b44a9d7b51c3c62626640cda0e2c2f70fdacdc25bbbd68038369d14ebdf4c39`
- License: MIT License
- Source: <https://huggingface.co/intfloat/multilingual-e5-small>
- Runtime: Spring AI Transformers 2.0.0, local ONNX, 384 dimensions
- Database: Tmax OpenSQL Single + pgvector, fresh `opensql_quality_e5` database
- Retrieval rule: document `passage: <chunk>`, query `query: <query>`

`vector_dims(embedding)`은 모든 생성 청크에서 384으로 확인했다. 기존 `vector(384)`과 `vector_cosine_ops` HNSW 인덱스는 변경하지 않았다.

## Evaluation set

소유자 `korean-quality-pilot` 아래의 단일 청크 문서 6개를 새 DB에 등록했다.

| Expected document | Questions |
| --- | ---: |
| 관리자 계정 보안 정책 | 2 |
| 서비스 장애 대응 절차 | 2 |
| 휴가 및 근태 운영 지침 | 2 |
| 배포 및 변경 관리 기준 | 2 |
| 개인정보 보관 및 파기 기준 | 2 |
| 법인카드 비용 정산 안내 | 2 |

질문은 다중 인증·회사 VPN, 장애 최초 기록·사후 분석 기한, 긴급 휴가·연차 승인 기한, 롤백 계획·긴급 배포, 개인정보 파기·수탁사 점검, 법인카드 영수증·개인 식사 지출을 묻는 12개 한국어 질의다. 각 질의의 기대 문서가 상위 결과에 포함되는지 측정했다.

## Result

| Model | Recall@1 | Recall@3 |
| --- | ---: | ---: |
| all-MiniLM-L6-v2 | 3 / 12 (25.0%) | 8 / 12 (66.7%) |
| multilingual-e5-small | 12 / 12 (100.0%) | 12 / 12 (100.0%) |

E5 결과는 REST `GET /api/search`와 MCP `search_documents`를 각각 호출해 확인했다. 예를 들어 `관리자 권한 계정에는 비밀번호 외에 어떤 인증 수단이 필요합니까?`는 MCP에서 `관리자 계정 보안 정책`을 1위로 반환했다.

## Boundary

이 파일럿은 문서 6개·질문 12개인 소규모 비교다. 모델 일반 성능의 벤치마크나 모든 한국어 업무 문서에서의 품질 보장을 의미하지 않는다. 더 큰 문서 집합, 다중 청크 문서, 도메인별 질문과 실패 사례는 후속 평가에서 추가한다.

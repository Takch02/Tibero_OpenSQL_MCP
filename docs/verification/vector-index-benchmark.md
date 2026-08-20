# pgvector HNSW 인덱스 성능 검증

이 문서는 이슈 #15의 재현 절차와 결과 기록 양식이다. 테스트 컨테이너 통과는 PostgreSQL 호환 확인이고, 아래 결과만 OpenSQL Single 실환경 성능 근거로 사용한다.

## 사전 조건

- OpenSQL Single에서 `vector` 확장이 활성화되어 있어야 한다.
- `postgres` 등 전용 측정 테이블을 생성·삭제할 권한이 있는 계정으로 `opensql_smoke`에 접속한다.
- 애플리케이션 테이블을 건드리지 않도록 스크립트는 `vector_benchmark_*` 전용 테이블만 사용한다.

## 실행

각 크기는 워밍업과 별개로 독립 실행한다. `08`의 exact 결과를 만든 뒤 같은 데이터셋에서 `09`를 실행해야 recall 비교가 가능하다.

```bash
cd /path/to/tibero_mcp

# 1,000 청크부터 시작한다. 10,000은 같은 순서로 반복한다.
/home/opensql/bin/psql -U postgres -d opensql_smoke -v chunk_count=1000 \
  -f scripts/sql/07_vector-benchmark-setup.sql | tee build/vector-benchmark-1000-setup.log
/home/opensql/bin/psql -U postgres -d opensql_smoke \
  -f scripts/sql/08_vector-benchmark-baseline.sql | tee build/vector-benchmark-1000-baseline.log
/home/opensql/bin/psql -U postgres -d opensql_smoke \
  -f scripts/sql/09_vector-benchmark-hnsw.sql | tee build/vector-benchmark-1000-hnsw.log
/home/opensql/bin/psql -U postgres -d opensql_smoke \
  -f scripts/sql/10_vector-benchmark-cleanup.sql
```

`10000`에도 같은 명령을 반복한다. 현재 VM에서는 10만 청크를 필수 범위에서 제외한다. 이 문서의 결과는 조건별 단일 실행 기록이다. 후속 반복 측정에서는 인덱스 생성과 검색을 분리한 뒤 워밍업 1회와 최소 20회 표본으로 p50·p95를 기록한다.

## 기록할 값

| 데이터셋 | 조건 | baseline 실행계획/시간 | HNSW 실행계획/시간 | recall@10 | HNSW 크기 | 생성 시간 |
| --- | --- | --- | --- | --- | --- | --- |
| 1,000 | 무필터 | 미측정 | 미측정 | 미측정 | 미측정 | 미측정 |
| 1,000 | owner | 미측정 | 미측정 | - | - | - |
| 1,000 | owner + category | 미측정 | 미측정 | - | - | - |
| 10,000 | 무필터 | 미측정 | 미측정 | 미측정 | 미측정 | 미측정 |
| 10,000 | owner | 미측정 | 미측정 | - | - | - |
| 10,000 | owner + category | 미측정 | 미측정 | - | - | - |

## 2026-08-19 OpenSQL Single 측정 결과

### 환경

- DB: Tmax OpenSQL Single, PostgreSQL 17.8, pgvector 0.8.1
- VM: Rocky Linux 9.7, x86_64 UTM 에뮬레이션, 4 vCPU, 8 GiB 메모리
- 벡터: 외부 모델 호출 없이 생성한 결정적 384차원 synthetic vector
- 검색: 코사인 거리(`<=>`), `LIMIT 10`
- 표본: 각 조건의 단일 실행 결과. p50/p95/p99은 아직 측정하지 않았다.

### 1,000 청크

| 조건 | baseline 계획 / 실행 시간 | HNSW 계획 / 실행 시간 | recall@10 | 판단 |
| --- | --- | --- | --- | --- |
| 무필터 | Seq Scan / 334.920 ms | Seq Scan / 338.957 ms | 1.0 | HNSW 미선택 |
| owner | Seq Scan / 170.358 ms | Seq Scan / 171.244 ms | - | HNSW 미선택 |
| owner + category | Seq Scan / 69.978 ms | Seq Scan / 70.137 ms | - | HNSW 미선택 |

1천 청크에서는 인덱스 탐색 비용보다 전체 스캔 비용이 낮다고 플래너가 판단했다. HNSW 인덱스를 생성한 상태에서도 세 조건 모두 `Seq Scan`이 선택됐으며, 성능 향상 주장은 하지 않는다.

### 10,000 청크

| 조건 | baseline 계획 / 실행 시간 | HNSW 계획 / 실행 시간 | 단일 실행 비율 | recall@10 |
| --- | --- | --- | --- | --- |
| 무필터 | Seq Scan / 3,403.620 ms | HNSW Index Scan / 11.211 ms | 약 304배 | 1.0 |
| owner | Seq Scan / 1,711.538 ms | HNSW Index Scan / 7.018 ms | 약 244배 | 미측정 |
| owner + category | Seq Scan / 695.874 ms | HNSW Index Scan / 7.741 ms | 약 90배 | 미측정 |

- HNSW 생성 시간: 11.317초
- HNSW 인덱스 크기: 20 MB
- `vector_benchmark_chunks` 테이블 크기: 16 MB
- HNSW 무필터 검색은 `idx_vector_benchmark_embedding_hnsw`의 `Index Scan`을 사용했고, shared buffer hit은 364개였다. baseline 무필터는 2,001개였다.

### 결론

1천 청크에서는 HNSW를 선택하지 않는 것이 정상임을 확인했고, 1만 청크에서는 실제 OpenSQL 실행계획이 HNSW Index Scan으로 전환되는 것을 확인했다. 단일 실행만 비교하면 무필터 조건에서 3,403.620 ms에서 11.211 ms로 줄었다.

이 결과는 현재 VM·synthetic vector·질의 벡터 하나의 실험 결과다. 반복 측정 p50/p95, 다양한 질의 벡터에서의 owner/category 필터 recall@10, 실제 기업 문서 임베딩 분포는 후속 검증 항목으로 남긴다.

## 해석 경계

- 1천 건처럼 작은 데이터셋에서 `Seq Scan`이 선택돼도 오류가 아니다. HNSW의 탐색·초기화 비용보다 전체 스캔이 쌀 수 있다.
- owner/category 필터는 HNSW 내부 필터가 아니라 조인 뒤 조건으로 적용될 수 있다. 조건별 `EXPLAIN (ANALYZE, BUFFERS)`을 따로 남긴다.
- `recall_at_10`은 HNSW 결과와 baseline exact top-10의 교집합 크기/10이다. 한 쿼리·한 벡터의 결과이므로 일반적인 정확도 주장으로 확대하지 않는다.
- OpenSQL Single 수치는 고가용성 또는 운영 트래픽 성능을 증명하지 않는다.

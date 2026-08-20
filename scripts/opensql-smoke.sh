#!/usr/bin/env bash

# OpenSQL Single 전용 DB에 애플리케이션을 연결해 API smoke를 반복 실행한다.
# DB 문법 검증은 scripts/sql의 쿼리를 psql 두 세션에서 직접 실행한다.
set -euo pipefail

readonly PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly SMOKE_DIR="${PROJECT_ROOT}/build/opensql-smoke"
readonly PID_FILE="${SMOKE_DIR}/application.pid"
readonly LOG_FILE="${SMOKE_DIR}/application.log"
readonly PORT="${OPENSQL_SMOKE_PORT:-8081}"
readonly BASE_URL="http://127.0.0.1:${PORT}"
readonly MAX_POLL_ATTEMPTS="${OPENSQL_SMOKE_MAX_POLL_ATTEMPTS:-24}"
readonly POLL_INTERVAL_SECONDS="${OPENSQL_SMOKE_POLL_INTERVAL_SECONDS:-5}"

usage() {
  cat <<'USAGE'
Usage: ./scripts/opensql-smoke.sh <start|status|api|failure-api|stop>

Required environment variables:
  OPENSQL_SMOKE_JDBC_URL       Disposable OpenSQL database JDBC URL
  OPENSQL_SMOKE_DB_USERNAME    Database user
  OPENSQL_SMOKE_DB_PASSWORD    Database password

Optional environment variables:
  OPENSQL_SMOKE_PORT=8081
  OPENSQL_SMOKE_MAX_POLL_ATTEMPTS=24
  OPENSQL_SMOKE_POLL_INTERVAL_SECONDS=5

Commands:
  start   Start the application on the OpenSQL smoke database and wait for /v3/api-docs.
  status  Show the application process and API readiness.
  api     Run upload -> embedding -> search -> update -> delete -> restore smoke flow.
  failure-api  Run v1 success -> v2 injected failure -> manual retry -> v2 recovery flow.
  stop    Stop the application before manual SQL checks such as SKIP LOCKED.
USAGE
}

require_connection_settings() {
  : "${OPENSQL_SMOKE_JDBC_URL:?OPENSQL_SMOKE_JDBC_URL is required}"
  : "${OPENSQL_SMOKE_DB_USERNAME:?OPENSQL_SMOKE_DB_USERNAME is required}"
  : "${OPENSQL_SMOKE_DB_PASSWORD:?OPENSQL_SMOKE_DB_PASSWORD is required}"
}

is_running() {
  [[ -f "${PID_FILE}" ]] && kill -0 "$(cat "${PID_FILE}")" 2>/dev/null
}

wait_for_api() {
  local attempt
  for ((attempt = 1; attempt <= MAX_POLL_ATTEMPTS; attempt++)); do
    if curl --fail-with-body --silent --show-error "${BASE_URL}/v3/api-docs" >/dev/null; then
      return
    fi
    sleep "${POLL_INTERVAL_SECONDS}"
  done

  echo "OpenSQL smoke application did not become ready. log=${LOG_FILE}" >&2
  tail -n 80 "${LOG_FILE}" >&2 || true
  exit 1
}

start() {
  require_connection_settings
  mkdir -p "${SMOKE_DIR}"

  if is_running; then
    echo "Application is already running. pid=$(cat "${PID_FILE}") url=${BASE_URL}"
    return
  fi

  rm -f "${PID_FILE}"
  (
    export SPRING_DATASOURCE_URL="${OPENSQL_SMOKE_JDBC_URL}"
    export SPRING_DATASOURCE_USERNAME="${OPENSQL_SMOKE_DB_USERNAME}"
    export SPRING_DATASOURCE_PASSWORD="${OPENSQL_SMOKE_DB_PASSWORD}"
    export SPRING_PROFILES_ACTIVE="opensql-smoke${SPRING_PROFILES_ACTIVE:+,${SPRING_PROFILES_ACTIVE}}"
    export SERVER_PORT="${PORT}"
    cd "${PROJECT_ROOT}"
    exec ./gradlew bootRun --no-daemon
  ) >"${LOG_FILE}" 2>&1 &
  echo "$!" >"${PID_FILE}"

  wait_for_api
  echo "Application started. pid=$(cat "${PID_FILE}") url=${BASE_URL} log=${LOG_FILE}"
}

status() {
  if is_running; then
    echo "Application process is running. pid=$(cat "${PID_FILE}")"
  else
    echo "Application process is not running."
  fi

  if curl --fail-with-body --silent --show-error "${BASE_URL}/v3/api-docs" >/dev/null; then
    echo "API is ready. url=${BASE_URL}"
  else
    echo "API is not ready. url=${BASE_URL}"
  fi
}

response_field() {
  local response="$1"
  local field="$2"
  printf '%s' "${response}" | sed -nE "s/.*\\\"${field}\\\":(\\\"[^\\\"]*\\\"|[0-9]+).*/\\1/p" | tr -d '"'
}

wait_for_embedding() {
  local document_id="$1"
  local expected_version="$2"
  local owner_id="${3:-opensql-smoke-owner}"
  local response
  local attempt

  for ((attempt = 1; attempt <= MAX_POLL_ATTEMPTS; attempt++)); do
    response="$(
      curl --fail-with-body --silent --show-error \
        "${BASE_URL}/api/documents/${document_id}/ingestion?ownerId=${owner_id}"
    )"
    printf 'ingestion status: %s\n' "${response}"

    if [[ "${response}" == *"\"version\":${expected_version}"* \
      && "${response}" == *'"documentStatus":"EMBEDDED"'* \
      && "${response}" == *'"taskStatus":"EMBEDDED"'* ]]; then
      return
    fi
    sleep "${POLL_INTERVAL_SECONDS}"
  done

  echo "Embedding did not finish for document=${document_id}, version=${expected_version}" >&2
  exit 1
}

wait_for_failure() {
  local document_id="$1"
  local expected_version="$2"
  local owner_id="${3:-opensql-smoke-owner}"
  local response
  local attempt

  for ((attempt = 1; attempt <= MAX_POLL_ATTEMPTS; attempt++)); do
    response="$(
      curl --fail-with-body --silent --show-error \
        "${BASE_URL}/api/documents/${document_id}/ingestion?ownerId=${owner_id}"
    )"
    printf 'ingestion status: %s\n' "${response}"

    if [[ "${response}" == *"\"version\":${expected_version}"* \
      && "${response}" == *'"documentStatus":"FAILED"'* \
      && "${response}" == *'"taskStatus":"FAILED"'* ]]; then
      return
    fi
    sleep "${POLL_INTERVAL_SECONDS}"
  done

  echo "Embedding did not fail for document=${document_id}, version=${expected_version}" >&2
  exit 1
}

api_smoke() {
  if ! is_running; then
    echo "Run '$0 start' before '$0 api'." >&2
    exit 1
  fi
  wait_for_api

  local run_id
  run_id="$(date +%Y%m%d%H%M%S)"
  local idempotency_key="opensql-smoke-${run_id}"
  local upload_response
  local document_id
  local update_response
  local search_response

  upload_response="$(
    curl --fail-with-body --silent --show-error \
      -X POST "${BASE_URL}/api/documents" \
      -H 'Content-Type: application/json' \
      --data "{\"idempotencyKey\":\"${idempotency_key}\",\"title\":\"OpenSQL smoke ${run_id}\",\"content\":\"OpenSQL Single 환경의 벡터 검색과 문서 버전을 검증하는 테스트 문서입니다.\",\"ownerId\":\"opensql-smoke-owner\",\"category\":\"opensql-smoke\"}"
  )"
  document_id="$(response_field "${upload_response}" documentId)"
  if [[ -z "${document_id}" ]]; then
    echo "Could not read documentId from upload response: ${upload_response}" >&2
    exit 1
  fi
  printf 'upload response: %s\n' "${upload_response}"

  wait_for_embedding "${document_id}" 1

  search_response="$(
    curl --fail-with-body --silent --show-error --get "${BASE_URL}/api/search" \
      --data-urlencode 'query=OpenSQL Single 벡터 검색 검증' \
      --data-urlencode 'ownerId=opensql-smoke-owner' \
      --data-urlencode 'category=opensql-smoke' \
      --data-urlencode 'limit=5'
  )"
  printf 'search response: %s\n' "${search_response}"
  if [[ "${search_response}" != *"\"documentId\":${document_id}"* ]]; then
    echo "Search response does not contain uploaded document=${document_id}." >&2
    exit 1
  fi

  update_response="$(
    curl --fail-with-body --silent --show-error \
      -X PUT "${BASE_URL}/api/documents/${document_id}" \
      -H 'Content-Type: application/json' \
      --data "{\"ownerId\":\"opensql-smoke-owner\",\"expectedVersion\":1,\"title\":\"OpenSQL smoke ${run_id} v2\",\"content\":\"OpenSQL Single 환경에서 새 버전의 임베딩과 검색 노출 전환을 검증하는 수정 문서입니다.\",\"category\":\"opensql-smoke\"}"
  )"
  printf 'update response: %s\n' "${update_response}"
  wait_for_embedding "${document_id}" 2

  curl --fail-with-body --silent --show-error \
    -X DELETE "${BASE_URL}/api/documents/${document_id}?ownerId=opensql-smoke-owner&expectedVersion=2" \
    >/dev/null

  local restore_response
  restore_response="$(
    curl --fail-with-body --silent --show-error \
      -X POST "${BASE_URL}/api/documents/${document_id}/versions/1/restore" \
      -H 'Content-Type: application/json' \
      --data '{"ownerId":"opensql-smoke-owner","expectedVersion":2}'
  )"
  printf 'restore response: %s\n' "${restore_response}"
  wait_for_embedding "${document_id}" 3

  echo "API smoke succeeded. documentId=${document_id} runId=${run_id}"
  echo "After '$0 stop', find this document's ingestion_tasks.id and set it as task_id in scripts/sql/00, 03, 04, and 05."
}

failure_api_smoke() {
  if ! is_running; then
    echo "Run '$0 start' before '$0 failure-api'." >&2
    exit 1
  fi
  wait_for_api

  local run_id
  run_id="$(date +%Y%m%d%H%M%S)"
  local idempotency_key="opensql-failure-smoke-${run_id}"
  local owner_id="opensql-failure-smoke-owner-${run_id}"
  local v1_content="이 문서는 실패 중에도 계속 검색되는 안정 버전입니다."
  local v2_content="[[OPENSQL_SMOKE_FAIL]] 수동 재처리 뒤 검색으로 전환되는 새 버전입니다."
  local upload_response
  local document_id
  local update_response
  local document_response
  local search_response
  local retry_response

  upload_response="$(
    curl --fail-with-body --silent --show-error \
      -X POST "${BASE_URL}/api/documents" \
      -H 'Content-Type: application/json' \
      --data "{\"idempotencyKey\":\"${idempotency_key}\",\"title\":\"OpenSQL failure smoke ${run_id}\",\"content\":\"${v1_content}\",\"ownerId\":\"${owner_id}\",\"category\":\"opensql-smoke\"}"
  )"
  document_id="$(response_field "${upload_response}" documentId)"
  if [[ -z "${document_id}" ]]; then
    echo "Could not read documentId from upload response: ${upload_response}" >&2
    exit 1
  fi
  printf 'v1 upload response: %s\n' "${upload_response}"
  wait_for_embedding "${document_id}" 1 "${owner_id}"

  update_response="$(
    curl --fail-with-body --silent --show-error \
      -X PUT "${BASE_URL}/api/documents/${document_id}" \
      -H 'Content-Type: application/json' \
      --data "{\"ownerId\":\"${owner_id}\",\"expectedVersion\":1,\"title\":\"OpenSQL failure smoke ${run_id} v2\",\"content\":\"${v2_content}\",\"category\":\"opensql-smoke\"}"
  )"
  printf 'v2 update response: %s\n' "${update_response}"
  wait_for_failure "${document_id}" 2 "${owner_id}"

  document_response="$(
    curl --fail-with-body --silent --show-error \
      "${BASE_URL}/api/documents/${document_id}?ownerId=${owner_id}"
  )"
  printf 'failed document state: %s\n' "${document_response}"
  if [[ "${document_response}" != *'"currentSearchVersion":1'* ]]; then
    echo "Failed v2 unexpectedly replaced currentSearchVersion: ${document_response}" >&2
    exit 1
  fi

  search_response="$(
    curl --fail-with-body --silent --show-error --get "${BASE_URL}/api/search" \
      --data-urlencode 'query=OpenSQL 실패 복구' \
      --data-urlencode "ownerId=${owner_id}" \
      --data-urlencode 'category=opensql-smoke' \
      --data-urlencode 'limit=5'
  )"
  printf 'search while v2 failed: %s\n' "${search_response}"
  if [[ "${search_response}" != *"${v1_content}"* || "${search_response}" == *"${v2_content}"* ]]; then
    echo "Search did not retain only v1 while v2 failed." >&2
    exit 1
  fi

  retry_response="$(
    curl --fail-with-body --silent --show-error \
      -X POST "${BASE_URL}/api/documents/${document_id}/ingestion/retry" \
      -H 'Content-Type: application/json' \
      --data "{\"ownerId\":\"${owner_id}\",\"expectedVersion\":2}"
  )"
  printf 'manual retry response: %s\n' "${retry_response}"
  wait_for_embedding "${document_id}" 2 "${owner_id}"

  search_response="$(
    curl --fail-with-body --silent --show-error --get "${BASE_URL}/api/search" \
      --data-urlencode 'query=OpenSQL 실패 복구' \
      --data-urlencode "ownerId=${owner_id}" \
      --data-urlencode 'category=opensql-smoke' \
      --data-urlencode 'limit=5'
  )"
  printf 'search after manual retry: %s\n' "${search_response}"
  if [[ "${search_response}" != *"${v2_content}"* || "${search_response}" == *"${v1_content}"* ]]; then
    echo "Search did not switch to v2 after manual retry." >&2
    exit 1
  fi

  echo "Failure recovery smoke succeeded. documentId=${document_id} runId=${run_id}"
}

stop() {
  if ! is_running; then
    rm -f "${PID_FILE}"
    echo "Application process is not running."
    return
  fi

  local pid
  pid="$(cat "${PID_FILE}")"
  kill "${pid}"
  for _ in {1..15}; do
    if ! kill -0 "${pid}" 2>/dev/null; then
      rm -f "${PID_FILE}"
      echo "Application stopped. pid=${pid}"
      return
    fi
    sleep 1
  done

  echo "Application is still stopping. Wait until '$0 status' reports it is not running before manual SQL checks. pid=${pid}" >&2
  exit 1
}

case "${1:-}" in
  start) start ;;
  status) status ;;
  api) api_smoke ;;
  failure-api) failure_api_smoke ;;
  stop) stop ;;
  *) usage; exit 1 ;;
esac

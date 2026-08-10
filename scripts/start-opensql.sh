#!/usr/bin/env bash
set -euo pipefail

OPENSQL_HOME="${OPENSQL_HOME:-/home/opensql}"
ETCDCTL="$OPENSQL_HOME/bin/etcdctl"
PG_ISREADY="$OPENSQL_HOME/bin/pg_isready"
PATRONICTL="$OPENSQL_HOME/bin/patronictl"

if [[ "$(id -un)" != "opensql" ]]; then
  echo "opensql 사용자로 실행하세요: su - opensql"
  exit 1
fi

wait_for() {
  local description="$1"
  shift

  for _ in {1..15}; do
    if "$@" >/dev/null 2>&1; then
      echo "[OK] $description"
      return 0
    fi
    sleep 1
  done

  echo "[ERROR] $description 준비에 실패했습니다."
  return 1
}

if "$ETCDCTL" endpoint health >/dev/null 2>&1; then
  echo "[OK] etcd가 이미 실행 중입니다."
else
  echo "[INFO] etcd를 시작합니다."
  sh "$OPENSQL_HOME/scripts/start_etcd.sh"
fi
wait_for "etcd" "$ETCDCTL" endpoint health

if "$PG_ISREADY" -h 127.0.0.1 -p 5432 >/dev/null 2>&1; then
  echo "[OK] PostgreSQL이 이미 접속을 받고 있습니다."
else
  echo "[INFO] Patroni를 시작합니다."
  sh "$OPENSQL_HOME/scripts/start_patroni.sh"
fi
wait_for "PostgreSQL" "$PG_ISREADY" -h 127.0.0.1 -p 5432

"$PATRONICTL" -c "$OPENSQL_HOME/etc/patroni/patroni.yml" list

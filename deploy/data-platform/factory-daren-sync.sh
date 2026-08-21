#!/usr/bin/env bash
set -euo pipefail

mode="${1:-}"
case "$mode" in
  incremental|daily-full|weekly-full) ;;
  *) echo "usage: $0 {incremental|daily-full|weekly-full}" >&2; exit 2 ;;
esac

set -a
. /opt/data-platform/shared/config/data-platform.env
set +a
: "${DATA_PLATFORM_DORIS_USERNAME:=root}"
: "${DATA_PLATFORM_DORIS_PASSWORD:=}"
export DATA_PLATFORM_DORIS_USERNAME DATA_PLATFORM_DORIS_PASSWORD

exec 9>/run/lock/factory-daren-sync.lock
if ! flock -n 9; then
  echo "another factory-daren synchronization is still running; skip mode=$mode"
  exit 0
fi

api_base="http://127.0.0.1:${SERVER_PORT:-48080}/admin-api"
max_parallel="${FACTORY_DAREN_SYNC_PARALLELISM:-4}"
poll_seconds="${FACTORY_DAREN_SYNC_POLL_SECONDS:-5}"
max_polls="${FACTORY_DAREN_SYNC_MAX_POLLS:-720}"
failure_file="$(mktemp)"
jobs_file="$(mktemp)"
trap 'rm -f "$failure_file" "$jobs_file"' EXIT

login_payload="$(jq -n --arg username "$LEMAN_ADMIN_USERNAME" --arg password "$LEMAN_ADMIN_PASSWORD" \
  '{username:$username,password:$password}')"
login_response="$(curl -fsS -H 'Content-Type: application/json' -d "$login_payload" "$api_base/system/auth/login")"
token="$(jq -r '.data.accessToken // empty' <<<"$login_response")"
if [[ -z "$token" ]]; then
  echo "data platform login failed" >&2
  exit 1
fi

page=1
while :; do
  response="$(curl -fsS -G -H "Authorization: Bearer $token" \
    --data-urlencode "pageNo=$page" --data-urlencode 'pageSize=100' "$api_base/data-platform/sync-job/page")"
  jq -c '.data.list[]? | select(.status == 0 and (.code | startswith("factory_daren_erp_")))' \
    <<<"$response" >>"$jobs_file"
  total="$(jq -r '.data.total // 0' <<<"$response")"
  if (( page * 100 >= total )); then break; fi
  ((page += 1))
done

case "$mode" in
  incremental) suffix='_inc' ;;
  daily-full) suffix='_full_daily' ;;
  weekly-full) suffix='_full_weekly' ;;
esac

truncate_target() {
  local database="$1" table="$2"
  if [[ ! "$database" =~ ^[A-Za-z0-9_]+$ || ! "$table" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo "refusing unsafe Doris identifier: $database.$table" >&2
    return 1
  fi
  MYSQL_PWD="${DATA_PLATFORM_DORIS_PASSWORD:-}" mysql --protocol=TCP -h 127.0.0.1 -P 9030 \
    -u "$DATA_PLATFORM_DORIS_USERNAME" -e "TRUNCATE TABLE \`$database\`.\`$table\`"
}

run_one() {
  local job_json="$1" job_id code database table latest execute_response run_id status attempt
  job_id="$(jq -r '.id' <<<"$job_json")"
  code="$(jq -r '.code' <<<"$job_json")"
  database="$(jq -r '.targetDatabase' <<<"$job_json")"
  table="$(jq -r '.targetTable' <<<"$job_json")"
  latest="$(curl -fsS -G -H "Authorization: Bearer $token" --data-urlencode "jobId=$job_id" \
    --data-urlencode 'pageNo=1' --data-urlencode 'pageSize=1' "$api_base/data-platform/sync-job/run/page")"
  status="$(jq -r '.data.list[0].status // "NONE"' <<<"$latest")"
  if [[ "$status" == "PENDING" || "$status" == "RUNNING" ]]; then
    echo "job_skip_running=$code"
    return 0
  fi
  if [[ "$mode" != "incremental" ]]; then truncate_target "$database" "$table"; fi
  execute_response="$(curl -fsS -X POST -H "Authorization: Bearer $token" \
    "$api_base/data-platform/sync-job/execute?id=$job_id")"
  if [[ "$(jq -r '.code' <<<"$execute_response")" != "0" ]]; then
    echo "job_submit_failed=$code" >&2
    return 1
  fi
  run_id="$(jq -r '.data' <<<"$execute_response")"
  echo "job_submitted=$code,run_id=$run_id"
  for ((attempt=1; attempt<=max_polls; attempt++)); do
    latest="$(curl -fsS -G -H "Authorization: Bearer $token" --data-urlencode "jobId=$job_id" \
      --data-urlencode 'pageNo=1' --data-urlencode 'pageSize=1' "$api_base/data-platform/sync-job/run/page")"
    status="$(jq -r '.data.list[0].status // "UNKNOWN"' <<<"$latest")"
    case "$status" in
      SUCCESS) echo "job_success=$code,run_id=$run_id"; return 0 ;;
      FAILED|TIMEOUT) echo "job_failed=$code,run_id=$run_id,status=$status" >&2; return 1 ;;
    esac
    sleep "$poll_seconds"
  done
  echo "job_poll_timeout=$code,run_id=$run_id" >&2
  return 1
}

active=0
selected=0
while IFS= read -r job_json; do
  code="$(jq -r '.code' <<<"$job_json")"
  [[ "$code" == *"$suffix" ]] || continue
  ((selected += 1))
  (run_one "$job_json" || echo "$code" >>"$failure_file") &
  ((active += 1))
  if (( active >= max_parallel )); then
    wait -n || true
    ((active -= 1))
  fi
done <"$jobs_file"
wait || true

failures="$(wc -l <"$failure_file")"
echo "factory_daren_sync_complete mode=$mode selected=$selected failures=$failures"
if (( failures > 0 )); then
  sort -u "$failure_file" >&2
  exit 1
fi

#!/usr/bin/env bash
#
# demo.sh -- builds the pipeline, starts the REST API (specification-capstone.md Task 2),
# submits every transaction from sample-transactions.json over HTTP, and prints a
# formatted summary. Zero manual steps: run `./demo.sh` from a clean checkout.
#
# Idempotency note (Task 3 Implementation Notes): this script clears shared/ at the start
# of every run, matching the /run-pipeline skill's existing behavior, so each run is a
# fresh, presentable demo rather than depending on leftover state from a previous run.
set -euo pipefail

PORT="${PORT:-8080}"
BASE_URL="http://localhost:${PORT}"
JAR="target/banking-pipeline-exec.jar"
TRANSACTIONS_FILE="sample-transactions.json"
READY_TIMEOUT_SECONDS=60
SERVER_LOG="$(mktemp -t demo-server-log)"
SERVER_PID=""

log() { printf '%s\n' "$*" >&2; }

cleanup() {
    if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
        log "Stopping server (pid $SERVER_PID)..."
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
    fi
    rm -f "$SERVER_LOG"
}
trap cleanup EXIT INT TERM

check_dependencies() {
    local missing=()
    for tool in mvn curl jq; do
        command -v "$tool" >/dev/null 2>&1 || missing+=("$tool")
    done
    if [[ ${#missing[@]} -gt 0 ]]; then
        log "Missing required tool(s): ${missing[*]}. Install them and re-run."
        exit 1
    fi
    if [[ ! -f "$TRANSACTIONS_FILE" ]]; then
        log "Cannot find ${TRANSACTIONS_FILE} in $(pwd). Run this script from homework-6/."
        exit 1
    fi
}

build() {
    log "Building banking-pipeline-exec.jar (mvn package -DskipTests)..."
    mvn -q package -DskipTests
    if [[ ! -f "$JAR" ]]; then
        log "Build finished but ${JAR} was not produced. Aborting."
        exit 1
    fi
}

reset_shared_state() {
    log "Clearing shared/ for a fresh demo run..."
    find shared/input shared/processing shared/output shared/results -type f ! -name '.gitkeep' -delete 2>/dev/null || true
}

start_server() {
    log "Starting the API on port ${PORT}..."
    SERVER_PORT="$PORT" java -jar "$JAR" > "$SERVER_LOG" 2>&1 &
    SERVER_PID=$!
}

wait_for_ready() {
    log "Waiting for the API to become ready (timeout: ${READY_TIMEOUT_SECONDS}s)..."
    local waited=0
    # /api/v1/transactions (list) always returns 200 [] even with zero results, unlike
    # /api/v1/pipeline/summary which 404s until at least one transaction is processed --
    # so it's the correct readiness probe here (a summary-based probe would never
    # succeed on a fresh run, since nothing populates results before submission starts).
    until curl -sf --max-time 3 "${BASE_URL}/api/v1/transactions" > /dev/null 2>&1; do
        if ! kill -0 "$SERVER_PID" 2>/dev/null; then
            log "Server process exited unexpectedly. Last log output:"
            tail -n 40 "$SERVER_LOG" >&2
            exit 1
        fi
        if (( waited >= READY_TIMEOUT_SECONDS )); then
            log "Server did not become ready within ${READY_TIMEOUT_SECONDS}s. Last log output:"
            tail -n 40 "$SERVER_LOG" >&2
            exit 1
        fi
        sleep 1
        waited=$((waited + 1))
    done
    log "API is ready."
}

submit_transactions() {
    log "Submitting transactions from ${TRANSACTIONS_FILE}..."
    local count=0
    local id http_code
    while IFS= read -r txn; do
        id=$(jq -r '.transaction_id' <<<"$txn")
        http_code=$(curl -s --max-time 10 -o /dev/null -w '%{http_code}' -X POST "${BASE_URL}/api/v1/transactions" \
            -H "Content-Type: application/json" -d "$txn")
        if [[ "$http_code" != "200" ]]; then
            log "Submission failed for transaction '${id}': HTTP ${http_code}"
            exit 1
        fi
        count=$((count + 1))
    done < <(jq -c '.[]' "$TRANSACTIONS_FILE")
    log "Submitted ${count} transaction(s)."
}

print_summary() {
    local summary
    summary=$(curl -sf --max-time 10 "${BASE_URL}/api/v1/pipeline/summary")

    log ""
    log "=== Pipeline Summary ==="
    printf '%-14s %-20s %s\n' "TRANSACTION_ID" "STATUS" "REASON_CODE"
    printf '%s\n' "------------------------------------------------------------"
    jq -r '.outcomes[] | [.transaction_id, .status, (.reason_code // "-")] | @tsv' <<<"$summary" \
        | while IFS=$'\t' read -r id status reason; do
            printf '%-14s %-20s %s\n' "$id" "$status" "$reason"
        done

    log ""
    log "Status counts:"
    jq -r '.counts_by_status | to_entries[] | "  \(.key): \(.value)"' <<<"$summary"
    log ""
    log "Total: $(jq -r '.results_written' <<<"$summary") transaction(s) processed."
}

main() {
    check_dependencies
    build
    reset_shared_state
    start_server
    wait_for_ready
    submit_transactions
    print_summary
}

main

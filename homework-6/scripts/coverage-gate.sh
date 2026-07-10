#!/usr/bin/env bash
# Coverage-gate PreToolUse hook (Claude Code): reads a Bash tool call on stdin as
# JSON, and if the command is a `git push`, runs `mvn verify` (which enforces the
# JaCoCo 80% line-coverage rule bound to the verify phase in pom.xml) before
# letting the push through. Any other command passes through immediately.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

INPUT_JSON="$(cat)"
COMMAND="$(printf '%s' "$INPUT_JSON" | jq -r '.tool_input.command // empty')"

if ! printf '%s' "$COMMAND" | grep -Eq '(^|[[:space:]])git[[:space:]]+push([[:space:]]|$)'; then
    exit 0
fi

echo "[coverage-gate] git push detected — running 'mvn verify' (JaCoCo >= 80% line coverage) in $PROJECT_DIR" >&2

if ! (cd "$PROJECT_DIR" && mvn -B verify > /tmp/homework6-coverage-gate.log 2>&1); then
    COVERAGE_LINE="$(grep -m1 'Rule violated' /tmp/homework6-coverage-gate.log || echo 'See /tmp/homework6-coverage-gate.log for details.')"
    REASON="Coverage gate failed: homework-6 line coverage is below the required 80% threshold. ${COVERAGE_LINE} Push blocked — add/expand tests (see specification.md Task 5), then run 'mvn verify' locally to confirm, before pushing."
    printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":%s}}\n' \
        "$(printf '%s' "$REASON" | jq -Rs .)"
    exit 2
fi

echo "[coverage-gate] Coverage check passed (>= 80% line coverage) — allowing push." >&2
exit 0

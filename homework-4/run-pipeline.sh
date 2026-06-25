#!/usr/bin/env bash
#
# run-pipeline.sh — Single-command orchestrator for the 4-agent bug-fix pipeline.
#
# Launches the agents in the correct order with NO manual per-agent invocation
# between steps. For each stage it auto-loads the agent's related skills, runs the
# agent on the model declared in its *.agent.md frontmatter, and gates on the
# verdict the agent writes to its result file before moving on:
#
#     Bug Research Verifier  ->  Bug Fixer  ->  Security Verifier
#                                          \->  Unit Test Generator
#
# (Security Verifier and Unit Test Generator both depend only on the Bug Fixer, so
#  they are independent; this runner executes them sequentially for clean logs.)
#
# Upstream inputs (out of scope for this homework's 4 agents) are required to
# pre-exist in the bug folder:
#   - research/codebase-research.md  (from the Bug Researcher)
#   - implementation-plan.md         (from the Bug Planner)
#
# Usage:
#   ./run-pipeline.sh [BUG_ID]              # run the full pipeline (default BUG_ID=001)
#   ./run-pipeline.sh [BUG_ID] --dry-run    # validate setup & print the plan only
#   ./run-pipeline.sh --help
#
# Tip: capture a run log with:  ./run-pipeline.sh 001 | tee context/bugs/001/pipeline-run.log
#
# Configuration (environment variables):
#   AGENT_CLI         CLI used to launch an agent headlessly      (default: claude)
#   AGENT_CLI_FLAGS   extra flags for non-interactive runs        (default: --permission-mode acceptEdits)
#   FAIL_ON_CRITICAL  exit non-zero if security finds CRITICAL    (default: 1)
#
set -euo pipefail

# --------------------------------------------------------------------------- #
# Paths & configuration
# --------------------------------------------------------------------------- #
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AGENTS_DIR="$ROOT_DIR/agents"

AGENT_CLI="${AGENT_CLI:-claude}"
AGENT_CLI_FLAGS="${AGENT_CLI_FLAGS:---permission-mode acceptEdits}"
FAIL_ON_CRITICAL="${FAIL_ON_CRITICAL:-1}"

DRY_RUN=0
BUG_ID="001"
OVERALL=0

# --------------------------------------------------------------------------- #
# Pretty logging
# --------------------------------------------------------------------------- #
if [ -t 1 ]; then
  BOLD=$'\033[1m'; RED=$'\033[31m'; GRN=$'\033[32m'; YLW=$'\033[33m'; CYN=$'\033[36m'; RST=$'\033[0m'
else
  BOLD=; RED=; GRN=; YLW=; CYN=; RST=
fi
log()  { printf '%s\n' "${CYN}[pipeline]${RST} $*"; }
ok()   { printf '%s\n' "${GRN}[pipeline] ok${RST} $*"; }
warn() { printf '%s\n' "${YLW}[pipeline] !!${RST} $*"; }
err()  { printf '%s\n' "${RED}[pipeline] xx${RST} $*" >&2; }
die()  { err "$*"; exit 1; }
rel()  { printf '%s' "${1#"$ROOT_DIR"/}"; }

show_help() { awk 'NR==1{next} /^#/{sub(/^# ?/,""); print; next} {exit}' "${BASH_SOURCE[0]}"; }

# --------------------------------------------------------------------------- #
# Helpers: parse agent frontmatter / skills, build the system prompt
# --------------------------------------------------------------------------- #

# Print the markdown body of a *.agent.md / skill file (everything after the
# closing --- of the YAML frontmatter).
strip_frontmatter() {
  awk 'NR==1 && $0=="---"{fm=1; next} fm==1 && $0=="---"{fm=2; next} fm!=1{print}' "$1"
}

# Echo the model declared in the agent frontmatter (e.g. "opus", "sonnet").
model_of() { grep -m1 '^model:[[:space:]]*' "$1" | sed -E 's/^model:[[:space:]]*//' | tr -d '\r' || true; }

# Echo the skill file references found in an agent file (e.g. skills/foo.md).
skills_of() { grep -oE 'skills/[A-Za-z0-9._/-]+\.md' "$1" 2>/dev/null | sort -u || true; }

# Compose the full system prompt for an agent: its instructions plus the content
# of every skill it references (this is the "loads their related skills
# automatically" step).
build_system_prompt() {
  local agent_file="$1" s sp
  echo "# AGENT INSTRUCTIONS"
  strip_frontmatter "$agent_file"
  while IFS= read -r s; do
    [ -z "$s" ] && continue
    sp="$ROOT_DIR/$s"
    if [ -f "$sp" ]; then
      printf '\n# LOADED SKILL: %s\n' "$s"
      strip_frontmatter "$sp"
    else
      warn "skill referenced but not found: $s"
    fi
  done < <(skills_of "$agent_file")
}

# --------------------------------------------------------------------------- #
# Helpers: prerequisites & gating
# --------------------------------------------------------------------------- #
require_agent() { [ -f "$1" ] || die "Missing agent definition: $(rel "$1")"; }

check_prereq() {
  # $1 = path, $2 = human description
  if [ -f "$1" ]; then
    ok "prerequisite present: $(rel "$1")"
  elif [ "$DRY_RUN" -eq 1 ]; then
    warn "prerequisite MISSING: $(rel "$1")  ($2)"
  else
    die "prerequisite MISSING: $(rel "$1")  ($2). Provide it before running the pipeline."
  fi
}

gate_file() { [ -f "$1" ] || die "Gate failed: expected output not produced: $(rel "$1")"; }

# Echo the first verdict-like token found in a file, upper-cased.
first_token() { grep -oiE "$2" "$1" 2>/dev/null | head -n1 | tr '[:lower:]' '[:upper:]' || true; }

# --------------------------------------------------------------------------- #
# Launch one agent stage
# --------------------------------------------------------------------------- #
run_agent() {
  # $1 = stage label, $2 = agent file, $3 = user prompt
  local label="$1" agent_file="$2" user_prompt="$3"
  local model skills sys
  model="$(model_of "$agent_file")"
  skills="$(skills_of "$agent_file" | paste -sd ',' - || true)"

  printf '\n'
  log "${BOLD}${label}${RST}"
  log "  agent : $(rel "$agent_file")"
  log "  model : ${model:-<unset>}"
  log "  skills: ${skills:-<none>}"

  if [ "$DRY_RUN" -eq 1 ]; then
    warn "  dry-run: skipping '$AGENT_CLI' invocation"
    return 0
  fi

  command -v "$AGENT_CLI" >/dev/null 2>&1 \
    || die "Agent CLI '$AGENT_CLI' not found. Install it or set AGENT_CLI=<your-cli>."

  sys="$(build_system_prompt "$agent_file")"
  # shellcheck disable=SC2086
  "$AGENT_CLI" -p "$user_prompt" --model "${model:-sonnet}" \
    --append-system-prompt "$sys" $AGENT_CLI_FLAGS
}

# --------------------------------------------------------------------------- #
# Argument parsing
# --------------------------------------------------------------------------- #
for arg in "$@"; do
  case "$arg" in
    --dry-run)  DRY_RUN=1 ;;
    --help|-h)  show_help; exit 0 ;;
    -*)         die "Unknown option: $arg (try --help)" ;;
    *)          BUG_ID="$arg" ;;
  esac
done

# --------------------------------------------------------------------------- #
# Main pipeline
# --------------------------------------------------------------------------- #
BUG_DIR="$ROOT_DIR/context/bugs/$BUG_ID"
RESEARCH_DIR="$BUG_DIR/research"
RESEARCH_MD="$RESEARCH_DIR/codebase-research.md"
VERIFIED_MD="$RESEARCH_DIR/verified-research.md"
PLAN_MD="$BUG_DIR/implementation-plan.md"
FIX_MD="$BUG_DIR/fix-summary.md"
SEC_MD="$BUG_DIR/security-report.md"
TEST_MD="$BUG_DIR/test-report.md"

printf '%s\n' "${BOLD}=== 4-Agent Bug-Fix Pipeline ===${RST}"
log "bug id     : $BUG_ID"
log "bug folder : $(rel "$BUG_DIR")"
log "agent CLI  : $AGENT_CLI ${AGENT_CLI_FLAGS}"
[ "$DRY_RUN" -eq 1 ] && warn "DRY RUN — validating setup and printing the plan only"

mkdir -p "$RESEARCH_DIR"

# Agents must all exist
require_agent "$AGENTS_DIR/research-verifier.agent.md"
require_agent "$AGENTS_DIR/bug-fixer.agent.md"
require_agent "$AGENTS_DIR/security-verifier.agent.md"
require_agent "$AGENTS_DIR/unit-test-generator.agent.md"
ok "all 4 agent definitions found"

# Upstream prerequisites (Bug Researcher / Bug Planner are out of scope here)
check_prereq "$RESEARCH_MD" "Bug Researcher output: research/codebase-research.md"

# --- Stage 1: Bug Research Verifier ---------------------------------------- #
run_agent "Stage 1/4 — Bug Research Verifier" "$AGENTS_DIR/research-verifier.agent.md" \
  "Verify the bug research for bug ${BUG_ID}. Read ${RESEARCH_MD} and check every reference and snippet against the source under src/. Apply the research-quality-measurement skill and write ${VERIFIED_MD} with all required sections."
if [ "$DRY_RUN" -eq 0 ]; then
  gate_file "$VERIFIED_MD"
  case "$(first_token "$VERIFIED_MD" 'CONDITIONAL PASS|PASS|FAIL')" in
    FAIL) die "Gate failed: research verdict is FAIL. Stopping before the Bug Fixer." ;;
    "")   warn "Gate: no explicit verdict found in $(rel "$VERIFIED_MD"); proceeding with caution." ;;
    *)    ok "Gate passed: research verified (proceeding to fix)." ;;
  esac
fi

# Plan is required before the Bug Fixer can run
check_prereq "$PLAN_MD" "Bug Planner output: implementation-plan.md"

# --- Stage 2: Bug Fixer ----------------------------------------------------- #
run_agent "Stage 2/4 — Bug Fixer" "$AGENTS_DIR/bug-fixer.agent.md" \
  "Execute the implementation plan for bug ${BUG_ID}. Read ${PLAN_MD}, apply each change to the source under src/, run the plan's test command after each change, and write ${FIX_MD}. Stop and document if a test fails."
if [ "$DRY_RUN" -eq 0 ]; then
  gate_file "$FIX_MD"
  case "$(first_token "$FIX_MD" 'COMPLETE|BLOCKED')" in
    BLOCKED) die "Gate failed: Bug Fixer reported BLOCKED. Stopping (review ${FIX_MD})." ;;
    "")      warn "Gate: no explicit status in $(rel "$FIX_MD"); proceeding with caution." ;;
    *)       ok "Gate passed: fixes applied (Overall Status COMPLETE)." ;;
  esac
fi

# --- Stage 3: Security Verifier (depends on Bug Fixer) ---------------------- #
run_agent "Stage 3/4 — Security Vulnerabilities Verifier" "$AGENTS_DIR/security-verifier.agent.md" \
  "Perform a security review of the code changed by the Bug Fixer for bug ${BUG_ID}. Read ${FIX_MD} and the changed files, then write ${SEC_MD}. Report only — do not edit code."
if [ "$DRY_RUN" -eq 0 ]; then
  gate_file "$SEC_MD"
  if grep -qiE '\bCRITICAL\b' "$SEC_MD"; then
    warn "Security review reports CRITICAL finding(s) — review ${SEC_MD}."
    [ "$FAIL_ON_CRITICAL" = "1" ] && OVERALL=1
  else
    ok "Security review complete (no CRITICAL findings)."
  fi
fi

# --- Stage 4: Unit Test Generator (depends on Bug Fixer) -------------------- #
run_agent "Stage 4/4 — Unit Test Generator" "$AGENTS_DIR/unit-test-generator.agent.md" \
  "Generate and run unit tests for the code changed by the Bug Fixer for bug ${BUG_ID}. Read ${FIX_MD} and the changed files, write tests for the changed code only (applying the unit-tests-FIRST skill) into the project's test location, run the project's test command, and write ${TEST_MD}."
if [ "$DRY_RUN" -eq 0 ]; then
  gate_file "$TEST_MD"
  case "$(first_token "$TEST_MD" 'PASS|FAIL')" in
    FAIL) err "Unit tests reported FAIL — review ${TEST_MD}."; OVERALL=1 ;;
    "")   warn "Gate: no explicit test status in $(rel "$TEST_MD")." ;;
    *)    ok "Unit tests passed." ;;
  esac
fi

# --- Summary ---------------------------------------------------------------- #
printf '\n%s\n' "${BOLD}=== Pipeline summary (bug ${BUG_ID}) ===${RST}"
if [ "$DRY_RUN" -eq 1 ]; then
  ok "dry run complete — setup validated, no agents were launched."
  exit 0
fi
for f in "$VERIFIED_MD" "$FIX_MD" "$SEC_MD" "$TEST_MD"; do
  if [ -f "$f" ]; then ok "artifact: $(rel "$f")"; else warn "missing artifact: $(rel "$f")"; fi
done
if [ "$OVERALL" -eq 0 ]; then
  ok "PIPELINE SUCCEEDED"
else
  err "PIPELINE COMPLETED WITH FAILURES (see warnings above)"
fi
exit "$OVERALL"

# HOWTORUN — Homework 4 (4-Agent Bug-Fix Pipeline)

All commands are run from the **`homework-4/` project root**:

```bash
cd path/to/gen-ai-software-engineering/homework-4
```

## 1. Prerequisites
- **Python 3.8+** (tested on 3.13). The `calc` app and its tests use only the
  standard library — no `pip install` needed.
- **To run the automated pipeline:** the **Claude Code CLI** (`claude`), installed
  and authenticated. The runner launches each agent through it.
  ```bash
  command -v claude   # should print a path
  ```
  You do **not** need `claude` to run the app or the tests directly (sections 2–3).

## 2. Run the sample app
```bash
PYTHONPATH=src python3 -m calc eval "2 + 3 * 4"     # -> 14
PYTHONPATH=src python3 -m calc percent 25 200        # -> 12.5  (after fix)
PYTHONPATH=src python3 -m calc average 2 4 6          # -> 4.0
```
Security check (after the fix this is rejected instead of executed):
```bash
PYTHONPATH=src python3 -m calc eval "__import__('os').getcwd()"
# -> ValueError: unsupported expression element
```

## 3. Run the tests
```bash
PYTHONPATH=src python3 -m unittest discover -s tests -v
```
- **Before** the pipeline: 2 passing, 4 red (reproducing BUG-1 ×2, BUG-2, SEC-1).
- **After** the pipeline (current state): **39 tests pass** (`OK`).

`pytest` also works if installed: `PYTHONPATH=src pytest tests`.

## 4. Run the full pipeline (single command)
```bash
./run-pipeline.sh 001
```
This runs all four agents in order — Research Verifier → Bug Fixer → Security
Verifier → Unit Test Generator — each on the model from its `*.agent.md`
frontmatter, auto-loading its skills, and gating between stages on the verdict
each agent writes.

Useful variants:
```bash
./run-pipeline.sh 001 --dry-run     # validate setup + print the plan; launches nothing
./run-pipeline.sh --help            # usage and configuration
./run-pipeline.sh 001 | tee context/bugs/001/pipeline-run.log   # capture a run log
```

Configuration (environment variables):
- `AGENT_CLI` — the CLI used to launch each agent (default `claude`).
- `AGENT_CLI_FLAGS` — flags for non-interactive runs (default
  `--permission-mode acceptEdits`).
- `FAIL_ON_CRITICAL` — exit non-zero if the security stage reports a CRITICAL
  finding (default `1`).

> ⚠️ Running the pipeline **modifies `src/calc/`** (applies the fixes) and adds a
> generated test file. Commit the "before" state first if you want the PR diff to
> show the change.

## 5. Pipeline inputs & outputs (per bug, under `context/bugs/001/`)
Inputs (already provided):
- `bug-context.md` — the seeded-issue catalogue.
- `research/codebase-research.md` — Bug Researcher output (upstream).
- `implementation-plan.md` — Bug Planner output (upstream).

Outputs (produced by the run):
- `research/verified-research.md` — Stage 1 (Research Verifier).
- `fix-summary.md` — Stage 2 (Bug Fixer).
- `security-report.md` — Stage 3 (Security Verifier).
- `test-report.md` + `tests/test_bug_001_fixes.py` — Stage 4 (Unit Test Generator).

## 6. Expected result
- `src/calc/` is fixed (percentage `* 100`, empty-average guard, safe AST
  evaluator instead of `eval`).
- Full suite green: `Ran 39 tests ... OK`.
- Artifacts present and consistent: research verified **L4/PASS**, security
  **PASS** (0 CRITICAL/HIGH), tests **PASS** (FIRST-compliant).

## 7. Alternative: run inside Warp (no Claude Code)
If you prefer not to use the `claude` CLI, ask the Warp (Oz) agent to act as the
orchestrator following `agents/orchestrator.agent.md`: it reads each
`*.agent.md` + its skills and performs the four stages in-conversation, producing
the same artifacts. This path runs no `claude` subprocesses.

## 8. Troubleshooting
- **`claude: command not found` / auth errors** — install/authenticate the Claude
  Code CLI, or set `AGENT_CLI=<your-cli>` (must accept `-p`, `--model`,
  `--append-system-prompt`). Use `--dry-run` to validate everything else first.
- **`ModuleNotFoundError: calc`** — you omitted `PYTHONPATH=src`. Prefix every app
  or test command with it, and run from the project root.
- **Pipeline stops at a prerequisite check** — ensure
  `context/bugs/001/research/codebase-research.md` and
  `context/bugs/001/implementation-plan.md` exist.
- **Agents report tests BLOCKED** — in headless `acceptEdits` mode the agents may
  be unable to run `python3`. Verify manually with the section 3 command; the
  applied fixes are independent of that block.

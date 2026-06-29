# Verified Research — Bug 001 (calc sample app)

Reviewed: `context/bugs/001/research/codebase-research.md`
Source of truth: `src/calc/`, `tests/`
Rubric: `skills/research-quality-measurement.md`

## 1. Verification Summary

- **Verdict: PASS**
- **Research Quality: L4 — VERIFIED (100%)**

Every `path:line` reference resolves to the cited location, every quoted snippet
matches the source byte-for-byte (whitespace-immaterial), and every behavioural
assertion is consistent with the source. No fabricated references. Zero
discrepancies. The research is ready for the Bug Planner with no caveats.

Counts:

| Claim type | Total | VERIFIED | PARTIAL | DISCREPANCY | UNVERIFIABLE |
|------------|-------|----------|---------|-------------|--------------|
| References | 12    | 12       | 0       | 0           | 0            |
| Snippets   | 8     | 8        | 0       | 0           | 0            |
| Assertions | 7     | 7        | 0       | 0           | 0            |
| **All**    | 27    | 27       | 0       | 0           | 0            |

Verification note: `unittest` execution was blocked by the sandbox (approval not
granted in this run), so the runtime behaviours (F1 return values, F2
`ZeroDivisionError`, F3 code execution, and the two CLI reproductions) were
confirmed by static inspection of the cited source rather than by running. For
these cases the code is fully deterministic and unambiguous (`25/200 == 0.125`,
`50/50 == 1.0`, `sum([]) / len([])` → `0/0` → `ZeroDivisionError`, and
`eval(expression)` executes arbitrary Python), so inspection is conclusive and
the assertions are marked `VERIFIED`.

## 2. Verified Claims

### References — all `VERIFIED`

| # | Research reference | Checked at | Label |
|---|--------------------|-----------|-------|
| R1 | `operations.py:11` — `def percentage(part: float, whole: float) -> float:` | `src/calc/operations.py:11` | VERIFIED |
| R2 | `operations.py:17` — `return part / whole` | `src/calc/operations.py:17` | VERIFIED |
| R3 | `operations.py:20` — `def average(numbers: Sequence[float]) -> float:` | `src/calc/operations.py:20` | VERIFIED |
| R4 | `operations.py:25` — `return sum(numbers) / len(numbers)` | `src/calc/operations.py:25` | VERIFIED |
| R5 | `evaluator.py:9` — `def evaluate(expression: str) -> float:` | `src/calc/evaluator.py:9` | VERIFIED |
| R6 | `evaluator.py:16` — `return eval(expression)` | `src/calc/evaluator.py:16` | VERIFIED |
| R7 | `operations.py:12-15` — percentage docstring contract | `src/calc/operations.py:12-16` (prose on 12–15, closing `"""` on 16) | VERIFIED |
| R8 | `operations.py:21-23` — average docstring contract ("mean of 0.0") | `src/calc/operations.py:21-24` (target sentence on line 23) | VERIFIED |
| R9 | `context/bugs/001/bug-context.md` — seeded issue catalogue | file present (3396 bytes) | VERIFIED |
| R10 | `tests/test_operations.py::PercentageTests` (2 failing) | `tests/test_operations.py:15-20` (two tests) | VERIFIED |
| R11 | `tests/test_operations.py::AverageTests::test_average_of_empty_is_zero` | `tests/test_operations.py:27-28` | VERIFIED |
| R12 | `tests/test_evaluator.py::EvaluateTests::test_rejects_non_arithmetic_input` | `tests/test_evaluator.py:20-24` | VERIFIED |

### Snippets — all `VERIFIED`

| # | Quoted snippet | Source | Label |
|---|----------------|--------|-------|
| S1 | `return part / whole` | `operations.py:17` (`    return part / whole`) — whitespace-immaterial match | VERIFIED |
| S2 | `return sum(numbers) / len(numbers)` | `operations.py:25` exact | VERIFIED |
| S3 | `return eval(expression)` | `evaluator.py:16` exact | VERIFIED |
| S4 | `def percentage(part: float, whole: float) -> float:` | `operations.py:11` exact | VERIFIED |
| S5 | `def average(numbers: Sequence[float]) -> float:` | `operations.py:20` exact | VERIFIED |
| S6 | `def evaluate(expression: str) -> float:` | `evaluator.py:9` exact | VERIFIED |
| S7 | percentage contract quote ("…`percentage(25, 200)` should be `12.5` … `percentage(50, 50)` should be `100.0`") | `operations.py:12-15` exact | VERIFIED |
| S8 | average contract quote ("An empty sequence has a mean of `0.0`.") | `operations.py:23` exact | VERIFIED |

### Assertions — all `VERIFIED`

| # | Assertion | Basis | Label |
|---|-----------|-------|-------|
| A1 | `percentage(25, 200)` returns `0.125` | `25/200 = 0.125` from `operations.py:17` | VERIFIED |
| A2 | `percentage(50, 50)` returns `1.0` | `50/50 = 1.0` from `operations.py:17` | VERIFIED |
| A3 | Root cause: missing `* 100` factor | `operations.py:17` returns the raw ratio | VERIFIED |
| A4 | `average([])` raises `ZeroDivisionError`; no empty guard | `operations.py:25` is the whole body; `0/0` raises | VERIFIED |
| A5 | `evaluate()` executes arbitrary code (CWE-95) | `evaluator.py:16` uses `eval()` on raw input | VERIFIED |
| A6 | CLI `percent 25 200` → `0.125` | `cli.py:39-40` routes `percent` → `percentage(part, whole)` then prints | VERIFIED |
| A7 | CLI `eval "__import__('os').getcwd()"` prints cwd | `cli.py:37-38` routes `eval` → `evaluate(expression)`; `eval()` executes it | VERIFIED |

## 3. Discrepancies Found

None.

Minor notes (non-blocking, not scored as discrepancies):
- R7 cites the percentage docstring as lines `12-15`; the quoted prose is exactly
  on lines 12–15, with the closing `"""` on line 16. The cited range captures all
  quoted text accurately. No planning impact.
- R8 cites the average docstring as lines `21-23`; the "mean of `0.0`" sentence is
  on line 23 and the docstring closes on line 24. Accurate. No planning impact.

## 4. Research Quality Assessment

- **Level: L4 — VERIFIED (Excellent)**
- **Reference Accuracy Rate (RAR):** `(12 VERIFIED + 0.5 × 0 PARTIAL) / 12 = 1.000`
- **Snippet Fidelity Rate (SFR):** over 8 snippets + 7 assertions = 15 claims,
  `(15 + 0.5 × 0) / 15 = 1.000`
- **Overall Score:** `min(RAR, SFR) × 100 = min(1.000, 1.000) × 100 = 100%`

Counts: total claims 27 — VERIFIED 27 / PARTIAL 0 / DISCREPANCY 0 / UNVERIFIABLE 0.

Reasoning: All 12 references resolve to the exact cited lines; all 8 snippets
match the source (only immaterial leading-whitespace differences); all 7
behavioural assertions follow unambiguously from the cited code. No reference
points to a non-existent file or symbol, so the L1 fabrication cap does not apply.
A 100% Overall Score maps to **L4 — VERIFIED**, which maps to the **PASS** verdict.

**Meaning for the Bug Planner:** The research can be turned into an
implementation plan as-is. The three findings are correctly located and correctly
diagnosed:
- F1 — fix `operations.py:17` to multiply by 100 (`return (part / whole) * 100`),
  honouring the docstring contract.
- F2 — add an empty-sequence guard at `operations.py:25` returning `0.0`.
- F3 — replace `eval()` at `evaluator.py:16` with a safe, arithmetic-only
  evaluator (e.g. AST-based) that rejects non-arithmetic input.
No claim requires re-verification before planning.

## 5. References (evidence inspected by the verifier)

- `src/calc/operations.py:11` — `def percentage(part: float, whole: float) -> float:`
- `src/calc/operations.py:12-16` — percentage docstring (contract: `12.5`, `100.0`)
- `src/calc/operations.py:17` — `    return part / whole`
- `src/calc/operations.py:20` — `def average(numbers: Sequence[float]) -> float:`
- `src/calc/operations.py:21-24` — average docstring (contract: empty → `0.0`)
- `src/calc/operations.py:25` — `    return sum(numbers) / len(numbers)`
- `src/calc/evaluator.py:9` — `def evaluate(expression: str) -> float:`
- `src/calc/evaluator.py:10-14` — docstring ("Only arithmetic … should be rejected")
- `src/calc/evaluator.py:16` — `    return eval(expression)`
- `src/calc/cli.py:37-42` — subcommand routing for `eval` / `percent` / `average`
- `tests/test_operations.py:15-28` — `PercentageTests`, `AverageTests`
- `tests/test_evaluator.py:16-24` — `EvaluateTests` (incl. `test_rejects_non_arithmetic_input`)
- `context/bugs/001/bug-context.md` — present (3396 bytes)

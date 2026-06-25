# Bug Context — Bug 001 (calc sample app)

## Application under test
`calc` is a tiny, dependency-free Python calculator CLI in `src/calc/`. It
exists so the 4-agent pipeline has a concrete app to operate on, with a clear
before/after state.

- Stack: Python 3 standard library only (no third-party dependencies).
- Entry point: `python -m calc <command>` (run with `PYTHONPATH=src`).
- Test command: `PYTHONPATH=src python3 -m unittest discover -s tests -v`
- Source:
  - `src/calc/operations.py` — arithmetic helpers (`percentage`, `average`)
  - `src/calc/evaluator.py` — expression evaluation (`evaluate`)
  - `src/calc/cli.py`, `src/calc/__main__.py` — CLI wiring

## Seeded issues
This app intentionally ships with **2 functional bugs** and **1 security
vulnerability** for the pipeline to find and fix.

### BUG-1 — `percentage()` returns a ratio, not a percentage (logic)
- Location: `src/calc/operations.py:17`
- Function: `percentage(part, whole)`
- Expected: `percentage(25, 200) == 12.5`; `percentage(50, 50) == 100.0`
- Actual: returns `part / whole` (missing `* 100`) → `0.125` and `1.0`
- Reproduce (CLI): `PYTHONPATH=src python3 -m calc percent 25 200` → `0.125`
- Reproduce (test): `tests/test_operations.py::PercentageTests` (2 failing)
- Suggested fix: `return (part / whole) * 100`

### BUG-2 — `average()` crashes on empty input (robustness / edge case)
- Location: `src/calc/operations.py:25`
- Function: `average(numbers)`
- Expected: `average([]) == 0.0`
- Actual: `sum(numbers) / len(numbers)` raises `ZeroDivisionError` for `[]`
- Reproduce (CLI): `PYTHONPATH=src python3 -m calc average` → `ZeroDivisionError`
- Reproduce (test): `tests/test_operations.py::AverageTests::test_average_of_empty_is_zero` (error)
- Suggested fix: guard empty input, e.g. `return sum(numbers) / len(numbers) if numbers else 0.0`

### SEC-1 — `evaluate()` executes arbitrary code via `eval()` (security, CRITICAL)
- Location: `src/calc/evaluator.py:16`
- Function: `evaluate(expression)`
- Class: Code injection / arbitrary code execution (CWE-95; OWASP Injection)
- Impact: any string passed to `evaluate` is executed as Python, e.g.
  `evaluate("__import__('os').system('rm -rf ~')")` runs shell commands.
- Reproduce (CLI): `PYTHONPATH=src python3 -m calc eval "__import__('os').getcwd()"`
  prints the working directory — proving arbitrary code execution (harmless payload).
- Reproduce (test): `tests/test_evaluator.py::EvaluateTests::test_rejects_non_arithmetic_input` (fails — no exception raised)
- Suggested fix: replace `eval` with a safe arithmetic evaluator — parse with
  `ast` and allow only numeric literals and arithmetic operator nodes (or use
  `ast.literal_eval` for literals) so non-arithmetic input is rejected.

## Before / after
- Before (current state): `PYTHONPATH=src python3 -m unittest discover -s tests`
  reports **2 passing, 4 red** (3 failures + 1 error) reproducing BUG-1 (×2),
  BUG-2, and SEC-1.
- After (pipeline fixes applied): all tests pass, `evaluate` rejects
  non-arithmetic input, and the Unit Test Generator adds further tests for the
  changed code.

## Test status snapshot (before)
- PASS: `test_arithmetic_expression`, `test_average_of_values`
- FAIL: `test_part_of_whole`, `test_full_whole_is_100_percent` (BUG-1)
- ERROR: `test_average_of_empty_is_zero` (BUG-2)
- FAIL: `test_rejects_non_arithmetic_input` (SEC-1)

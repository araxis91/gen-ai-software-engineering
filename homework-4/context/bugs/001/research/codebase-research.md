# Codebase Research — Bug 001 (calc sample app)

## Scope
Investigate the reported defects in the `calc` CLI (`src/calc/`) ahead of
planning a fix. Three issues were identified: two functional bugs in
`operations.py` and one security vulnerability in `evaluator.py`.

## Environment
- Language: Python 3 (standard library only).
- Tests: `unittest`, run with
  `PYTHONPATH=src python3 -m unittest discover -s tests -v`.

## Findings

### F1 — `percentage()` returns a ratio instead of a percentage
- Location: `src/calc/operations.py:17`
- Function: `percentage(part, whole)` defined at `src/calc/operations.py:11`.
- Current implementation:
  ```python
      return part / whole
  ```
- Documented contract (`src/calc/operations.py:12-15`): "Return what percentage
  ``part`` is of ``whole``. For example, ``percentage(25, 200)`` should be
  ``12.5`` ... and ``percentage(50, 50)`` should be ``100.0``."
- Observed: `percentage(25, 200)` returns `0.125` and `percentage(50, 50)`
  returns `1.0` — the `* 100` factor is missing.
- Reproduced by: `tests/test_operations.py::PercentageTests` (2 failing) and
  `PYTHONPATH=src python3 -m calc percent 25 200` → `0.125`.

### F2 — `average()` raises ZeroDivisionError on empty input
- Location: `src/calc/operations.py:25`
- Function: `average(numbers)` defined at `src/calc/operations.py:20`.
- Current implementation:
  ```python
      return sum(numbers) / len(numbers)
  ```
- Documented contract (`src/calc/operations.py:21-23`): "An empty sequence has a
  mean of ``0.0``."
- Observed: `average([])` raises `ZeroDivisionError: division by zero` because
  `len(numbers)` is `0` and there is no empty guard.
- Reproduced by:
  `tests/test_operations.py::AverageTests::test_average_of_empty_is_zero` (error).

### F3 — `evaluate()` executes arbitrary code via `eval()` [SECURITY]
- Location: `src/calc/evaluator.py:16`
- Function: `evaluate(expression)` defined at `src/calc/evaluator.py:9`.
- Current implementation:
  ```python
      return eval(expression)
  ```
- Class: Code injection / arbitrary code execution (CWE-95).
- Observed: any Python is executed, e.g.
  `PYTHONPATH=src python3 -m calc eval "__import__('os').getcwd()"` prints the
  working directory. The function is documented to support arithmetic only.
- Reproduced by:
  `tests/test_evaluator.py::EvaluateTests::test_rejects_non_arithmetic_input`
  (failing — no exception raised).

## References
- `src/calc/operations.py:11` — `def percentage(part: float, whole: float) -> float:`
- `src/calc/operations.py:17` — `    return part / whole`
- `src/calc/operations.py:20` — `def average(numbers: Sequence[float]) -> float:`
- `src/calc/operations.py:25` — `    return sum(numbers) / len(numbers)`
- `src/calc/evaluator.py:9` — `def evaluate(expression: str) -> float:`
- `src/calc/evaluator.py:16` — `    return eval(expression)`
- `context/bugs/001/bug-context.md` — seeded issue catalogue.

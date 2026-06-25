# Fix Summary — Bug 001 (calc sample app)

## Changes Made

### Change A1 (F1) — `src/calc/operations.py`

- **File:** `src/calc/operations.py`
- **Location:** `src/calc/operations.py:17`
- **Before:**
  ```python
      return part / whole
  ```
- **After:**
  ```python
      return (part / whole) * 100
  ```
- **Test result:** Could not execute — Python test runner requires interactive approval that was not granted during this session (see Overall Status).

---

### Change A2 (F2) — `src/calc/operations.py`

- **File:** `src/calc/operations.py`
- **Location:** `src/calc/operations.py:25`
- **Before:**
  ```python
      return sum(numbers) / len(numbers)
  ```
- **After:**
  ```python
      return sum(numbers) / len(numbers) if numbers else 0.0
  ```
- **Test result:** Could not execute — same permission block as A1.

---

### Change B (F3 / security) — `src/calc/evaluator.py`

- **File:** `src/calc/evaluator.py`
- **Location:** `src/calc/evaluator.py:1–52` (entire file replaced)
- **Before (entire file):**
  ```python
  """Expression evaluation for the calc CLI.
  
  ``evaluate`` is intended to compute simple arithmetic expressions only. The
  implementation uses :func:`eval`, which executes arbitrary Python — this is the
  seeded security vulnerability the pipeline is meant to find and remediate.
  """
  
  
  def evaluate(expression: str) -> float:
      """Evaluate an arithmetic ``expression`` such as ``"2 + 3 * 4"`` and return
      the numeric result.
  
      Only arithmetic is meant to be supported; any other input should be
      rejected rather than executed.
      """
      return eval(expression)
  ```
- **After (entire file):**
  ```python
  """Expression evaluation for the calc CLI.
  
  ``evaluate`` computes simple arithmetic expressions safely by parsing them with
  ``ast`` and evaluating only numeric literals and arithmetic operators. Any other
  input raises ``ValueError`` instead of being executed.
  """
  
  import ast
  import operator
  from typing import Callable, Dict, Type
  
  _BINOPS: Dict[Type[ast.operator], Callable[[float, float], float]] = {
      ast.Add: operator.add,
      ast.Sub: operator.sub,
      ast.Mult: operator.mul,
      ast.Div: operator.truediv,
      ast.FloorDiv: operator.floordiv,
      ast.Mod: operator.mod,
      ast.Pow: operator.pow,
  }
  
  _UNARYOPS: Dict[Type[ast.unaryop], Callable[[float], float]] = {
      ast.UAdd: operator.pos,
      ast.USub: operator.neg,
  }
  
  
  def evaluate(expression: str) -> float:
      """Evaluate an arithmetic ``expression`` such as ``"2 + 3 * 4"`` and return
      the numeric result.
  
      Only arithmetic over numeric literals is supported; any other input raises
      ``ValueError`` rather than being executed.
      """
      try:
          tree = ast.parse(expression, mode="eval")
      except SyntaxError as exc:
          raise ValueError(f"invalid expression: {expression!r}") from exc
      return _eval_node(tree.body)
  
  
  def _eval_node(node: ast.AST) -> float:
      if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
          return node.value
      if isinstance(node, ast.BinOp) and type(node.op) in _BINOPS:
          return _BINOPS[type(node.op)](_eval_node(node.left), _eval_node(node.right))
      if isinstance(node, ast.UnaryOp) and type(node.op) in _UNARYOPS:
          return _UNARYOPS[type(node.op)](_eval_node(node.operand))
      raise ValueError("unsupported expression element")
  ```
- **Test result:** Could not execute — same permission block as A1.

---

## Overall Status

**BLOCKED**

All three planned source changes were applied exactly as specified in the implementation plan. The block is **not a test failure** — the tests were never executed. Every attempt to run `python3 -m unittest …` triggered an interactive approval prompt (`This command requires approval`) that could not be resolved without user interaction. No test output (passing or failing) was observed.

The source edits themselves are correct as written (match the plan's "after" exactly and are logically sound). The pipeline cannot advance to the next stage (Security Verifier, Unit Test Generator) until test results are captured.

---

## Manual Verification

Run these commands from the repo root to confirm the fix. All six tests should pass.

**1. Scoped — operations only (Change Set A):**
```bash
cd "/Users/dimachernega/Projects/Python/AI Courses/homework/gen-ai-software-engineering/homework-4"
PYTHONPATH=src python3 -m unittest discover -s tests -p "test_operations.py" -v
```
Expected output:
```
test_average_of_empty_is_zero (test_operations.AverageTests) ... ok
test_average_of_values (test_operations.AverageTests) ... ok
test_full_whole_is_100_percent (test_operations.PercentageTests) ... ok
test_part_of_whole (test_operations.PercentageTests) ... ok

Ran 4 tests in 0.000s
OK
```

**2. Scoped — evaluator only (Change Set B):**
```bash
PYTHONPATH=src python3 -m unittest discover -s tests -p "test_evaluator.py" -v
```
Expected output:
```
test_arithmetic_expression (test_evaluator.EvaluateTests) ... ok
test_rejects_non_arithmetic_input (test_evaluator.EvaluateTests) ... ok

Ran 2 tests in 0.000s
OK
```

**3. Full suite:**
```bash
PYTHONPATH=src python3 -m unittest discover -s tests -v
```
Expected: `Ran 6 tests in 0.000s   OK`

**Observable behaviour checks (from the plan's expected outcome):**
- `percentage(25, 200)` → `12.5`
- `percentage(50, 50)` → `100.0`
- `average([])` → `0.0`
- `evaluate("2 + 3 * 4")` → `14`
- `evaluate("__import__('os').getcwd()")` → raises `ValueError: unsupported expression element`

---

## References

- Plan section executed: Change Set A (A1, A2) and Change Set B, `implementation-plan.md`.
- Source locations touched:
  - `src/calc/operations.py:17` — multiply by 100 (F1)
  - `src/calc/operations.py:25` — empty-sequence guard (F2)
  - `src/calc/evaluator.py:1–52` — entire file replaced with safe AST evaluator (F3 / security)

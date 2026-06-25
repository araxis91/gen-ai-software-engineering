# Implementation Plan — Bug 001 (calc sample app)

## Objective
Resolve the three issues found in research (F1, F2, F3) so the documented
behaviour holds and untrusted input is no longer executed. Derived from
`context/bugs/001/research/codebase-research.md`.

## Test command
Full suite (run at the end; expected all green):

    PYTHONPATH=src python3 -m unittest discover -s tests -v

Note: the suite starts with pre-existing failures from issues not yet fixed.
Apply the change-sets below in order; after each change-set run its scoped test
command and confirm it is green before continuing. Stop only if a change
introduces a NEW failure.

## Change Set A — `src/calc/operations.py` (fixes F1 and F2)

### A1 (F1) — multiply by 100 (line 17)
Before:
```python
    return part / whole
```
After:
```python
    return (part / whole) * 100
```

### A2 (F2) — handle empty input (line 25)
Before:
```python
    return sum(numbers) / len(numbers)
```
After:
```python
    return sum(numbers) / len(numbers) if numbers else 0.0
```

Scoped verification (expect all green):

    PYTHONPATH=src python3 -m unittest discover -s tests -p "test_operations.py" -v

## Change Set B — `src/calc/evaluator.py` (fixes F3, security)
Replace the unsafe `eval`-based implementation with a safe arithmetic evaluator
that parses the expression with `ast` and permits only numeric literals and
arithmetic operators; anything else raises `ValueError`.

Before (entire file):
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
After (entire file):
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

Scoped verification (expect all green):

    PYTHONPATH=src python3 -m unittest discover -s tests -p "test_evaluator.py" -v

## Final verification
Run the full suite; expected: all tests pass.

    PYTHONPATH=src python3 -m unittest discover -s tests -v

## Expected outcome
- F1: `percent 25 200` → `12.5`; `percent 50 50` → `100.0`.
- F2: `average` of `[]` → `0.0`.
- F3: `eval "__import__('os').getcwd()"` raises `ValueError`; `eval "2 + 3 * 4"` → `14`.

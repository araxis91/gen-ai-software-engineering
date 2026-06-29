# Security Report — Bug 001 (calc sample app)

## 1. Security Summary

**Overall verdict: PASS (with one hardening note).**

The headline change — replacing `eval()` with an `ast`-based arithmetic
evaluator — correctly closes the seeded **arbitrary code execution (RCE)**
vulnerability. Attacker-controlled CLI input can no longer reach a code-execution
sink: only numeric literals and a fixed allow-list of arithmetic operators are
evaluated; everything else raises `ValueError`. No name resolution, attribute
access, function calls, or comprehensions are reachable.

One residual weakness remains: the operator allow-list includes `**` (`ast.Pow`),
which permits an unbounded-cost exponentiation denial-of-service. It is low
real-world impact for a local single-shot CLI, so it is filed as a LOW finding,
not a merge blocker.

Findings by severity:

| Severity | Count |
|----------|-------|
| CRITICAL | 0 |
| HIGH     | 0 |
| MEDIUM   | 0 |
| LOW      | 1 |
| INFO     | 2 |

## 2. Scope

Reviewed exactly the locations listed in `fix-summary.md` → Changes Made, plus the
immediate caller (`cli.py`) to judge data flow from attacker-controlled input.

- `src/calc/operations.py:17` — Change A1 (F1): `(part / whole) * 100`
- `src/calc/operations.py:25` — Change A2 (F2): empty-sequence guard
- `src/calc/evaluator.py:1–49` — Change B (F3 / security): entire file replaced
  with AST evaluator
- `src/calc/cli.py:13,37–38` (context only) — confirms `evaluate()` receives the
  raw `eval` subcommand argument (attacker-controlled).

No dependency manifest exists in the repo (no `requirements.txt`,
`pyproject.toml`, `setup.py`, or `package.json`); the change adds only
standard-library imports (`ast`, `operator`, `typing`), so no dependency surface
changed.

## 3. Vulnerability Classes Reviewed

| Class | Considered | Applies | Notes |
|-------|-----------|---------|-------|
| Injection — code/`eval` (RCE) | Yes | Resolved | `eval()` removed; replaced by AST allow-list. This was the seeded vuln; now closed. See INFO-001. |
| Injection — SQL | Yes | Not applicable | No database access anywhere in scope. |
| Injection — OS command | Yes | Not applicable | No `os.system`/`subprocess`/shell invocation. |
| Injection — path traversal | Yes | Not applicable | No filesystem path handling in scope. |
| Hardcoded secrets / credentials | Yes | Not applicable | No secrets, tokens, keys, or passwords introduced. |
| Insecure comparisons (non-constant-time / `==` on secrets) | Yes | Not applicable | Comparisons are `isinstance`/`type(...) in dict` for AST dispatch; no secret/token comparison. |
| Missing / improper input validation | Yes | Reviewed | Evaluator now strictly validates (allow-list); unhandled `ZeroDivisionError`/`OverflowError` are robustness, not security — see INFO-002. |
| Unsafe / known-vulnerable dependencies | Yes | Not applicable | Only stdlib added; no third-party deps. |
| Resource exhaustion / DoS | Yes | Applies | `ast.Pow` allows unbounded exponentiation cost — see SEC-001. |
| XSS / CSRF | Yes | Not applicable | CLI app; no web/HTML/HTTP response context. |

## 4. Findings

### SEC-001 — Unbounded exponentiation allows CPU/memory denial of service

- **Severity:** LOW
- **Category:** Resource exhaustion / DoS (improper input validation on
  computation cost)
- **Location:** `src/calc/evaluator.py:19` (registration of `ast.Pow`), exploited
  via `src/calc/evaluator.py:45–46` (`BinOp` dispatch)
- **Description:** The operator allow-list maps `ast.Pow` (`**`) to
  `operator.pow`. Because operands can themselves be expressions, an attacker can
  chain exponentiation to force an enormous integer computation, e.g.
  `calc eval "9**9**9**9"`. Python evaluates this right-associatively into an
  integer with astronomically many digits, consuming CPU and memory until the
  process hangs or is OOM-killed. Unlike the RCE that was fixed, this does not
  execute arbitrary code, and for a local, single-invocation CLI the blast radius
  is limited to the user's own process — hence LOW. The risk would rise to
  MEDIUM/HIGH if `evaluate()` were ever exposed over a service or used on
  untrusted batch input.
- **Evidence:**
  ```python
  _BINOPS: Dict[Type[ast.operator], Callable[[float, float], float]] = {
      ...
      ast.Pow: operator.pow,   # evaluator.py:19 — unbounded cost
  }
  # evaluator.py:45–46
  if isinstance(node, ast.BinOp) and type(node.op) in _BINOPS:
      return _BINOPS[type(node.op)](_eval_node(node.left), _eval_node(node.right))
  ```
- **Remediation:** If exponentiation is not a required feature, drop `ast.Pow`
  from `_BINOPS` (other findings of this class then disappear). If it must stay,
  bound it — e.g. reject when the exponent or base magnitude exceeds a small
  threshold before computing:
  ```python
  def _pow(base: float, exp: float) -> float:
      if abs(exp) > 100 or abs(base) > 10**6:
          raise ValueError("exponent/base out of allowed range")
      return operator.pow(base, exp)
  ```
  Optionally also reject overly long input expressions and limit recursion depth
  for defense in depth.

### INFO-001 — `eval()` RCE correctly remediated (observation)

- **Severity:** INFO
- **Category:** Injection — code/`eval` (RCE)
- **Location:** `src/calc/evaluator.py:28–49`
- **Description:** The previous implementation called `eval(expression)` on the
  raw CLI argument, allowing payloads such as
  `calc eval "__import__('os').system('id')"`. The replacement parses with
  `ast.parse(..., mode="eval")` and walks the tree, accepting only numeric
  `Constant` nodes and a fixed set of binary/unary arithmetic operators; any other
  node (Name, Call, Attribute, Subscript, comprehension, etc.) hits the terminal
  `raise ValueError("unsupported expression element")`. No code-execution sink
  remains reachable. Recorded as positive confirmation, not a defect.
- **Evidence:**
  ```python
  if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
      return node.value
  ...
  raise ValueError("unsupported expression element")
  ```
- **Remediation:** None required. (Note: a `bool` would pass the `int` check since
  `bool` subclasses `int`, but a bare `True`/`False` literal parses to a `Name`,
  not a numeric `Constant`, so it is rejected upstream — no action needed.)

### INFO-002 — Unhandled arithmetic exceptions (robustness, not security)

- **Severity:** INFO
- **Category:** Missing / improper input validation
- **Location:** `src/calc/evaluator.py:45–48`; `src/calc/operations.py:17`
- **Description:** `calc eval "1/0"` raises an uncaught `ZeroDivisionError`, and
  `calc percent 5 0` likewise raises `ZeroDivisionError` from
  `(part / whole) * 100`. A very large `**` can raise `OverflowError`. These crash
  the process with a stack trace rather than a clean error message. This is a
  robustness/UX concern, not an exploitable security vulnerability (no
  state corruption, no info disclosure beyond a local traceback), so it is INFO.
- **Evidence:**
  ```python
  return (part / whole) * 100   # operations.py:17 — whole == 0 → ZeroDivisionError
  ```
- **Remediation:** Optionally catch `ZeroDivisionError`/`OverflowError` in
  `evaluate()` (and guard `whole == 0` in `percentage`) and re-raise as a
  `ValueError` with a friendly message, consistent with the existing
  `SyntaxError → ValueError` handling.

## 5. References

Locations inspected to reach the above conclusions:

- `context/bugs/001/fix-summary.md` — Changes Made (review scope).
- `src/calc/evaluator.py:1–49` — replaced AST evaluator (primary security change).
- `src/calc/operations.py:11–25` — `percentage` (A1) and `average` (A2) changes.
- `src/calc/cli.py:13,21–22,37–38` — confirms the `eval` subcommand passes
  attacker-controlled input directly to `evaluate()`.
- Repository root / `src/calc/` — confirmed no dependency manifest exists; change
  introduces only Python standard-library imports.

> Reviewer note: tests were not executed (the Bug Fixer recorded a BLOCKED status
> due to an interactive-approval prompt). This security assessment is based on
> static review of the changed source. The DoS reasoning in SEC-001 can be
> confirmed dynamically with `time python -m calc eval "9**9**9**9"` (expect a
> hang / high memory), and the RCE closure with
> `python -m calc eval "__import__('os').system('id')"` (expect `ValueError`).

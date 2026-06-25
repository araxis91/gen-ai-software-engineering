# calc — sample mini application

A tiny, dependency-free Python calculator CLI. It is the sample app the 4-agent
bug-fix pipeline operates on, so the pipeline produces concrete, demonstrable
before/after results.

## Stack
- Python 3 standard library only — no third-party dependencies.

## Layout
- `calc/operations.py` — `percentage`, `average`
- `calc/evaluator.py` — `evaluate`
- `calc/cli.py`, `calc/__main__.py` — CLI wiring

## Run
From the `homework-4/` project root:

    PYTHONPATH=src python3 -m calc eval "2 + 3 * 4"   # arithmetic -> 14
    PYTHONPATH=src python3 -m calc percent 25 200      # percentage
    PYTHONPATH=src python3 -m calc average 2 4 6        # mean -> 4.0

## Test
From the `homework-4/` project root:

    PYTHONPATH=src python3 -m unittest discover -s tests -v

(`pytest tests` also works if pytest is installed: `PYTHONPATH=src pytest tests`.)

## Seeded issues (before the pipeline runs)
This app intentionally contains 2 bugs and 1 security issue, documented in
`context/bugs/001/bug-context.md`:

- BUG-1 — `percentage()` returns a ratio instead of a percentage
  (`percent 25 200` prints `0.125` instead of `12.5`).
- BUG-2 — `average([])` raises `ZeroDivisionError` instead of returning `0.0`.
- SEC-1 (CRITICAL) — `evaluate()` uses `eval()`, allowing arbitrary code
  execution.

Before the fix, the suite reports **2 passing and 4 red** (3 failures + 1
error). After the pipeline applies its fixes, all tests pass.

---
name: unit-tests-FIRST
description: >-
  The FIRST principles for high-quality unit tests — Fast, Independent,
  Repeatable, Self-validating, Timely. Use this skill when generating or reviewing
  unit tests for changed code and when writing the FIRST Compliance section of a
  test-report.md. Defines each principle with concrete do/don't guidance, a
  per-test checklist, and how to report compliance.
version: 1.0.0
applies_to:
  - agents/unit-test-generator.agent.md
outputs:
  - test-report.md (FIRST Compliance section)
---

# FIRST Unit Test Principles

**FIRST** = **F**ast, **I**ndependent, **R**epeatable, **S**elf-validating,
**T**imely. A proper unit test satisfies *all five*. Apply this skill when
generating tests for changed code, and treat a test as acceptable only when every
principle holds.

## When to use

- Generating unit tests for new/changed code.
- Deciding whether a candidate test is a real unit test (vs. an integration or
  flaky test).
- Writing the **FIRST Compliance** section of `test-report.md`.

## The five principles

### F — Fast
- **Definition**: each test runs in milliseconds so the whole suite runs in
  seconds and is run often.
- **Do**: test pure logic in isolation; replace slow collaborators (DB, network,
  filesystem, real time) with mocks/stubs/in-memory fakes.
- **Don't**: hit a real network/DB/disk, call `sleep`, spin up servers, or loop
  over huge inputs.
- **Check**: a single test is roughly < 100 ms; the suite for the changed code is
  near-instant.

### I — Independent
- **Definition**: tests do not depend on each other or on execution order; any one
  can run alone or in parallel.
- **Do**: build fresh fixtures per test (setup/teardown or per-test construction);
  isolate state.
- **Don't**: rely on a global/shared mutable state, on side effects from a prior
  test, or on a specific run order.
- **Check**: running tests individually, in random order, or in parallel gives the
  same results.

### R — Repeatable
- **Definition**: deterministic — same result on every run, on any machine, at any
  time.
- **Do**: control non-determinism — inject the clock (fixed time), seed randomness,
  pin timezone/locale, mock external services.
- **Don't**: depend on the real wall-clock/date, unseeded RNG, live services, or
  environment-specific paths.
- **Check**: repeated runs and runs in a different environment produce identical
  outcomes.

### S — Self-validating
- **Definition**: the test asserts its own pass/fail automatically; no human reads
  output to judge it.
- **Do**: use explicit assertions on the return value, resulting state, or raised
  exception; one clear behaviour per test; descriptive test names.
- **Don't**: print-and-eyeball, write tests with no assertions, or assert something
  trivially true.
- **Check**: a wrong implementation makes the test fail (it actually pins
  behaviour).

### T — Timely
- **Definition**: tests are written with — or immediately after — the code they
  cover; here, right after the fix, for the new/changed behaviour.
- **Do**: cover the changed behaviour: a regression test for the bug that was
  fixed, the happy path, edge cases, and failure modes.
- **Don't**: defer testing, or spend effort testing unchanged code while leaving
  the fix uncovered.
- **Check**: the changed code paths are covered and a regression test would have
  caught the original bug.

## Per-test checklist

A test is FIRST-compliant only if every box is true:

- [ ] **Fast** — no real I/O, network, disk, or sleeps; runs in ~ms.
- [ ] **Independent** — no shared mutable state or order dependency; fresh fixtures.
- [ ] **Repeatable** — deterministic; clock/RNG/locale/externals controlled.
- [ ] **Self-validating** — explicit assertions; automatic pass/fail.
- [ ] **Timely** — targets changed code; includes a regression case for the bug.

## Reporting FIRST compliance

In `test-report.md`, the **FIRST Compliance** section must state, for the generated
suite, how each of F/I/R/S/T is satisfied (one line each), and list any deviation
with an explicit justification and the mitigation. Declare the suite
**FIRST-compliant** only when all five principles hold for every generated test;
otherwise mark it **partial** and explain.

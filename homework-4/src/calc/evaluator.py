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

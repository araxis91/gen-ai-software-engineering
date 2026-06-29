"""Pure arithmetic helpers for the calc CLI.

The docstrings describe the intended (correct) behaviour. The implementations
below intentionally diverge from that contract — those divergences are the
seeded bugs the pipeline is meant to find and fix.
"""

from typing import Sequence


def percentage(part: float, whole: float) -> float:
    """Return what percentage ``part`` is of ``whole``.

    For example, ``percentage(25, 200)`` should be ``12.5`` (25 is 12.5% of
    200), and ``percentage(50, 50)`` should be ``100.0``.
    """
    return (part / whole) * 100


def average(numbers: Sequence[float]) -> float:
    """Return the arithmetic mean of ``numbers``.

    An empty sequence has a mean of ``0.0``.
    """
    return sum(numbers) / len(numbers) if numbers else 0.0

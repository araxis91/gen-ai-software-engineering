"""Tests for calc.evaluator.

``test_arithmetic_expression`` passes both before and after the fix. The
``test_rejects_non_arithmetic_input`` test currently fails because ``eval``
executes the input (the seeded security issue); it passes once ``evaluate`` is
replaced with a safe arithmetic evaluator.

Run with:  PYTHONPATH=src python3 -m unittest discover -s tests -v
"""

import unittest

from calc.evaluator import evaluate


class EvaluateTests(unittest.TestCase):
    def test_arithmetic_expression(self):
        self.assertEqual(evaluate("2 + 3 * 4"), 14)

    def test_rejects_non_arithmetic_input(self):
        # A safe evaluator must not execute arbitrary Python. The payload below
        # is harmless (reads the working directory) but proves code execution.
        with self.assertRaises(Exception):
            evaluate("__import__('os').getcwd()")


if __name__ == "__main__":
    unittest.main()

"""Tests for Bug 001 fixes: percentage, average, and evaluate.

Covers the three changes from fix-summary.md:
  A1 — percentage() now multiplies by 100
  A2 — average() returns 0.0 for empty sequences (no ZeroDivisionError)
  B  — evaluate() uses a safe AST parser instead of eval()

Run with:  PYTHONPATH=src python3 -m unittest discover -s tests -v
"""

import unittest

from calc.operations import average, percentage
from calc.evaluator import evaluate


# ---------------------------------------------------------------------------
# A1 — percentage: was missing "* 100"
# ---------------------------------------------------------------------------

class PercentageFixTests(unittest.TestCase):

    # regression — old code returned part/whole (e.g. 0.25), not percent (25.0)
    def test_regression_multiply_by_100(self):
        result = percentage(1, 4)
        self.assertEqual(result, 25.0)
        self.assertNotAlmostEqual(result, 0.25)

    def test_typical_fraction(self):
        self.assertEqual(percentage(25, 200), 12.5)

    def test_full_whole_is_100(self):
        self.assertEqual(percentage(50, 50), 100.0)

    def test_zero_part_yields_zero(self):
        self.assertEqual(percentage(0, 100), 0.0)

    def test_part_exceeds_whole_over_100(self):
        self.assertEqual(percentage(150, 100), 150.0)

    def test_float_inputs(self):
        self.assertAlmostEqual(percentage(1.5, 6.0), 25.0)

    def test_zero_whole_raises(self):
        with self.assertRaises(ZeroDivisionError):
            percentage(10, 0)


# ---------------------------------------------------------------------------
# A2 — average: was raising ZeroDivisionError on empty sequence
# ---------------------------------------------------------------------------

class AverageFixTests(unittest.TestCase):

    # regression — old code raised ZeroDivisionError; fixed code returns 0.0
    def test_regression_empty_returns_zero_not_error(self):
        self.assertEqual(average([]), 0.0)

    def test_single_element(self):
        self.assertEqual(average([7.0]), 7.0)

    def test_standard_integers(self):
        self.assertEqual(average([2, 4, 6]), 4.0)

    def test_negative_values(self):
        self.assertAlmostEqual(average([-3, -1, -2]), -2.0)

    def test_float_values(self):
        self.assertAlmostEqual(average([1.5, 2.5]), 2.0)

    def test_tuple_input(self):
        self.assertEqual(average((10, 20, 30)), 20.0)


# ---------------------------------------------------------------------------
# B — evaluate: old code used eval(); new code uses a safe AST evaluator
# ---------------------------------------------------------------------------

class EvaluateSafetyRegressionTests(unittest.TestCase):
    """Regression: the old eval()-based implementation would execute these."""

    def test_regression_import_attack_raises(self):
        with self.assertRaises(ValueError):
            evaluate("__import__('os').getcwd()")

    def test_regression_exec_attack_raises(self):
        with self.assertRaises(ValueError):
            evaluate("exec('import os')")

    def test_regression_open_attack_raises(self):
        with self.assertRaises(ValueError):
            evaluate("open('/etc/passwd').read()")


class EvaluateArithmeticTests(unittest.TestCase):

    def test_addition(self):
        self.assertEqual(evaluate("1 + 2"), 3)

    def test_subtraction(self):
        self.assertEqual(evaluate("10 - 3"), 7)

    def test_multiplication(self):
        self.assertEqual(evaluate("4 * 5"), 20)

    def test_division(self):
        self.assertAlmostEqual(evaluate("10 / 4"), 2.5)

    def test_floor_division(self):
        self.assertEqual(evaluate("10 // 3"), 3)

    def test_modulo(self):
        self.assertEqual(evaluate("10 % 3"), 1)

    def test_power(self):
        self.assertEqual(evaluate("2 ** 10"), 1024)

    def test_order_of_operations(self):
        self.assertEqual(evaluate("2 + 3 * 4"), 14)

    def test_parentheses_override_precedence(self):
        self.assertEqual(evaluate("(2 + 3) * 4"), 20)

    def test_float_literal(self):
        self.assertAlmostEqual(evaluate("1.5 + 2.5"), 4.0)

    def test_unary_minus(self):
        self.assertEqual(evaluate("-5"), -5)

    def test_unary_plus(self):
        self.assertEqual(evaluate("+3"), 3)


class EvaluateRejectionTests(unittest.TestCase):

    def test_invalid_syntax_raises_value_error(self):
        with self.assertRaises(ValueError):
            evaluate("2 +* 3")

    def test_string_literal_raises_value_error(self):
        with self.assertRaises(ValueError):
            evaluate("'hello'")

    def test_list_expression_raises_value_error(self):
        with self.assertRaises(ValueError):
            evaluate("[1, 2]")

    def test_name_reference_raises_value_error(self):
        with self.assertRaises(ValueError):
            evaluate("x + 1")

    def test_division_by_zero_raises(self):
        with self.assertRaises(ZeroDivisionError):
            evaluate("1 / 0")


if __name__ == "__main__":
    unittest.main()

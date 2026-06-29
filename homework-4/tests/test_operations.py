"""Tests for calc.operations.

These tests encode the intended behaviour from the docstrings. Before the
pipeline applies its fixes, the percentage tests and the empty-average test fail
(reproducing the seeded bugs); they pass once the bugs are fixed.

Run with:  PYTHONPATH=src python3 -m unittest discover -s tests -v
"""

import unittest

from calc.operations import average, percentage


class PercentageTests(unittest.TestCase):
    def test_part_of_whole(self):
        self.assertEqual(percentage(25, 200), 12.5)

    def test_full_whole_is_100_percent(self):
        self.assertEqual(percentage(50, 50), 100.0)


class AverageTests(unittest.TestCase):
    def test_average_of_values(self):
        self.assertEqual(average([2, 4, 6]), 4.0)

    def test_average_of_empty_is_zero(self):
        self.assertEqual(average([]), 0.0)


if __name__ == "__main__":
    unittest.main()

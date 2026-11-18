# -*- coding: utf-8 -*-
"""
Tests for validate_preset_books.compare_book and size_ok: the per-book
comparison of bundled words.json membership against the official syllabus
reference lists, and the tolerance check against the printed syllabus sizes.
"""

import sys
import os
import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from validate_preset_books import compare_book, size_ok, SIZE_TOLERANCE  # noqa: E402


class TestCompareBook:
    def test_exact_match_reports_nothing_missing_or_extra(self):
        report = compare_book(["apple", "core"], ["core", "apple"])
        assert report["missing"] == []
        assert report["extra"] == []
        assert report["ok"] is True

    def test_missing_word_reported(self):
        report = compare_book(["apple", "core", "zeal"], ["apple", "core"])
        assert report["missing"] == ["zeal"]
        assert report["ok"] is False

    def test_extra_word_reported(self):
        report = compare_book(["apple"], ["apple", "android"])
        assert report["extra"] == ["android"]
        assert report["ok"] is False

    def test_comparison_is_set_based(self):
        # Duplicate book entries do not read as extra words...
        report = compare_book(["apple", "core"], ["apple", "apple", "core"])
        assert report["extra"] == []
        # ...but they are flagged separately by the caller via book count.
        assert report["book_count"] == 3
        assert report["ref_count"] == 2

    def test_counts_reported(self):
        report = compare_book(["a", "b", "c"], ["a", "b", "c", "d"])
        assert report["ref_count"] == 3
        assert report["book_count"] == 4


class TestSizeOk:
    def test_within_tolerance(self):
        assert size_ok(4035, 4155) is True   # -2.9%
        assert size_ok(5298, 5418) is True   # -2.2%
        assert size_ok(5658, 5500) is True   # +2.9%

    def test_outside_tolerance(self):
        assert size_ok(3000, 4155) is False
        assert size_ok(7000, 5500) is False

    def test_exact_size_passes(self):
        assert size_ok(4155, 4155) is True

    def test_tolerance_is_five_percent(self):
        assert SIZE_TOLERANCE == 0.05


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))

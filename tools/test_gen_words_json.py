# -*- coding: utf-8 -*-
"""
Tests for gen_words_json.build_book: preset book membership comes from the
official syllabus reference lists; dictionary content is attached from the
bundled dictionary.json by exact word match. Words without a dictionary entry
keep their slot with phonetic null and no senses.
"""

import sys
import os
import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_words_json import build_book  # noqa: E402


DICT = {
    "apple": {"phonetic": "/ˈæpl/", "senses": [("n.", "苹果")]},
    "core": {"phonetic": "/kɔː/", "senses": [("n.", "核心"), ("v.", "挖去果心")]},
    "Africa": {"phonetic": "/ˈæfrikə/", "senses": [("n.", "非洲")]},
    "bare": {"phonetic": None, "senses": [("n.", "光秃的")]},
}


def test_membership_taken_from_reference_verbatim():
    book = build_book("CET-4", ["core", "apple"], DICT)
    assert [w["text"] for w in book["words"]] == ["apple", "core"]


def test_reference_duplicates_collapse():
    book = build_book("CET-4", ["apple", "apple", "core"], DICT)
    assert [w["text"] for w in book["words"]] == ["apple", "core"]


def test_content_attached_by_exact_match():
    book = build_book("CET-4", ["core"], DICT)
    entry = book["words"][0]
    assert entry["phonetic"] == "/kɔː/"
    assert entry["senses"] == [
        {"pos": "n.", "meaning": "核心"},
        {"pos": "v.", "meaning": "挖去果心"},
    ]


def test_word_without_dictionary_entry_keeps_slot():
    book = build_book("CET-4", ["x-ray", "apple"], DICT)
    entry = book["words"][1]
    assert entry == {"text": "x-ray", "phonetic": None, "senses": []}


def test_null_phonetic_stays_null():
    book = build_book("CET-4", ["bare"], DICT)
    assert book["words"][0]["phonetic"] is None


def test_case_not_folded():
    # The dictionary holds "Africa"; a reference word "africa" must not
    # silently take the capitalized entry's content.
    book = build_book("CET-4", ["africa"], DICT)
    assert book["words"][0]["senses"] == []


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))

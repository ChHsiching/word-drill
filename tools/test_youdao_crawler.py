# -*- coding: utf-8 -*-
"""
Tests for youdao_crawler's pure helpers: seed loading, checkpoint/resume, and
raw-response trimming. Network fetch and the run loop are exercised live
against the real API during pipeline verification, not here.
"""

import json
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from youdao_crawler import (  # noqa: E402
    append_checkpoint,
    append_raw,
    load_checkpoint,
    load_raw,
    load_seed_words,
    pending_words,
    trim_response,
)

# The ec subtree of a real response for "handover" (2026-11, trimmed by hand).
HANDOVER_RAW = {
    "input": "handover",
    "ec": {
        "word": [{
            "usphone": "ˈhændoʊvər",
            "ukphone": "ˈhændəʊvə(r)",
            "trs": [{"tr": [{"l": {"i": ["n. 移交，交接；（电信）切换，越区切换"]}}]}],
        }],
    },
    "blng_sents_part": {"sentence-pair": []},  # subtree we do not keep
}

MISS_RAW = {"input": "zzzqqqxy", "lang": "eng", "le": "eng", "meta": {}}


def write_dictionary_asset(path, words):
    entries = [{"word": w, "phonetic": "/x/", "pos": "n.", "meaning": "x"} for w in words]
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"source": "test", "words": entries}, f, ensure_ascii=False)


class TestSeedLoading:
    def test_loads_unique_words_from_bundled_shape(self, tmp_path):
        asset = tmp_path / "dictionary.json"
        write_dictionary_asset(asset, ["the", "be", "the", "of"])
        assert load_seed_words(str(asset)) == ["be", "of", "the"]

    def test_extra_words_merge_and_dedupe(self, tmp_path):
        asset = tmp_path / "dictionary.json"
        write_dictionary_asset(asset, ["the", "be"])
        extra = tmp_path / "extra.txt"
        extra.write_text("be\ncoffee\n", encoding="utf-8")
        assert load_seed_words(str(asset), str(extra)) == ["be", "coffee", "the"]

    def test_extra_words_file_optional(self, tmp_path):
        asset = tmp_path / "dictionary.json"
        write_dictionary_asset(asset, ["the"])
        assert load_seed_words(str(asset)) == ["the"]


class TestCheckpointResume:
    def test_checkpoint_roundtrip(self, tmp_path):
        cp = tmp_path / "checkpoint.jsonl"
        for w in ["the", "be"]:
            append_checkpoint(cp, w)
        assert load_checkpoint(cp) == {"the", "be"}

    def test_checkpoint_tolerates_truncated_last_line(self, tmp_path):
        cp = tmp_path / "checkpoint.jsonl"
        append_checkpoint(cp, "the")
        with open(cp, "a", encoding="utf-8") as f:
            f.write("trunc")  # crash between append and flush
        assert load_checkpoint(cp) == {"the"}

    def test_missing_checkpoint_is_empty(self, tmp_path):
        assert load_checkpoint(tmp_path / "absent.jsonl") == set()

    def test_pending_skips_completed_keeps_seed_order(self):
        assert pending_words(["be", "coffee", "of", "the"], {"be", "the"}) == ["coffee", "of"]

    def test_raw_roundtrip_and_duplicate_last_wins(self, tmp_path):
        raw = tmp_path / "raw.jsonl"
        append_raw(raw, "handover", trim_response(HANDOVER_RAW))
        append_raw(raw, "handover", trim_response(MISS_RAW))  # refetch after a crash
        loaded = load_raw(raw)
        assert loaded["handover"]["ec"] is None  # second append (miss) wins
        assert set(loaded["handover"]) == {"input", "ec"}

    def test_raw_keeps_word_keyed_entries(self, tmp_path):
        raw = tmp_path / "raw.jsonl"
        append_raw(raw, "handover", trim_response(HANDOVER_RAW))
        loaded = load_raw(raw)
        assert loaded["handover"]["ec"]["word"][0]["usphone"] == "ˈhændoʊvər"

    def test_raw_tolerates_truncated_last_line(self, tmp_path):
        raw = tmp_path / "raw.jsonl"
        append_raw(raw, "handover", trim_response(HANDOVER_RAW))
        with open(raw, "a", encoding="utf-8") as f:
            f.write('{"word": "trunc")')  # partial write from a hard kill
        assert set(load_raw(raw)) == {"handover"}


class TestTrimResponse:
    def test_keeps_only_input_and_ec(self):
        trimmed = trim_response(HANDOVER_RAW)
        assert set(trimmed) == {"input", "ec"}

    def test_missing_ec_becomes_none(self):
        assert trim_response(MISS_RAW)["ec"] is None

    def test_ec_data_preserved(self):
        trimmed = trim_response(HANDOVER_RAW)
        assert trimmed["ec"]["word"][0]["ukphone"] == "ˈhændəʊvə(r)"


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))

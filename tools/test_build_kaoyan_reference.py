# -*- coding: utf-8 -*-
"""
Tests for build_kaoyan_reference merging: the 考研 reference is the
variant-normalized union of two syllabus transcriptions (mahavivo
NPEE_Wordlist.txt and exam-data/NETEMVocabulary), with the newer NETEM form
preferred and hyphenated forms preferred over spaced ones.
"""

import sys
import os
import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_kaoyan_reference import us_key, choose_form, PHRASES  # noqa: E402


class TestUsKey:
    def test_suffix_variants_share_key(self):
        assert us_key("analyze") == us_key("analyse")
        assert us_key("organization") == us_key("organisation")
        assert us_key("center") == us_key("centre")
        assert us_key("color") == us_key("colour")
        assert us_key("catalog") == us_key("catalogue")
        assert us_key("civilization") == us_key("civilisation")

    def test_irregular_variants_share_key(self):
        assert us_key("defense") == us_key("defence")
        assert us_key("mold") == us_key("mould")
        assert us_key("program") == us_key("programme")
        assert us_key("judgment") == us_key("judgement")
        assert us_key("skeptical") == us_key("sceptical")
        assert us_key("maneuver") == us_key("manoeuvre")
        assert us_key("sulfur") == us_key("sulphur")
        assert us_key("fiber") == us_key("fibre")
        assert us_key("enroll") == us_key("enrol")
        assert us_key("fulfill") == us_key("fulfil")
        assert us_key("installment") == us_key("instalment")
        assert us_key("skillful") == us_key("skilful")
        assert us_key("inquiry") == us_key("enquiry")
        assert us_key("distill") == us_key("distil")
        assert us_key("aesthetic") == us_key("esthetic")
        assert us_key("artifact") == us_key("artefact")

    def test_hyphen_space_apostrophe_ignored(self):
        assert us_key("air-conditioning") == us_key("air conditioning")
        assert us_key("o'clock") == us_key("oclock")
        assert us_key("passer-by") == us_key("passerby")

    def test_case_ignored(self):
        assert us_key("Negro") == us_key("negro")

    def test_distinct_words_stay_distinct(self):
        assert us_key("dorm") != us_key("dormitory")
        assert us_key("maths") != us_key("mathematics")
        assert us_key("very") != us_key("every")


class TestChooseForm:
    def test_netem_form_preferred(self):
        assert choose_form("programme", "program") == "program"
        assert choose_form("mould", "mold") == "mold"
        assert choose_form("goodby", "goodbye") == "goodbye"

    def test_npee_form_used_when_netem_missing(self):
        assert choose_form("TV", None) == "TV"

    def test_hyphen_preferred_over_space(self):
        assert choose_form("air-conditioning", "air conditioning") == "air-conditioning"
        assert choose_form("ice-cream", "ice cream") == "ice-cream"

    def test_phrase_preferred_over_bare_adverb(self):
        # Both sources list the bare adverb or the phrase; the phrase is the
        # syllabus entry form, so the bare form is dropped by the caller.
        assert PHRASES["according"] == "according to"
        assert PHRASES["ought"] == "ought to"
        assert PHRASES["owing"] == "owing to"


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))

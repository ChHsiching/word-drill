# ADR 0003: Dictionary content is Youdao-only; the phonetic gate targets preset books

Date: 2026-11-19
Status: Accepted (owner decision taken mid-implementation of the v0.3 dictionary workstream)

## Context

The crawl's real numbers diverged from the spec's assumption of near-universal phonetics:
Youdao returns phonetics for ~49% of the 100k-word inventory overall. The uncovered words
are overwhelmingly deep-tail inventory filler (taxonomic names, chemical compounds,
abbreviations), while the three preset-book word lists are covered at ~100%. A probe of
Wiktionary found IPA for only ~4% of the uncovered tail (and rate-limited the probe), and
the owner rejected any ECDICT backfill outright — ADR 0001's retirement of ECDICT is
absolute, including origin-neutral data such as IPA.

## Decision

- All dictionary content — definitions, phonetics, domain labels — is 100% Youdao-sourced.
  No external backfill of any kind, ever.
- The retired dataset's word inventory served only as the crawl's lookup list (which words
  to fetch), exactly as the spec defined; none of its data ships in the dictionary.
- Words Youdao has no entry for are excluded from the dictionary; importing them shows the
  not-found state.
- Words with a Youdao entry but no phonetic keep their definitions (they serve imported
  word lists' auto-lookup); cards simply show no phonetic for those words.
- Build validation hard gate: the three preset-book word lists reach 100% phonetic
  coverage. Overall coverage is reported, not gated.

## Consequences

- Imported-list auto-lookup keeps definitions for ~90% of the lookup list; phonetic-less
  words are almost exclusively deep tail that no study path touches.
- No second data source to maintain, no mixed provenance anywhere.
- The phonetic-preference setting (#58) must define fallback behavior when the chosen
  variant is missing for a word (show the other variant vs show nothing).

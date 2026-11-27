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

- Dictionary definitions and domain labels are 100% Youdao-sourced. No second source for
  meanings, ever.
- Phonetics are Youdao-first; English Wiktionary IPA is permitted as a gap-fill source for
  MISSING variants only (origin-neutral IPA, never definitions). Owner decision 2026-11-20:
  a word must not stay single-variant if Wiktionary knows the missing variant.
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
- Phonetic provenance is two-tier (Youdao primary, Wiktionary gap-fill) but meanings stay
  single-source; the build script records fill counts in its report.
- Wiktionary rate-limits bulk queries — the gap-fill must be paced (≤1 req/s), resumable,
  and tolerate partial fills (words neither source covers keep whatever they have).
- The phonetic-preference fallback (word lacks the chosen variant) is: show the available
  variant, show nothing if neither exists.

## Amendments

- 2026-11-25, owner decision: the three preset books carry no person-name entries. Youdao
  returns a name line for almost every spelling that exists as a surname. The build drops it
  for preset words only; the other words of the dictionary keep theirs.
- 2026-11-27, owner decision: errors in Youdao's own text are corrected for preset words, and
  only those a source check confirms. The build applies a listed table,
  `tools/data/sense_corrections.tsv`, after every other rule. A row enters the table only
  when the defect is in Youdao's raw line and the replacement is confirmed by the dictionary
  that owns the entry, or, for character forms and regional terms, by the 2002 variant-word
  standard, the 2013 standard character table, the approved terms of the terminology
  committee, or a dictionary. "No second source for meanings" still holds: no meaning is
  taken from another source, a row only repairs Youdao's text. The build stops when a row no
  longer matches, and a test fails when the bundled books do not carry the table.

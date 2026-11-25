# Preset book word lists — provenance

How the CET-4, CET-6, and 考研 preset books in `app/src/main/assets/words.json`
were rebuilt for v0.3 (issue #48), which sources produced them, and how the
result is verified. Full legal research lives in the v0.3 spec's research
notes; this file records what shipped and how to reproduce it.

## Pipeline

```
official CET 2016 PDF ──> tools/extract_cet_syllabus.py ──> tools/data/cet4_2016.txt
                                                           tools/data/cet6_2016.txt
NPEE_Wordlist.txt  ─┐
                    ├──> tools/build_kaoyan_reference.py ──> tools/data/kaoyan.txt
netem_full_list.json┘
                                                           │
tools/data/*.txt + assets/dictionary.json ──> tools/gen_words_json.py ──> assets/words.json
                                                           │
tools/data/*.txt + assets/words.json ──> tools/validate_preset_books.py (PASS/FAIL)
```

Reference lists in `tools/data/` are committed; the upstream source files are
not (they are downloaded per run; paths are passed as arguments).

## CET-4 / CET-6

Source: the official syllabus PDF 《全国大学英语四、六级考试大纲（2016年修订版）》
hosted by 教育部教育考试院 at cet.neea.edu.cn —
https://www.neea.edu.cn/res/Home/1704/55b02330ac17274664f06d9d3db8249d.pdf
(215 pages, SHA-256 `9166d3c03b7bc43abd9d9df91bd2ef8085b4419286f1e5ca100dea68f3cfd1f1`,
downloaded 2026-11-18).

Extraction (`tools/extract_cet_syllabus.py`, requires PyMuPDF): the 词表 spans
PDF pages 21–149 as family rows in up to four columns — column 1 holds the
词目 (the counted syllabus headword), later columns hold derivatives aligned on
the same baseline. Band-6 words carry a ★ in a separate span. The extractor
takes only column-1 cells for 词目; derivatives are display material, not
counted 词目, and are excluded from the books — with one exception, the
full-word second spellings of variant pairs (see below).

Printed forms are canonicalized: `X/-yyy` variant spellings keep the primary
form (`center/-tre` → `center`), parenthesized optional letters are dropped
(`labo(u)r` → `labor`), the font's hyphen glyph (fullwidth G, U+FF27) and
private-use apostrophe are mapped back, and four phrases whose space is lost
in the text layer are restored (`accordingto` → `according to`; likewise
`ought to`, `owing to`, `coup d'état`).

Counts and known discrepancies:

- The PDF prints its own totals (5,418 headwords, 2,551 derivatives) after the
  last entry, but only 5,377 headword rows are actually typeset. The
  extraction takes the typeset content as ground truth.
- ★-marked rows: exactly 1,263, matching the research note. Band-4 rows:
  4,115.
- 73 headword texts repeat in separate rows (poly-POS 分列, e.g. `bound` ×3);
  they are merged once. Four of them carry mixed star flags (`appropriate`,
  `converse`, `invalid`, `staple`); starred wins, so they sit in the band-6
  additions only.
- Final: CET-4 = 4,070 distinct unstarred headwords; CET-6 = 5,341 distinct
  (the full 4+6 list — the CET-6 book is the cumulative band-6 load, as it
  has always been), both including the variant seconds below. Against the
  printed 5,418 claim the −77 residual is the 41 untypeset rows plus the 79
  duplicate merges, offset by the +43 variant seconds.
- Odd entries verified against the rendered page: `advisor/-er` and
  `sober/-re` really print that way (the latter's variant is meaningless in
  English; the primary `sober` is kept). `Celsius/-cius` likewise.

### Variant second spellings (owner decision, 2026-11-22)

The syllabus prints spelling-variant pairs side by side (`anybody/anyone`,
`check/cheque`, `advertisement/ad`) in the headword and derivative columns
alike. The primary form is the counted 词目; per the owner's decision —
"a word not collected is a word not learned" — the second spelling is now
also emitted as a headword whenever it is a full word. Scope and rules:

- Every printed `X/yyy` pair contributes its second form, whichever column
  it prints in. Case-only repeats (`Internet/internet`, `Biblical/biblical`)
  and suffix abbreviations (`advise/Ｇer`, i.e. advis-er) are the same word
  and stay excluded. Case is kept as printed (`BBQ`, `TV`, `ad`; `apt.` →
  `apt`), and the ★ band is inherited from the row.
- 51 seconds qualify; 8 of them (`aesthetic`, `apt`, `despatch`, `enquire`,
  `ensure`, `gaol`, `petrol`, `tire`) are already listed as their own rows
  and merge — the syllabus itself double-lists them. That yields +43 new
  headwords: CET-6 5,298 → 5,341, CET-4 4,035 → 4,070 (the unstarred 36 of
  the 43; `despatch` moves to band-6 because the starred `dispatch/despatch`
  row now contributes its ★ under the existing any-starred-wins merge).
  Band-6 additions: 1,263 → 1,271 (7 starred seconds — `aesthetics`,
  `manoeuvre`, `sceptic`, `sceptical`, `scepticism`, `speciality`,
  `sulphur` — plus the `despatch` promotion).
- The 4 abbreviation pairs (`ad`, `apt`, `BBQ`, `TV`) are included under the
  same decision.
- Two rows typeset a derivative close enough to the headword that the cell
  splitter merged them (`esthetic/aesthetic` + `esthetics/aesthetics` on
  p63, `refrigerator/fridge` + `refrigeration` on p114); a tighter
  piece-level split (`split_pieces`) now separates them so the headword and
  its variant pair parse cleanly.

## 考研

No official digital source exists (the 大纲 is a paid paper book), so the
reference is a cross-verified union of two transcriptions, checked against a
third:

- mahavivo/english-wordlists `NPEE_Wordlist.txt` (5,394 distinct) — widely
  circulated transcription, but lossy: entire runs of common u–y words
  (`very`, `usage`, `urban`, `violence`, ...) are missing and it reflects an
  older 大纲 edition.
- exam-data/NETEMVocabulary `netem_full_list.json` (5,530 words) — derived
  from the 2024 考研英语(一) 大纲词汇表, but trimmed by its own frequency
  ranking (`comfortable`, `building`, `practice` absent).
- kajweb/dict 考研 books (5,047 distinct) — Youdao-curated subset used as the
  verification axis only; it agreed with the union on the mid-frequency band.

`tools/build_kaoyan_reference.py` merges the first two: words merge when
lowercased, hyphen/space/apostrophe-stripped, and UK→US normalized (suffix
rules plus an irregular-pair table: `defence/defense`, `mould/mold`,
`programme/program`, ...). The NETEM form wins for display (newer edition),
a hyphenated form beats a spaced one (`air-conditioning` over
`air conditioning`), and bare `according`/`ought`/`owing` drop in favor of the
phrase entries both syllabi print. Result: 5,658 words, +2.9% against the
大纲's printed "约5,500".

## Dictionary content

`tools/gen_words_json.py` attaches content (phonetic, senses) from the
bundled `assets/dictionary.json` (Youdao-sourced per ADR 0001/0003, built by
`tools/build_youdao_dictionary.py`) by exact word match.

Every preset word has content; the build fails otherwise (owner ruling,
2026-11-23: no syllabus word may ship without a definition):

- **Spelling aliases.** `PRESET_LOOKUP_ALIASES` in the build script maps a
  syllabus spelling Youdao does not list under that exact form to its headword
  and emits the headword's entries under the syllabus spelling as well. One
  pair, verified against live Youdao: `babyboom` (printed closed up in the
  official CET list, absent from Youdao) -> `baby boom`. The content stays
  Youdao's. Growing the table is an owner decision.
- **Parts of speech.** The build recognises every POS spelling Youdao uses on
  preset words, among them `comb.`, `pref.`, a full-width dot as in `n．` and
  an uppercase `V.`. `inter.` and `pronoun.` are mapped onto `int.` and
  `pron.`, and the one `cn.` typo (friendly) onto `n.`.
- **Subject labels.** Youdao marks subject labels and ordinary qualifiers the
  same way, with full-width parens or angle brackets. Examples are （电信）切换,
  <史>毁船打劫 and 理解（力）. A marker becomes a 【】 label only when it opens
  a sense segment and every part of it is in `DOMAIN_LABELS` in the build
  script. Everything else stays a parenthetical. The inventory was closed by
  reading every preset-word line on 2026-11-23. `物` is left out on purpose,
  because （物） opens segments meaning "of a thing" as often as "physics", so
  those stay parenthetical and （物理） carries the label.
- **Punctuation.** A paren pair whose opener and closer have different widths
  (Youdao typos such as `(未经证实地）` and `（Bike)`) is widened to one width.
  A balanced half-width pair in Chinese text gets the full width, for example
  `格雷丝 (女名)`. An English phrase such as `call (sb) up` keeps its
  half-width parens. Every ellipsis spelling becomes `……`, and stray spaces
  between Chinese characters are removed.
- **Person-name lines.** Youdao's 【名】 line becomes an entry of its own with
  an empty part of speech, so it never merges into the senses. Its literal
  `null` placeholder is dropped, and a name line printed twice is kept once.
  The exam books carry no person-name entries (owner decision 2026-11-25). For
  a preset word the build drops that line and the name entry Youdao appends to
  a sense, plus the untagged person and place names listed in
  `UNTAGGED_NAME_ENTRIES` in the build script. Other words of the dictionary
  keep theirs, and usage notes that merely mention a name stay, such as
  title's （人名前表示地位、职业或婚姻状况的）头衔.
- **Repeated senses.** A sense segment that repeats an earlier one under the
  same part of speech is kept once (Youdao repeats senses inside one line; the
  copies differ only by whitespace).

Non-preset words Youdao has no entry for are excluded from the dictionary;
importing them shows the not-found state (ADR 0003).

## Verification

`tools/validate_preset_books.py` checks each book against its reference list
(zero missing / zero extra), its count within ±5% of the official printed
syllabus size (4,155 / 5,418 / 5,500), and absence of duplicates. Run:

```
python tools/extract_cet_syllabus.py <cet-syllabus-2016.pdf>
python tools/build_kaoyan_reference.py <NPEE_Wordlist.txt> <netem_full_list.json>
python tools/gen_words_json.py
python tools/validate_preset_books.py
python -m pytest tools/ -q
```

## Licensing note

The research notes flag that the official syllabi are copyrighted
publications and free access is not a redistribution license; the spec (#47)
nevertheless locked these sources for the preset books. The shipped lists are
bare word forms; dictionary definitions are Youdao-sourced (ADR 0001/0003).
If the owner later wants a cleaner stance, the reference lists and
regenerating scripts here make any swap a one-file change.

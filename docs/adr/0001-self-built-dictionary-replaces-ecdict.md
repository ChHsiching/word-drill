# ADR 0001: Self-built dictionary (Youdao crawl) replaces ECDICT

## Status

Accepted (2026-11-18)

## Context

The app shipped with ECDICT (MIT, community-maintained, last release 2017) as its built-in English-Chinese dictionary. A user-facing audit found systematic quality problems: machine-translation noise in senses (telecom terminology in CET words), domain senses unlabeled, mixed full/half-width punctuation, single blended phonetic, missing phonetics for some words. Preset books also diverged from official exam syllabus counts.

Three research tracks investigated alternatives:
1. **Official syllabus wordlists** — exist and are downloadable, but are copyrighted compilations with no open-data license.
2. **Publisher APIs** (Oxford, Collins, Merriam-Webster, Longman, Cambridge, 有道, 百度) — all prohibit offline bundling or caching; enterprise-priced or B2B only.
3. **Open datasets** — ECDICT is the only legally clean 100k-word base; Wiktionary extracts (kaikki.org) offer better phonetics but thin Chinese glosses; richer Chinese datasets are unlicensed scrapes.

The owner's criteria: **quality + usability > convenience**, and **distrust of non-Chinese sources for learner-facing content**.

## Decision

Replace ECDICT with a self-built dictionary crawled from 有道词典 (Youdao). Retire ECDICT entirely — no fallback layer.

Key properties:
- Full 100k-word coverage (crawl seeds from ECDICT word inventory + official syllabus).
- UK + US phonetics stored separately; user selects preference (英式/美式/双语) in settings.
- Domain labels inline in sense text (e.g., 【电信】核心网).
- Preset books rebuilt from official exam syllabus word lists.
- One-off resumable crawl script; GitHub Actions-compatible via chunked runs.

## Consequences

**Positive:**
- Highest available quality for Chinese learners (Youdao carries publisher-grade content).
- Domain-labeled senses, dual phonetics, clean punctuation — all user-facing defects resolved.
- Full ownership of the data pipeline; can re-crawl at will.
- Preset books match official syllabi exactly.

**Negative:**
- ECDICT is gone; if Youdao changes their API, the pipeline breaks until fixed.
- The crawl is a one-time engineering investment (~28 hours machine time, script development).
- Legal posture: crawling commercial dictionary content is categorically different from using open data — the owner has explicitly accepted this risk.

**Why not the alternatives:**
- Keeping ECDICT: quality ceiling too low (owner: "它的也不好用").
- Wiktionary/kaikki: Chinese glosses too thin to serve as primary content.
- Publisher APIs: no offline path exists at any accessible price point.

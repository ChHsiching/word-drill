# Research: auto-advance dwell time + control placement for word cards (issue #28)

## Scope and method

Two questions for the Drill screen auto page-turning feature (issue #28):

1. Q1 — what dwell time (seconds per card) comparable products use or recommend
   for auto-advance of flashcards/cards.
2. Q2 — where mobile apps put the trigger for auto-play/auto-advance of card
   content, and how timing is changed.

Sources are primary wherever possible: source code on GitHub (read via `gh`),
official manuals/help centers, m3.material.io (rendered in a real browser), and
one peer-reviewed paper. Every claim below cites its owning source. Where only
a secondary source exists (or none), that is stated explicitly.

Note on the prompt's suggested source: `https://github.com/timrae/anki-auto-advance`
returns 404 (repo deleted or made private). Auto advance is now a **native**
feature in Anki desktop (23.12+) and in AnkiDroid (renamed
`ankidroid/Anki-Android`), so the native implementation is the primary source
used instead.

---

## Q1 — Dwell time numbers for auto-advancing cards

### Findings by product

**Anki desktop (native Auto Advance, Anki 23.12+).** Defaults are **0.0 s for
"Seconds to show question for" and 0.0 s for "Seconds to show answer for"** —
i.e. the feature ships disabled and both timings must be set non-zero to work.
From the deck-config defaults in `rslib/src/deckconfig/mod.rs` (lines 68-69):

```rust
seconds_to_show_question: 0.0,
seconds_to_show_answer: 0.0,
```

Source: <https://github.com/ankitects/anki/blob/main/rslib/src/deckconfig/mod.rs>

The deck-options UI (`ts/routes/deck-options/AutoAdvance.svelte`) renders both
timings as float spinboxes with `step={0.1}`; the spinbox component
(`ts/routes/deck-options/SpinBoxFloatRow.svelte`) declares
`export let min = 0; export let max = 9999;` — so the **configurable range is
0–9999 s in 0.1 s steps** (the deck options page does not override the bounds).

Sources:
<https://github.com/ankitects/anki/blob/main/ts/routes/deck-options/AutoAdvance.svelte>,
<https://github.com/ankitects/anki/blob/main/ts/routes/deck-options/SpinBoxFloatRow.svelte>

The official manual documents the mechanism and the 0-means-off rule, but not
any recommended values: "To use it, you must first set a non-zero time in
**Seconds to show question for** and/or **Seconds to show answer for**. Then,
in the study screen, use the Auto Advance action from the **More** button to
start advancing." The help tooltip says "Set to 0 to disable."
Source: <https://docs.ankiweb.net/deck-options.html#auto-advance>,
tooltip in `ftl/core/deck-config.ftl`
(<https://github.com/ankitects/anki/blob/main/ftl/core/deck-config.ftl>).

Useful cross-reference from the same defaults file (line 65):
`cap_answer_time_to_secs: 60` — Anki's own "Maximum answer seconds" default is
**60 s per card**, which anchors what its designers consider the ceiling of a
reasonable per-card dwell.

**AnkiDroid (now `ankidroid/Anki-Android`).** Timing is read from the same
shared deck config (`config.secondsToShowQuestion` / `secondsToShowAnswer`
converted to `Duration`), so defaults and range are identical to desktop;
0 disables. Source:
<https://github.com/ankidroid/Anki-Android/blob/main/AnkiDroid/src/main/java/com/ichi2/anki/ui/windows/reviewer/autoadvance/AutoAdvanceSettings.kt>
and the timer implementation
<https://github.com/ankidroid/Anki-Android/blob/main/AnkiDroid/src/main/java/com/ichi2/anki/ui/windows/reviewer/autoadvance/AutoAdvance.kt>
("If a timer is set to 0, the corresponding action is not triggered.").
The legacy AnkiDroid "Automatic Display Answer" global setting was removed:
changelog entry "Auto Advance now located in Deck Options and syncs with all
Anki clients" —
<https://github.com/ankidroid/Anki-Android/blob/main/AnkiDroid/src/main/assets/changelog.html>.

**Quizlet (Flashcards mode).** Official help documents autoplay as a Play
control with **no user-facing timing at all**: "If you don't review your cards
manually, select Play to cycle through automatically." Audio (per-card speaker
icon, Options menu audio) is the pacing mechanism; no speed or seconds setting
is described for autoplay on web, iOS, or Android. Source:
<https://help.quizlet.com/hc/en-us/articles/360030988091-Studying-with-Flashcards>.
(The first-party blog post announcing the mode was 403-blocked during this
research; the help center is the primary reference used.)

**Chinese vocabulary apps (墨墨记词 / 不背单词 / 扇贝单词).** Negative finding,
stated plainly: none of the three exposes a documented default seconds-per-word
for auto play in any official help/doc I could reach.

- 墨墨背单词's official help page (`https://www.maimemo.com/help30`) covers
  认识/模糊/忘记/熟知 grading, 选词, sync, reminders — it documents **no**
  auto-play/自动播放 dwell setting at all. Source:
  <https://www.maimemo.com/help30>.
- 不背单词 and 扇贝单词 have "listen" style features (听学/随身听-style audio
  playback of words and example sentences), but their public pages document no
  per-card dwell number; where a control exists it is audio-speed (倍速), not
  seconds-between-cards. No official primary source found; web searches
  surfaced only third-party reviews.

The pattern worth carrying over: the Chinese apps that do auto-advance couple
it to audio duration (advance when pronunciation/example audio ends) rather
than a fixed timer.

**Google Slides (cross-domain reference).** The official help confirms the
control and where it lives, but does **not** enumerate the second values:
while presenting, "At the bottom, click Options > Auto advance options …
Choose how quickly to advance your slides"; for published decks,
"Under 'Auto-advance slides,' choose how much time you want to add between
slides." Source:
<https://support.google.com/docs/answer/1696787> ("Present slides").
The preset values (every 1/2/3/5/10/15/30/60 s, then minutes) and a 3 s
default in the publish dialog are reported by Google's own support community
threads — community, not documentation:
<https://support.google.com/docs/thread/204727562>,
<https://support.google.com/docs/thread/8568896>.

**Reading-rate research (lower bound).** Brysbaert (2019, *Journal of Memory
and Language* 109, 104047, meta-analysis of 190 studies) found adult silent
reading of English averages **238 wpm ≈ 0.25 s/word** for native readers
reading connected text for comprehension; second-language readers read
substantially slower. That is the floor for *fluent connected* reading; a
flashcard dwell is not one word in context but two text fragments (word +
gloss) plus a recognition/decision step, so a card needs a multiple of that.
DOI: <https://doi.org/10.1016/j.jml.2019.104047>. (Full text was access-walled
during this research; the 238 wpm figure is the paper's headline abstract
number. The L2 slowdown is directional, not a fixed percentage I can quote
from the paper.)

### Q1 table

| Product / reference | Default dwell (per card) | Configurable range | Source URL |
|---|---|---|---|
| Anki desktop (native, 23.12+) | 0.0 s question / 0.0 s answer (off) | 0–9999 s each, 0.1 s steps (spinbox min/max) | https://github.com/ankitects/anki/blob/main/rslib/src/deckconfig/mod.rs ; .../ts/routes/deck-options/SpinBoxFloatRow.svelte |
| AnkiDroid / Anki-Android | inherits deck config (0 = off) | same deck options, synced with all Anki clients | https://github.com/ankidroid/Anki-Android/blob/main/AnkiDroid/src/main/java/com/ichi2/anki/ui/windows/reviewer/autoadvance/AutoAdvanceSettings.kt |
| Anki "Maximum answer seconds" (anchor) | 60 s | deck options | https://github.com/ankitects/anki/blob/main/rslib/src/deckconfig/mod.rs (cap_answer_time_to_secs: 60) |
| Quizlet Flashcards autoplay | not documented (audio-paced) | none exposed | https://help.quizlet.com/hc/en-us/articles/360030988091-Studying-with-Flashcards |
| 墨墨背单词 | none documented | n/a | https://www.maimemo.com/help30 |
| 不背单词 / 扇贝单词 | none documented publicly (listen modes are audio-paced; 倍速 not seconds) | n/a (no official doc found) | — |
| Google Slides auto-advance | 3 s (community-reported default) | presets 1–60 s then minutes; no custom values | https://support.google.com/docs/answer/1696787 (official, values unlisted) + https://support.google.com/docs/thread/204727562 (community) |
| Fluent reading floor | 238 wpm ≈ 0.25 s/word (L1, connected text) | — | https://doi.org/10.1016/j.jml.2019.104047 |

### Q1 synthesis (recommendation)

No comparable product ships a non-zero default — Anki's default is off, Google
Slides centers its presets on 3–15 s, and audio-driven apps (Quizlet, the
Chinese vocab apps) pace by audio rather than a timer — so word-drill should
treat auto-advance as opt-in with a modest default. The floor should be about
2 s: Brysbaert's 0.25 s/word is for one word in context, and a card demands
word + gloss + a recognize/decide step, so anything faster becomes a flipbook.
I recommend **min 2 s / default 5 s / max 30 s** in 1 s steps: 5 s sits in the
middle of Google Slides' practical presets, and a 30 s cap stays well under
Anki's own 60 s "Maximum answer seconds" ceiling while leaving slow readers
room. (If word pronunciation audio is ever added, consider Quizlet's model:
advance on audio end, with the timer as fallback.)

---

## Q2 — Control placement and form for auto-play

### Findings by pattern

**Material 3 (m3.material.io, incl. the May-2025 M3 Expressive update).**
Pages fetched in a rendered browser on 2026-11-16.

- *App bars* (the old "top app bar" page now lives at
  `/components/app-bars/overview`): "App bars are placed at the top of the
  screen to help people navigate through a product." The overview advises:
  "Focus on describing the current page and provide **1–2 essential actions**"
  and "Displays labels and page navigation controls at the top of the page.
  (Use a **toolbar** to display page actions.)" So M3 explicitly steers
  page-level actions away from the top bar and toward toolbars.
  Source: <https://m3.material.io/components/app-bars/overview>
  (redirect target of <https://m3.material.io/components/top-app-bar/overview>).
- *Toolbars* (the old "bottom app bar" URL now redirects here): "Toolbars
  display frequently used actions relevant to the current page." Two variants:
  **docked toolbar** (bottom) and **floating toolbar** — "The bottom app bar is
  no longer recommended and should be replaced with the docked toolbar … The
  floating toolbar was created for more versatility, greater amounts of
  actions, and more variety in where it's placed." Critically for our layout:
  "**Don't show at the same time as a navigation bar**."
  Source: <https://m3.material.io/components/toolbars/overview>.
- *FABs*: "Floating action buttons (FABs) help people take primary actions";
  "Use a FAB for the **most common or important action on a screen**"; "Make
  sure the icon in a FAB is clear and understandable"; "FABs persist on the
  screen when content is scrolling."
  Source: <https://m3.material.io/components/floating-action-button/overview>.
- M3 has no dedicated "media player" component page; the closest guidance is
  the above (toolbar/FAB) plus the reader-app precedents below.

**Anki desktop.** Auto-advance is started from the study screen's **More
button** (a menu action, not an on-screen button); timing is configured far
away in **Deck Options → Reviews → Auto Advance**. Source:
<https://docs.ankiweb.net/deck-options.html#auto-advance>.

**AnkiDroid / Anki-Android.** The trigger is a **menu item in the reviewer's
(top) overflow menu**: `ViewerAction.TOGGLE_AUTO_ADVANCE`. The item title
flips between "Enable auto advance" / "Disable auto advance" and its icon
between `ic_fast_forward_outlined` / `ic_fast_forward` (filled) while running.
Timing is **not** configured near the control — it is read from the per-deck
Deck Options screen. Sources:
<https://github.com/ankidroid/Anki-Android/blob/main/AnkiDroid/src/main/java/com/ichi2/anki/ui/windows/reviewer/ReviewerMenu.kt>
(lines 140-152),
<https://github.com/ankidroid/Anki-Android/blob/main/common/android/src/main/res/values/10-preferences.xml>
(`toggle_auto_advance` / `enable_auto_advance` / `disable_auto_advance`).
There is also a user-bindable answer-button/shortcut for the toggle
(`ActionButtonStatus.kt`, `action_toggle_auto_advance`).

**Quizlet.** In Flashcards mode the Play control sits in the mode's control
bar under/around the card (web: the bottom control strip of the flashcard
player); options (audio, shuffle) are behind a gear. Official help: "select
Play to cycle through automatically." No timing control exists anywhere.
Source: <https://help.quizlet.com/hc/en-us/articles/360030988091-Studying-with-Flashcards>.

**Kindle read-aloud (Assistive Reader).** Official Amazon help: enable it from
the reading settings — "Tap the top center of the screen, and then select the
reading settings menu, Aa. Select More, and then turn on Assistive Reader." —
but the playback surface is at the bottom: "Play, pause, control the reading
speed, or rewind by 30 seconds using the **player controls at the bottom of
the screen**." Timing (speed) is changed inside those bottom player controls.
Source:
<https://www.amazon.com/gp/help/customer/display.html?nodeId=TdHCSXXqsdhaVKt3Tc>.

**Pocket.** Not verifiable from a live primary source: Mozilla sunset Pocket
on 2025-07-08 (announcement "Pocket is Saying Goodbye" / "Shutting Down
Pocket", 2025-05-22) and the old `help.getpocket.com` pages now redirect to
Mozilla support and could not be fetched. Historically the Listen player was a
bottom-anchored bar with play/pause and speed controls; treat as secondary
knowledge, cited only to the sunset announcement.

**Instagram Stories / TikTok (autoplay-default).** Included for completeness
with an honest caveat: Meta's help center page covering story tap-gestures
could not be fetched during this research (the enumerated article IDs return
"page unavailable"), so there is no primary URL. The well-known pattern —
autoplay is the default state, **no play button at all**; segmented progress
bars at the top; tap right = next; press-and-hold = pause; per-item duration
fixed and not user-configurable — is consistent across secondary write-ups.

### Q2 table

| Pattern | Where the control lives | How timing is changed | Source URL |
|---|---|---|---|
| Anki desktop | Study screen **More** menu action | Deck Options → Reviews → Auto Advance (separate screen) | https://docs.ankiweb.net/deck-options.html#auto-advance |
| AnkiDroid (Anki-Android) | Reviewer top **overflow menu** item "Toggle auto advance" (fast-forward icon, filled while running) | Deck Options per deck (synced); not near the control | https://github.com/ankidroid/Anki-Android/blob/main/AnkiDroid/src/main/java/com/ichi2/anki/ui/windows/reviewer/ReviewerMenu.kt |
| Quizlet flashcards | **Play control in the mode's bottom control bar** under the card; gear for options | No timing control (audio-paced) | https://help.quizlet.com/hc/en-us/articles/360030988091-Studying-with-Flashcards |
| Kindle Assistive Reader | Enable behind top toolbar (Aa → More); playback **at bottom of screen** | Speed inside the bottom player controls | https://www.amazon.com/gp/help/customer/display.html?nodeId=TdHCSXXqsdhaVKt3Tc |
| Pocket Listen | (Historical) bottom player bar | speed in player — primary source offline | Mozilla sunset announcement, 2025-05-22 |
| Instagram Stories / TikTok | No play control — autoplay is ambient; progress at top; tap/hold gestures | Not user-configurable | no fetchable primary doc (stated) |
| Material 3 app bars | Top bar holds "1–2 essential actions" only; "use a toolbar to display page actions" | — | https://m3.material.io/components/app-bars/overview |
| Material 3 toolbars | Docked (bottom) or floating toolbar for "frequently used actions"; **don't show at the same time as a navigation bar** | — | https://m3.material.io/components/toolbars/overview |
| Material 3 FAB | "Most common or important action on a screen"; persists during scroll | — | https://m3.material.io/components/floating-action-button/overview |

### Q2 synthesis (fit to the Drill screen)

The convergent pattern for card/reader content is a play/pause control at the
**bottom of the content area, near where the thumb rests** (Kindle's bottom
player, Quizlet's bottom control bar), while the top bar is reserved for
identity/navigation plus at most 1–2 essential actions (M3 app-bar guidance)
and timing, when changeable at all, is either not exposed (Quizlet, Stories)
or lives in a settings surface (Anki deck options). M3's toolbars guidance
adds a direct constraint for word-drill: a toolbar must **not** coexist with a
navigation bar, and the floating capsule nav must not be overlapped anyway —
which rules out a floating toolbar hovering at the bottom edge.

For the Drill screen as it exists (top bar = book name + counter + skip
text-button + 30 dp circular lock button; bottom half empty; capsule nav
floating at the bottom), the best fit is: **a single circular play/pause
control (FAB-style, M3 sanctions it as "the most common or important action")
placed in the empty bottom half of the screen, horizontally centered or
end-aligned above the capsule nav with clear spacing, sized to match the
existing 30 dp lock button for rhythm.** Start auto-advance on tap, pause on
second tap (or on any manual swipe — same interruption semantics as AnkiDroid,
where any manual action supersedes the timer), and show remaining time as a
thin progress ring or bar on the button itself (the Stories pattern, minus its
fixed duration). Timing belongs in a small inline affordance (e.g. a "5 s" chip
next to the button cycling through 2/3/5/10/15/30 s) rather than a detour into
a settings screen, since Anki's deck-options detour is the one complaint its
own forum threads keep returning to.

---

## Source list (all primary unless noted)

- Anki deck-config defaults: <https://github.com/ankitects/anki/blob/main/rslib/src/deckconfig/mod.rs>
- Anki deck-options Auto Advance UI: <https://github.com/ankitects/anki/blob/main/ts/routes/deck-options/AutoAdvance.svelte>
- Anki spinbox bounds: <https://github.com/ankitects/anki/blob/main/ts/routes/deck-options/SpinBoxFloatRow.svelte>
- Anki manual, deck options: <https://docs.ankiweb.net/deck-options.html#auto-advance>
- Anki-Android auto-advance package: <https://github.com/ankidroid/Anki-Android/tree/main/AnkiDroid/src/main/java/com/ichi2/anki/ui/windows/reviewer/autoadvance>
- Anki-Android reviewer menu toggle: <https://github.com/ankidroid/Anki-Android/blob/main/AnkiDroid/src/main/java/com/ichi2/anki/ui/windows/reviewer/ReviewerMenu.kt>
- Anki-Android changelog: <https://github.com/ankidroid/Anki-Android/blob/main/AnkiDroid/src/main/assets/changelog.html>
- Quizlet help, Flashcards: <https://help.quizlet.com/hc/en-us/articles/360030988091-Studying-with-Flashcards>
- 墨墨背单词 official help: <https://www.maimemo.com/help30>
- Google Slides "Present slides" help: <https://support.google.com/docs/answer/1696787>
- Google Slides preset values (community, labeled): <https://support.google.com/docs/thread/204727562>, <https://support.google.com/docs/thread/8568896>
- Brysbaert 2019 reading-rate meta-analysis: <https://doi.org/10.1016/j.jml.2019.104047>
- M3 App bars: <https://m3.material.io/components/app-bars/overview>
- M3 Toolbars: <https://m3.material.io/components/toolbars/overview>
- M3 FAB: <https://m3.material.io/components/floating-action-button/overview>
- Amazon Kindle Assistive Reader help: <https://www.amazon.com/gp/help/customer/display.html?nodeId=TdHCSXXqsdhaVKt3Tc>
- Mozilla "Pocket is saying goodbye" sunset announcement (2025-05-22, blog.mozilla.org) — historical Pocket row only

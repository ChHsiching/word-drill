# Research: "mini player above a bottom tab bar" in real apps, and play controls in text-content apps

## Scope and method

Follow-up to `research-auto-advance-ux.md` (issue #28). The design proposal
claimed to borrow "Apple Music's miniplayer grammar" — described as **two
same-width pills stacked with a small gap** — and the result was twice rejected
as "nothing like Apple Music". This research establishes what the pattern
actually looks like, from **downloaded screenshots that were viewed
visually**, not from memory.

Method: every product below has at least one screenshot image file downloaded
into `research-imgs/` (this directory) and inspected with the image viewer.
Image paths in parentheses are relative to `.scratch/`. All images were
fetched 2026-11-16; provenance URLs are listed per claim and in the source
list at the bottom. Secondary sources are labeled as such.

Headline correction of the premise: **in every app examined, there is exactly
ONE accessory surface above (or merged into) the navigation bar.** No app
stacks two same-width pills.

---

## 1. Apple Music on iOS 18 (pre-Liquid Glass)

**The miniplayer is a single full-width rounded-rectangle card sitting ABOVE
the tab bar with a small visible gap; the tab bar below is an unboxed icon
strip — two visually different shapes, not two matching pills.**

AppleInsider's iOS 26 vs iOS 18 comparison shows both eras side by side
(research-imgs/ai-music-home-ios18-vs-26.jpg; source:
<https://appleinsider.com/inside/apple-music/vs/apple-music-ios-26-vs-ios-18-automix-liquid-glass-and-lyrics-in-your-language>,
fetched via Wayback Machine). What is visible in the iOS 18 phone (left):

- **Leading edge: a small square album-art thumbnail** flush at the left
  inside edge of the card. Then one line of song title. Nothing else.
- **Trailing edge: exactly two glyphs** — play (triangle) and fast-forward
  (double triangle). No progress bar, no extra buttons.
- The card spans nearly the full screen width (same side margins as the
  content lists), with **small corner radii** (a rounded rect, not a capsule).
- Its height is a **single text row, roughly equal to or slightly shorter
  than the tab bar** below it.
- The tab bar beneath has **five icon+label items** (Home / New / Radio /
  Library / Search) on a plain translucent strip with **no container shape
  around them** — visually nothing like the card above it.

So even pre-26 the pattern was never "two same-width pills": it is one
content card hovering over a transparent icon strip.

## 2. Apple Music on iOS 26 (Liquid Glass)

### Resting state: one glass pill above the tab-bar capsule, with a gap — and the pill is WIDER than the tab bar

MacStories iOS 26 review, Design chapter
(research-imgs/ms-legibility-tabbar.png right phone; source:
<https://www.macstories.net/stories/ios-and-ipados-26-the-macstories-review/2/>;
caption there: "Depending on what's behind Liquid Glass, legibility may
suffer."). What is visible:

- The miniplayer is a **glass capsule-shaped pill**: album-art square at the
  left, "A Little Love / Biffy Clyro" text, **two trailing glyphs
  (play + fast-forward)** at the right.
- Below it, separated by a **clear gap of roughly one-third of the bar's own
  height**, is the tab bar: a glass capsule containing [Home][New][Radio]
  [Library], plus a **separate circular Search button** floating to its right.
- Widths do NOT match: **the miniplayer spans the full accessory row width**
  (running over where the Search circle sits), while **the tab-bar capsule is
  narrower** — it stops short of the Search circle. Two different widths,
  two different shapes.
- The miniplayer is slightly taller than the tab-bar capsule.

The same layout in light mode, larger and cleaner: research-imgs/
ms-glass-across-os.png (leftmost phone; same review). The AppleInsider
comparison confirms it on the right phone of
research-imgs/ai-music-home-ios18-vs-26.jpg (art left, two glyphs right, pill
above capsule-with-gap, search circle separate).

### Scrolled state: the two surfaces MERGE into one row — this is the "morph"

The right phone of research-imgs/ai-music-scroll-ios26.jpg (same AppleInsider
source) shows the scrolled state: the tab bar has collapsed to a small [Home]
pill + [Search] circle, and **the miniplayer pill now sits between them in
the SAME row** — one single glass surface row, not two stacked shapes.
Two independent hands-on shots of this collapsed/inline state:
research-imgs/idrop-ios261-music-miniplayer.jpg (dark mode; source: iDrop
News "iOS 26.1 Is Here", <https://www.idropnews.com/news/ios-261-is-here-with-subtle-changes-youll-actually-notice/255083/>)
and research-imgs/9to5-music-swipe.jpg (light mode; source: 9to5Mac iOS 26.1
beta 1 article, <https://9to5mac.com/2025/09/22/ios-26-1-beta-1-changes-everything-new/>).
In all three, the row reads: [Home button] [miniplayer: art + title + play]
[Search circle].

iDrop News describes the motion explicitly: "Initially, it appears above the
button bar, similar to its previous iOS counterparts. However, once you begin
scrolling, the controls at the bottom collapse, and the MiniPlayer smoothly
flows down to sit between them." (Same article; iOS 26.1 additionally added
swiping the miniplayer left/right to change tracks.)

### Apple's own API documentation confirms the two states

SwiftUI `tabViewBottomAccessory(content:)` — "Places a view as the bottom
accessory of the tab view."
(<https://developer.apple.com/documentation/swiftui/view/tabviewbottomaccessory(content:)>).
The placement environment value documents the geometry rule exactly:
**"On iPhone, the placement of the bottom accessory depends on the tab bar
size: when the tab bar is normal size, the accessory appears above it; when
the tab bar is collapsed, the accessory displays inline."**
(<https://developer.apple.com/documentation/swiftui/environmentvalues/tabviewbottomaccessoryplacement>).
The iOS 26 Music app is the reference implementation (introduced at WWDC25,
session "Build a SwiftUI app with the new design",
<https://developer.apple.com/videos/play/wwdc2025/323/>).

## 3. Spotify mobile

**Full-width flat rectangle docked directly above the bottom nav, album art
bleeding to the left edge, three trailing controls, thin progress line along
the bar's bottom edge. No pill, no card, no matching widths.**

Two Android screenshots from 9to5Google's miniplayer coverage
(research-imgs/spotify-9t5g-1.png, research-imgs/spotify-9t5g-2.png; source:
<https://9to5google.com/2021/07/28/spotify-miniplayer-android-test/> — the
layout is unchanged to this day). Visible in both:

- The miniplayer is an **edge-to-edge rectangle** (squared corners, no side
  margins) sitting directly above the [Home | Search | Your Library] nav
  strip, separated by only a thin darker gap.
- **Leading edge: square album-art thumbnail at the very left edge of the
  bar**; then title + artist. **Trailing edge: three controls** — Spotify
  Connect (devices), like (heart), play/pause.
- A **thin progress line runs along the bottom edge** of the bar.
- The bar's background is tinted by the album art color (gray-green in the
  Muse shot, slate blue in the Louise Attaque shot) — it reads as a media
  surface, not navigation.
- Height is one text row, comparable to the nav bar height; the nav below is
  plain icons + labels with no container.

Current state (Oct 2026): Spotify adopted partial Liquid Glass on iOS — the
mini player "docked at the bottom of the screen" became translucent while
"the bottom tab bar kept Spotify's own design" (secondary source, but recent
and specific: <https://liquidglassdesign.com/blog/spotify-liquid-glass>).

## 4. YouTube Music mobile

**Full-width dark band docked directly on top of the bottom nav (no gap);
art square flush left; play + next at right (or Cast + play after the 2023
redesign); thin progress line at the band's bottom.**

research-imgs/ytm-miniplayer-current.jpg and
research-imgs/ytm-miniplayer-new.jpg (source: 9to5Google,
<https://9to5google.com/2023/06/29/new-youtube-music-miniplayer/> — "YouTube
Music rolls out miniplayer redesign with permanent Cast button"). Visible:

- One full-width band above the [Home | Explore | Library] nav, **art
  thumbnail flush at the left edge**, title + artist beside it, **two glyphs
  at the right** (before the redesign: play + next; after: Cast + play/pause,
  with next/back moved to a swipe gesture — the same swipe-while-docked idea
  Apple shipped in iOS 26.1).
- A thin white progress line along the bottom of the band, and the band is
  directly adjacent to the nav strip (no gap).
- **The empty state is revealing:** with nothing playing the band still shows
  a **gray square placeholder in the art slot** plus "Nothing is playing"
  (research-imgs/ytm-miniplayer-new.jpg) — the art slot is structural, not a
  decoration that appears when convenient.

## 5. AnkiDroid (text-content auto-advance, no art)

Already established in `research-auto-advance-ux.md`; re-verified in source
today. The auto-advance trigger is a **menu item in the reviewer's top
overflow menu**: `ViewerAction.TOGGLE_AUTO_ADVANCE`; the icon flips between
`ic_fast_forward_outlined` and `ic_fast_forward` (filled) while running, the
title between "Enable auto advance" / "Disable auto advance"
(`ReviewerMenu.kt` lines 140-152,
<https://github.com/ankidroid/Anki-Android/blob/main/AnkiDroid/src/main/java/com/ichi2/anki/ui/windows/reviewer/ReviewerMenu.kt>).
The reviewer's chrome is a standard toolbar (`activity_reviewer.xml` →
`include_toolbar`) whose visible actions live in the overflow; timing is not
near the control (Deck Options, synced). So AnkiDroid = **control embedded in
existing chrome**, invisible until you open the menu.

## 6. Kindle read-aloud (Assistive Reader) — text content, no art

**A bottom player row docked at the bottom of the reader screen: text label
at left (no art), speed, rewind-30, and a circular outlined PLAY button at
the right edge. During playback the top toolbar hides.**

research-imgs/kindle-assistive-reader.png (three-screen composite; source:
Good e-Reader's Kindle Assistive Reader hands-on — found via image search,
original article at goode-reader.com). What is visible:

- Screen 1: at the **bottom of the page**, one row: left-aligned label
  "Assistive Reader / 11h 5m left in book", a "1x" speed control, a
  rewind-30 icon, and a **circular outlined play button** at the far right.
  The page footer (Page 59 of 425) sits just above it. **No album art, no
  card, no pill — just a thin row pinned to the bottom edge.**
- Screen 3: tapping the speed control opens a bottom sheet (Speed slider +
  preset chips 0.7 / 1 / 1.2 / 1.5 / 1.7 / 2). Timing lives in a sheet, one
  tap from the player, not in Settings.
- Screen 2: while reading aloud, **the top toolbar (and its six actions)
  disappear** — playback goes immersive; tapping the page brings chrome back.
- Kindle's **top toolbar when shown carries six controls** (back, contents,
  search, notebook, Aa, overflow) — but they are transient and hide during
  playback.

Amazon's official help (fetched and quoted in `research-auto-advance-ux.md`;
page is bot-blocked to plain curl today): enable via the Aa menu, then
"Play, pause, control the reading speed, or rewind by 30 seconds using the
**player controls at the bottom of the screen**."
(<https://www.amazon.com/gp/help/customer/display.html?nodeId=TdHCSXXqsdhaVKt3Tc>).

## 7. Quizlet Flashcards — text-content autoplay

**Play is a labeled text-button in a vertical stack [Play] [Shuffle]
[Options] at the bottom of the left sidebar — not in a top bar, not a
miniplayer.**

research-imgs/quizlet-like-flashcard.png (Quizlet web Flashcards mode;
screenshot via eclaireMFL blog, found through image search). Visible: card
area at right with speaker + star (+ edit) icons at the card's top-right and
prev/next circles below the card; the **Play button sits at the bottom of the
sidebar**, third in a stack under Shuffle and Options. The official help
describes it in one line: "If you don't review your cards manually, select
Play to cycle through automatically."
(<https://help.quizlet.com/hc/en-us/articles/360030988091-Studying-with-Flashcards>;
cited from the previous research doc — the page is bot-blocked to plain curl
today).

---

## Findings table

| App (state) | Accessory surface | Sits where | Width vs nav | Leading edge | Trailing edge | Progress | Screenshot |
|---|---|---|---|---|---|---|---|
| Apple Music iOS 18 (rest) | flat rounded-rect card, small radius | above tab bar, small gap | ~full width (same margins as content); tab bar is an unboxed icon strip | album-art square | play + fast-forward (2 glyphs) | none | ai-music-home-ios18-vs-26.jpg |
| Apple Music iOS 26 (rest) | glass capsule pill, slightly taller than nav | above tab-bar capsule, clear gap | WIDER than the tab-bar capsule (spans over the Search circle's column) | album-art square + title/artist | play + fast-forward (2 glyphs) | none | ms-legibility-tabbar.png; ms-glass-across-os.png; ai-music-home-ios18-vs-26.jpg |
| Apple Music iOS 26 (scrolled) | miniplayer merged INTO the nav row | inline between [Home] and [Search] | narrower than full row (sits between two buttons) | album-art square | play (1–2 glyphs) | none | ai-music-scroll-ios26.jpg; idrop-ios261-music-miniplayer.jpg; 9to5-music-swipe.jpg |
| Spotify (rest) | full-width flat rectangle, album-color tint | directly above nav, thin gap | exactly nav width, edge-to-edge | album-art square bleeding to edge | connect + heart + play/pause (3) | thin line at bar bottom | spotify-9t5g-1.png; spotify-9t5g-2.png |
| YouTube Music (rest) | full-width dark band | docked directly on nav, no gap | exactly nav width, edge-to-edge | album-art square (gray placeholder when empty) | play + next, or Cast + play (2) | thin line at band bottom | ytm-miniplayer-current.jpg; ytm-miniplayer-new.jpg |
| Kindle Assistive Reader (playing) | thin bottom player row | pinned to screen bottom (reader has no nav bar) | full width | text label "Assistive Reader / time left" — NO art | speed, rewind-30, circular outlined play | none (time-left text instead) | kindle-assistive-reader.png |
| Quizlet Flashcards (web) | sidebar button stack | bottom of left sidebar | n/a | "▶ Play" text label | — | progress bar at sidebar top | quizlet-like-flashcard.png |

---

## Synthesis

### A. Is "two same-width pills stacked with a small gap" in Apple Music's design anywhere?

**No — verified against six screenshots.** Apple Music has exactly one
accessory surface above the nav bar, and it is never the same shape/width as
the nav below it:

- iOS 18: a rounded-rect content card above an *unboxed* icon strip — two
  categorically different shapes (research-imgs/ai-music-home-ios18-vs-26.jpg).
- iOS 26 resting: one glass pill whose width is *wider* than the tab-bar
  capsule (it spans the full accessory row, over the Search circle's column),
  with a gap — pill above capsule+circle, not pill above pill
  (research-imgs/ms-legibility-tabbar.png, ms-glass-across-os.png).
- iOS 26 scrolled: the surfaces **merge into one row** — the opposite of
  stacking (research-imgs/ai-music-scroll-ios26.jpg,
  idrop-ios261-music-miniplayer.jpg, 9to5-music-swipe.jpg).
- Apple's own docs describe only two placements: accessory *above* the tab
  bar, or *inline* with it (tabViewBottomAccessoryPlacement). Nothing about
  stacked duplicate surfaces.

A designer who ships "two same-width pills stacked with a gap" has invented a
pattern that exists nowhere in the product being cited — which explains the
user's reaction: it reads as two navigation bars, which is precisely what the
miniplayer grammar avoids.

### B. What makes the miniplayer read as CONTENT rather than a second nav bar? Is the album art load-bearing?

**Yes — the album-art square is the load-bearing element**, and the evidence
is unusually direct:

- It is the first element at the leading edge and the largest object in the
  bar in Apple Music (both eras), Spotify, and YouTube Music (all screenshots
  above).
- **YouTube Music keeps a gray square placeholder in the art slot even when
  nothing is playing** (research-imgs/ytm-miniplayer-new.jpg) — the slot is
  structural to the layout, not decoration.
- The trailing edge carries only 2 transport glyphs (Apple, YT Music) or 3
  (Spotify: connect/like/play). Text is one or two short lines. Nothing in
  the bar looks like a tab item or a label-with-glyph nav pair.
- Secondary cues: Spotify tints the bar with the album color
  (spotify-9t5g-2.png); Spotify/YT Music run a thin progress line along the
  bar's bottom edge — a media affordance nav bars never have.

Remove the art slot and put text at the left with a control at the right, and
what remains is a strip of text+glyph in a bar-shaped container — visually
indistinguishable from the nav row below it. That is what the rejected
proposal produced. **A media-style accessory bar without a media slot needs
some other dominant asymmetric element** (Kindle solves it with a large
circular play button at the right edge plus a two-line status label at left —
research-imgs/kindle-assistive-reader.png).

### C. For a flashcard app (text cards, no art), which evidenced pattern fits?

Evidence per option:

- **(i) transient full-width accessory** — supported. Kindle: while read-aloud
  is active, a full-width bottom player row appears, text label left,
  circular play right, and it is the only extra chrome (toolbar hides);
  stopping it removes the row (kindle-assistive-reader.png). Spotify/YT
  Music show the full-width docked-bar grammar for running media without art
  slots being pills. Apple's API defines the accessory as a *persistent
  while content exists* surface that alternates between above/inline — the
  "transient" part (present only while auto-advance runs) matches Kindle's
  behavior and word-drill's state model better than Apple's persistence.
- **(ii) separate mini-card with a media slot** — NOT supported. There is no
  evidenced instance of an art-style accessory without art; faking the slot
  with an icon would be mimicry without meaning, and the card shape is what
  produced the "second nav bar" reading in the rejected attempts.
- **(iii) control embedded in existing chrome** — supported as the *inactive*
  state. AnkiDroid buries the toggle in the reviewer overflow menu
  (ReviewerMenu.kt); Quizlet puts Play in a sidebar stack
  (quizlet-like-flashcard.png). Both are discoverable only if you look, which
  is why media apps surface a dedicated control while playing instead.
- **(iv) something else the evidence shows: immersive-when-playing.** Both
  Kindle (toolbar hides) and Apple Music (tab bar collapses, accessory
  inlines) *reduce* chrome while playback runs — playback mode earns its own
  surface by taking chrome away elsewhere.

**Fit for the Drill screen (top bar = book name + counter + skip + lock;
bottom half empty; capsule nav floating):** keep the previously recommended
FAB-style circular play/pause (sized with the 30 dp lock button) as the
*start* affordance in the empty bottom half; once auto-advance is running,
the evidenced move is either (a) keep just the button and switch it to
pause + thin progress ring, or (b) morph it into a single full-width,
inset bottom accessory row — "Auto 5 s · 12/40" text at left, pause glyph at
right — docked above the capsule nav with a gap, exactly the Music/Spotify
grammar minus the art slot, disappearing on stop. What is NOT evidenced:
a second pill card that mirrors the nav capsule's width and shape.

### D. How many actions do the referenced apps' top bars carry on their drill/reader screens?

| Screen | Top-bar actions | Evidence |
|---|---|---|
| Apple Music Home | 1 (avatar; page title at left) | ai-music-home-ios18-vs-26.jpg |
| YouTube Music | 2–3 (search, avatar; Cast before 2023, then History in overflow position) | 9to5google 2023 article (text) |
| AnkiDroid reviewer | 1 visible (overflow menu; all actions incl. auto-advance inside it) | ReviewerMenu.kt + activity_reviewer.xml |
| Kindle reader (toolbar shown) | 6 (back, contents, search, notebook, Aa, overflow) — **hidden during playback** | kindle-assistive-reader.png |
| Quizlet Flashcards | 0 in a top app bar; 3 small icons on the card (edit, speaker, star) + sidebar stack | quizlet-like-flashcard.png |

Reading apps that do carry a busy top bar (Kindle: 6) **hide it during
playback**, and Material 3 caps app bars at "1–2 essential actions"
(m3.material.io/components/app-bars/overview, cited in
research-auto-advance-ux.md). So a third persistent text action in the Drill
top bar is defensible by neither the platform guidance nor the reference
apps; the references move mode controls to the bottom bar, a sidebar, the
overflow, or a dedicated playback surface.

---

## Source list

Screenshots (downloaded into `research-imgs/`, viewed 2026-11-16):

- ai-music-home-ios18-vs-26.jpg, ai-music-scroll-ios26.jpg — AppleInsider,
  "Apple Music iOS 26 vs iOS 18":
  <https://appleinsider.com/inside/apple-music/vs/apple-music-ios-26-vs-ios-18-automix-liquid-glass-and-lyrics-in-your-language> (via web.archive.org)
- ms-legibility-tabbar.png, ms-glass-across-os.png — MacStories,
  "iOS and iPadOS 26: The MacStories Review", Design chapter:
  <https://www.macstories.net/stories/ios-and-ipados-26-the-macstories-review/2/>
- idrop-ios261-music-miniplayer.jpg — iDrop News, "iOS 26.1 Is Here":
  <https://www.idropnews.com/news/ios-261-is-here-with-subtle-changes-youll-actually-notice/255083/>
- 9to5-music-swipe.jpg — 9to5Mac, "iOS 26.1 beta 1 changes":
  <https://9to5mac.com/2025/09/22/ios-26-1-beta-1-changes-everything-new/>
- spotify-9t5g-1.png, spotify-9t5g-2.png — 9to5Google,
  "Spotify miniplayer now matches colors on Android":
  <https://9to5google.com/2021/07/28/spotify-miniplayer-android-test/>
- ytm-miniplayer-current.jpg, ytm-miniplayer-new.jpg — 9to5Google,
  "YouTube Music rolls out miniplayer redesign with permanent Cast button":
  <https://9to5google.com/2023/06/29/new-youtube-music-miniplayer/>
- kindle-assistive-reader.png — Good e-Reader Kindle Assistive Reader
  hands-on (image located via image search; article: goode-reader.com)
- quizlet-like-flashcard.png — Quizlet web Flashcards mode screenshot via
  eclaireMFL (image located via image search)

Documentation and prose sources:

- SwiftUI `tabViewBottomAccessory(content:)`:
  <https://developer.apple.com/documentation/swiftui/view/tabviewbottomaccessory(content:)>
- `tabViewBottomAccessoryPlacement` ("accessory appears above it / displays
  inline"):
  <https://developer.apple.com/documentation/swiftui/environmentvalues/tabviewbottomaccessoryplacement>
- WWDC25 session 323, "Build a SwiftUI app with the new design":
  <https://developer.apple.com/videos/play/wwdc2025/323/>
- Spotify Liquid Glass partial adoption (secondary, Oct 2026):
  <https://liquidglassdesign.com/blog/spotify-liquid-glass>
- AnkiDroid `ReviewerMenu.kt` (TOGGLE_AUTO_ADVANCE, lines 140-152):
  <https://github.com/ankidroid/Anki-Android/blob/main/AnkiDroid/src/main/java/com/ichi2/anki/ui/windows/reviewer/ReviewerMenu.kt>
- AnkiDroid reviewer layout:
  <https://github.com/ankidroid/Anki-Android/blob/main/AnkiDroid/src/main/res/layout/activity_reviewer.xml>
- Amazon Kindle Assistive Reader help (quoted via
  research-auto-advance-ux.md; bot-blocked to plain fetch on 2026-11-16):
  <https://www.amazon.com/gp/help/customer/display.html?nodeId=TdHCSXXqsdhaVKt3Tc>
- Quizlet help, "Studying with Flashcards" (quoted via
  research-auto-advance-ux.md; bot-blocked to plain fetch on 2026-11-16):
  <https://help.quizlet.com/hc/en-us/articles/360030988091-Studying-with-Flashcards>
- Material 3 app bars ("1–2 essential actions") — cited from
  research-auto-advance-ux.md: <https://m3.material.io/components/app-bars/overview>

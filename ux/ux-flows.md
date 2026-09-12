# Viewer felt-moment audit (U0)

Status: first draft, branch `viewer-perf-recon`. Evidence: code inspection with
`file:line` refs, production harness timings from
`frontend/editor/.perf-local/BASELINE.md` (Chromium, localhost, Apple Silicon —
treat as best-case; slower devices move every row one tier worse). No human
trial or instrumented sessions exist yet (U7 not implemented); rows marked
"unverified" are code-inspection judgements, not evidence.

Tier definitions (Nielsen): **T1** <100 ms show nothing; **T2** 100 ms–1 s
delayed indicator only (≥200 ms), never a flashing spinner; **T3** 1–10 s
loading becomes UI (content-shaped skeleton or spinner); **T4** >10 s
determinate progress + stage text + cancel/degrade.

## Flow table

| # | flow | intent | truth | today | tier (this machine) | tier-gap |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | open-doc (any size) | click a file | first readable page | one unconditional `Loader` + "Loading PDF Engine..." for engine init, then "Preparing document..." while the document loads (`LocalEmbedPDF.tsx:1207`, `:1387`). No skeleton, no progress, no cancel; at 15 s only a warning string appears (`:1152-1220`) | measured 0.18–0.35 s (500 p), 0.64–0.85 s (40 MB), 0.68–1.12 s (155 MB) → T2 on this machine; large scans on slow devices reach T3/T4 | **T2:** no delayed indicator (spinner shows instantly); **T3:** no content-shaped skeleton or determinate stage; **T4:** no determinate progress / cancel / degrade |
| 2 | reopen / continue reading | reopen a doc | same page, zoom, mode | **not implemented:** reopen starts at the cover. A resume feature existed briefly and was reverted: its restore raced the initial zoom and could overwrite the stored position with page 0. This is a document processor, not a reading app; per-file reading history stays out of scope unless a product decision says otherwise | n/a | none |
| 3 | open-password | open an encrypted doc | enter password and continue | dedicated locked state + `Unlock` button → prompt (`EmbedPdfViewer.tsx:1202-1217`) | n/a | core flow met; prompt autocomplete/remember semantics unverified |
| 4 | open-corrupt / failing page | open a broken file | calm message + next step | non-PDF formats have a dedicated viewer/banner (`NonPdfViewer.tsx`); engine failure renders the raw `error.message` (`LocalEmbedPDF.tsx:1240`). No per-page failure/retry path found | n/a | human-copy gap; partial-broken docs unverified |
| 5 | scroll-reading | scroll a long doc | stay anchored, no jank | continuous scroll default; buffer 4 pages ≥4 GB deviceMemory else 2 (`LocalEmbedPDF.tsx:975`, `:1004`); scroll-end 150 ms (`:997`). No explicit anchor `{page,yFraction}` save/restore | n/a | anchoring under eviction churn unverified; no scroll-freeze measurement |
| 6 | zoom | Ctrl+wheel / ± / fit | zoom on the focal point, predictable stops | buttons + Ctrl+±, Ctrl+0 = FitWidth (`EmbedPdfViewer.tsx:424-437`); range 0.2–5.0, no labeled stops (`LocalEmbedPDF.tsx:1047-1048`) | n/a | no human labels (`Fit`, `100%`…); focal-point behavior unverified |
| 7 | page-jump | type "p. 12" / "page 12 of 300" | jump to page 12 | `Number(value)` only; `"p. 12"` → `NaN`, silently ignored (`PdfViewerToolbar.tsx:198`); no preview while typing | n/a | tolerant input + target preview missing |
| 8 | search | Ctrl+F, type ≥3 chars | hits highlight and navigate | Ctrl+F opens/focuses (`EmbedPdfViewer.tsx:449-459`); 300 ms debounce, no minimum length (`SearchInterface.tsx:55-88`); result state polled at 200 ms (`:122`); Enter/Esc/↑↓ handled (`:127-139`) | n/a | debounce + min-length + polling vs spec; highlight UX unverified |
| 9 | thumbnails | scan the sidebar | thumbnails appear progressively | unloaded tiles show a plain text "Loading..." box (`ThumbnailSidebar.tsx:299`) | tier 3-ish | no skeleton/shimmer; flashes on fast docs |
| 10 | keyboard | operate without mouse | full navigation | Home/End/PgUp/PgDn, Ctrl+F/S/Z/Y/P/±/0, Esc (`EmbedPdfViewer.tsx:384-516`) | n/a | no `?` legend, no `/` to search; arrow pan relies on native scroll |
| 11 | touch | pan/pinch/tap-select | native feel | pan config + touch-selection regressions are covered by specs; no momentum/haptics customization found | n/a | "never hijack momentum" unverified (no passive-listener audit yet) |
| 12 | reduced-motion | prefers-reduced-motion | no motion | global CSS collapses animation/transition durations (`tokens.css:370-377`) | n/a | met |
| 13 | focus theft / interruptions | scroll while background work runs | nothing steals focus or shifts layout | viewer `focus()` only on search/comments inputs (`SearchInterface.tsx:38,45`, `CommentsSidebar.tsx:368`); no viewer toasts; no prefetch/index background work exists yet | n/a | low risk today; becomes a hazard when prefetch/search-index work lands |
| 14 | offline / flaky | open a cached doc offline | cached pages render; silent retry | no offline badge or reconnect-retry path found | n/a | unverified; expected gap |

## Ranked gaps (worst gap × frequency)

1. **open-doc loading truth** — every open, worst unmet tier (T4 on stress
   corpus, T2 already violated on fast docs: instant spinner). Code fixable
   without product semantics for the delay/stage parts; cancel/degrade needs a
   decision.
2. **page-jump tolerant input + preview** — small, self-contained, visible.
3. **search tuning** (150 ms, ≥3 chars, drop polling) — small, self-contained.
4. **thumbnail loading placeholders** — cosmetic, tier-3 only.
5. **error/offline copy and per-page retry** — real users, low frequency.

## Rude-interruption audit

- No toasts or `focus()` calls fire during viewer scroll today; background
  prefetch/indexing does not exist yet, so there is nothing to reflow mid-gesture.
- The one anti-pattern found: `SearchInterface` polls its result state with
  `setInterval(…, 200)` while visible (`SearchInterface.tsx:120-125`) instead of
  subscribing — main-thread work every 200 ms during reading, and a source of
  layout churn risk.
- Spinner → content transition (`LocalEmbedPDF.tsx:1207`) swaps the whole viewer
  body; if the toolbar appears at a different size, that is a layout shift at the
  exact moment of first paint. Fix pairs with gap 1.

## Measurement (U7)

Not implemented. There is no session recorder; count rage clicks, swipe thrash,
quit-within-3 s, and dwell with a new recorder when the next gap is implemented.

## Next fix (needs a decision before coding)

**Gap 1 (open-doc loading truth):**
- <200 ms: render nothing (removes today's spinner flash on warm small docs).
- 200 ms–1 s: delayed quiet indicator only.
- 1–10 s: page-aspect skeleton in the document's real aspect ratio (cover
  aspect is known from the first metadata read) + stage text
  ("Preparing document…", "Rendering page 1…").
- >10 s: add real progress where achievable (bytes parsed / pages prepared) and
  a cancel affordance. **Decision needed:** what "cancel" does — abort the
  current open and return to the file list, or keep the doc loading in the
  background and only close the viewer?
- Also fix the label: engine-ready and document-parsing are different stages.
- Verification: Phase 1 rails on 40 MB/155 MB + a throttled 4× run; reduced-
  motion respected.

## Reverted: U1 reading continuity

A reopen-continuity feature persisted page/offset/zoom in a device-local
IndexedDB store and restored it with a "Resumed where you left off" toast. It
was reverted from the branch:

- The restore raced the viewer's initial auto-zoom/layout. Reproduced 3/3 runs:
  the toolbar read page 3 while the viewport painted page 1, and a capture then
  overwrote the stored record with page 0 — so the next reopen was guaranteed
  wrong. The screenshot in the validation report shows the cover page under a
  "Resumed where you left off" toast.
- The official spec passed because it asserted element visibility, which is true
  for a mounted-but-off-screen page.
- Product call: a document processor should not silently track reading history;
  any future attempt needs an explicit flag and a decision, not a hard-coded
  behavior.

The shimmery page skeleton that shipped alongside it was removed too: it flashed
in and out before the loader label and the content, which reads as noise rather
than progress.


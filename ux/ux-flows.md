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
| 1 | open-doc (any size) | click a file | first readable page | one unconditional `Loader` + "Loading PDF Engine..." for engine init **and** document parse (`LocalEmbedPDF.tsx:1214-1237`, engine state `:1095`). No delay, no skeleton, no stage text, no progress, no cancel; at 15 s only a warning string appears (`:1157-1169`) | measured 0.18–0.35 s (500 p), 0.64–0.85 s (40 MB), 0.68–1.12 s (155 MB) → T2 on this machine; large scans on slow devices reach T3/T4 | **T2:** spinner flashes instantly and names the wrong stage; **T3:** no content-shaped skeleton or stage; **T4:** no determinate progress / cancel / degrade |
| 2 | reopen / continue reading | reopen a doc I was reading | same page, zoom, mode | nothing persists page/zoom/scroll/mode. `workbenchSession.ts` restores open files, selection and view only (`:15-24`, `:130-155`); no scroll or zoom record anywhere | truth is page 1 / FitWidth every time | **P0 continuity defect** (spec: nothing outranks) |
| 3 | open-password | open an encrypted doc | enter password and continue | dedicated locked state + `Unlock` button → prompt (`EmbedPdfViewer.tsx:1200-1220`) | n/a | core flow met; prompt autocomplete/remember semantics unverified |
| 4 | open-corrupt / failing page | open a broken file | calm message + next step | non-PDF formats have dedicated viewers/banner (`NonPdfViewer`, `:1171-1208`); engine failure renders the raw `error.message` (`LocalEmbedPDF.tsx:1239-1256`). No per-page failure/retry path found | n/a | human-copy gap; partial-broken docs unverified |
| 5 | scroll-reading | scroll a long doc | stay anchored, no jank | continuous scroll default; buffer 4 pages ≥4 GB deviceMemory else 2 (`LocalEmbedPDF.tsx:986`, `:1015`); scroll-end 150 ms (`:1008`). No explicit anchor `{page,yFraction}` save/restore | n/a | anchoring under eviction churn unverified; no scroll-freeze measurement |
| 6 | zoom | Ctrl+wheel / ± / fit | zoom on the focal point, predictable stops | buttons + Ctrl+±, Ctrl+0 = FitWidth (`EmbedPdfViewer.tsx:424-437`); range 0.2–5.0, no labeled stops (`LocalEmbedPDF.tsx:1056-1060`) | n/a | no human labels (`Fit`, `100%`…); focal-point behavior unverified |
| 7 | page-jump | type "p. 12" / "page 12 of 300" | jump to page 12 | `Number(value)` only; `"p. 12"` → `NaN`, silently ignored (`PdfViewerToolbar.tsx:196-201`); no preview while typing | n/a | tolerant input + target preview missing |
| 8 | search | Ctrl+F, type ≥3 chars | hits highlight and navigate | Ctrl+F opens/focuses (`EmbedPdfViewer.tsx:449-459`); 300 ms debounce, no minimum length (`SearchInterface.tsx:55-88`); result state polled at 200 ms (`:120-125`); Enter/Esc/↑↓ handled (`:127-139`) | n/a | debounce + min-length + polling vs spec; highlight UX unverified |
| 9 | thumbnails | scan the sidebar | thumbnails appear progressively | unloaded tiles show a plain text "Loading..." box (`ThumbnailSidebar.tsx:285-302`) | tier 3-ish | no skeleton/shimmer; flashes on fast docs |
| 10 | keyboard | operate without mouse | full navigation | Home/End/PgUp/PgDn, Ctrl+F/S/Z/Y/P/±/0, Esc (`EmbedPdfViewer.tsx:384-516`) | n/a | no `?` legend, no `/` to search; arrow pan relies on native scroll |
| 11 | touch | pan/pinch/tap-select | native feel | pan config + touch-selection regressions are covered by specs; no momentum/haptics customization found | n/a | "never hijack momentum" unverified (no passive-listener audit yet) |
| 12 | reduced-motion | prefers-reduced-motion | no motion | global CSS collapses animation/transition durations (`tokens.css:370-377`) | n/a | met |
| 13 | focus theft / interruptions | scroll while background work runs | nothing steals focus or shifts layout | viewer `focus()` only on search/comments inputs (`SearchInterface.tsx:38,45`, `CommentsSidebar.tsx:368`); no viewer toasts; no prefetch/index background work exists yet | n/a | low risk today; becomes a hazard when prefetch/search-index work lands |
| 14 | offline / flaky | open a cached doc offline | cached pages render; silent retry | no offline badge or reconnect-retry path found | n/a | unverified; expected gap |

## Ranked gaps (worst gap × frequency)

1. **open-doc loading truth** — every open, worst unmet tier (T4 on stress
   corpus, T2 already violated on fast docs: instant spinner + wrong stage
   label). Code fixable without product semantics for the skeleton/stage/delay
   parts; cancel/degrade needs a decision.
2. **reopen continuity** — every reopen of a previously-read doc; P0 by the
   spec. Persistence scope, TTL and resume-copy need decisions.
3. **page-jump tolerant input + preview** — small, self-contained, visible.
4. **search tuning** (150 ms, ≥3 chars, drop polling) — small, self-contained.
5. **thumbnail loading placeholders** — cosmetic, tier-3 only.
6. **error/offline copy and per-page retry** — real users, low frequency.

## Rude-interruption audit

- No toasts or `focus()` calls fire during viewer scroll today; background
  prefetch/indexing does not exist yet, so there is nothing to reflow mid-gesture.
- The one anti-pattern found: `SearchInterface` polls its result state with
  `setInterval(…, 200)` while visible (`SearchInterface.tsx:120-125`) instead of
  subscribing — main-thread work every 200 ms during reading, and a source of
  layout churn risk.
- Spinner → content transition (`LocalEmbedPDF.tsx:1214`) swaps the whole viewer
  body; if the eventual skeleton/toolbar appear at different sizes, that is a
  layout shift at the exact moment of first paint. Fix pairs with gap 1.

## Measurement gaps (U7)

Not implemented. The Phase 1 harness (`perf-baseline.local.spec.ts`) measures
`tStart → tFirstPage` but logs no intent event and no tier/stage boundaries; it
also does not count rage clicks, swipe thrash, quit-within-3 s, or dwell
moments. Any Phase 2 fix should add intent→truth marks behind `?uxstudy=1`
(e.g. `ux:doc-open:start`, `ux:first-page:visible`, `ux:doc-open:cancel`) and
feed them into the existing `[PERF-BASELINE]` JSON rather than a new pipeline.

## Proposed first fix (needs a decision before coding)

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
  motion respected; one sampled intent→truth session.

**Gap 2 (continuity), if preferred:** persist
`(docFingerprint, pageIndex, yFractionWithinPage, zoomMode, zoom)` device-local,
restore within ±1/4 page, non-blocking "Resumed where you left off — [Go to
start]" toast, in-memory/idb only (never localStorage for content). Decisions:
storage TTL, per-doc vs global zoom inheritance, and cross-device behavior
(none for this pass).

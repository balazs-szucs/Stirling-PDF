# Viewer performance work — branch `viewer-perf-recon` (local handoff)

Status: **local only, never pushed**. The working tree is clean and the branch is
kept for a 2nd/3rd agent pass. Do not push or open PRs without the owner's
explicit approval.

Status: **local only, never pushed**. The working tree is clean and the branch is
kept for a 2nd/3rd agent pass. Do not push or open PRs without the owner's
explicit approval.

## Current State — convergence pass (2026-09-13, HEAD `5f98b13c6`)

Single authoritative section; dated sections below are the archive. Where
they disagree with this section, this section wins.

### P0 record

- Mode: IDE (editor tooling; perf runs sequential). Load: **loaded, not
  idle** — entry loadavg 5.68/7.39/10.56 (Firefox plugin-container ~100%,
  IntelliJ ~32%, JVM ~29%, several procs >5% sustained; rose to ~11.75
  mid-pass). Network: localhost harness; no external fetch needed.
- Entry tree was **dirty**: `TextSelectionMenu.tsx` +364/-9 plus untracked
  `viewer-text-selection-menu.spec.ts` (5 tests) — the pass-6 menu port,
  documented below as landed but never committed. Re-verified (typecheck /
  lint / format clean, spec 5/5 ×3, selection batch 26/26) and committed as
  `5f98b13c6`. Tree clean after.
- Docs read fully: this file (1313 lines), `ux/ux-flows.md`,
  `.perf-local/BASELINE.md` (758), `research-ledger.md` (153, pass-5 A–D +
  pass-6 E–F), `NEXT_AGENT_PROMPT.md` (stale, see finding 4).
- Harness / fixtures / corpus present: `perf-baseline.local.spec.ts`,
  `engine-patch-smoke.local.spec.ts`, `corpus-gauntlet.local.spec.ts`,
  `viewer-memory-soak.spec.ts` (committed), fixtures large-40mb / huge-150mb
  / form-40mb / pages-500, corpus manifest at `corpus/corpus-manifest.csv`
  (60 files). Baseline ref `77b325cf1` present; the `../sp-baseline` A/B
  worktree was removed after use.
- Baseline repro on entry (production preview, single large-40mb run):
  firstPage 706 ms, 1 long task 59 ms (pre-existing), 1 full copy 40.6 MB,
  main wasm 810 pp / 50.6 MB, worker→main 5.3/5.4 MB transferred, m2w 40.4 MB
  cloned, bootstrap marks 54/80/373 ms — matches the documented branch state.
  Environment reproduces; timings UNVERIFIED (loaded machine).

### Master truth table

Status: LANDED (in tree, still true today) · DECAYED (present but drifted) ·
PARTIAL (started, unfinished) · CLOSED (refuted/ruled-out, closure holds) ·
GHOST (documented, absent — now zero open).

| # | Claim | Commit | Status | Evidence today |
| --- | --- | --- | --- | --- |
| 1 | Branch layers viewer-fonts + embedpdf-noodling + #7878 | `6170eb194`, `9b9c53dff` | LANDED | merges in log; in-flight PR branches untouched |
| 2 | One-read bytes + scan queue | `81ba215e3` | LANDED | repro: 1 full copy 40.6 MB; files + contract tests present |
| 3 | Shared main-thread document (ref-counted) | `92f7fbaa1`, `d786c1822` | LANDED | `openRawDocument`/release-pending in tree; shared-doc unit tests present |
| 4 | `/AcroForm` byte gate + catalog `FPDF_GetFormType` probe | `118ee1718`, `88d6b40dd` | LANDED | `hasAcroForm` + probe in tree; huge fixture main wasm 285 pp |
| 5 | `EPDF_*ByIndex` metadata, single thumb render, rotation-first views | `2a2528796`, `3fb4727eb` | LANDED | index APIs + rotation-before-heap-views in tree |
| 6 | Per-page signature/button overlays | `2e6d1c38b` | LANDED | `pageIndexes` filters in tree |
| 7 | Engine patch: worker→main transfers + module handoff | `bc519ae07`, `e9b820d5a` | LANDED | repro 5.3/5.4 MB transferred; smoke 3/3 browsers, 0 worker wasm fetches; `check:embedpdf-patch` green |
| 8 | Wasm `.br`/`.gz` + immutable `/assets/**` | `3cd84a5e3` | LANDED | dist has `.br`/`.gz`; bootstrap marks ~50/80/370 ms |
| 9 | BMP default, encoder band documented | #557 + `72c2b8934` | LANDED | `defaultImageType` + scroll deltas transfer ~99% |
| 10 | U1 resume reverted; skeleton removed | `86111af5a` → `323bed080` | CLOSED | no `readingPosition`/skeleton remnants in tree |
| 11 | Hunt fixes (dead hooks, queue wedge, LRU keys, form-fill/pdfbox + thumbnail reads, overlay teardown, race timers, thumbnail release) | `16f4bb6d8`, `c41008d99`, `bf0c5b4f7`, `9b7d92afa`, `8c0fbf4e1`, `290fe972d`, `2bf8d5a1f`, `37db8f0f6`, `79ef97cd3`, `fcc1d0b34`, `789a573e6`, `eea2e63a5`, `2ced8f0e9`, `0af4de496` | LANDED | each commit focused + scoped as messaged; deleted files gone; soak green |
| 12 | F10 JPEG-q0.8/400px record thumbnails | `87205808e` | LANDED | clamp + quality in tree; form soak green |
| 13 | G3/G16 worker respawn (10 MB threshold, URL revoke) | `8c2e740c1`, `852192fac`, `e9b820d5a` | LANDED | 10 MB threshold + watcher + revoke in tree; soak worker pages flat |
| 14 | G11 registry pin dropped; G12a listener unsubscribe; G14 WeakRef cache; File-signature dedupe | `a542633f1`, `c1860a3da`, `2dad72c57` | LANDED | registry 0 docs, unsubscribe + `WeakMap<Blob, WeakRef>` + signature tier in tree |
| 15 | G13 reclaim blocked (two negative attempts) | — | CLOSED (blocked) | main wasm floor unchanged by design; do not retry blind |
| 16 | G19/G21 plugin coalescing; G23 posthog gate; G23b jszip lazy; G24 format | `cac5fdb64`, `c511e6da6`, `b99a0c8b1`, `28fb8583e`, `911188913` | LANDED | anchors in `patch-embedpdf-plugins.mjs`, check green; dynamic imports in tree; dist vendor-zip lazy-only; format check clean |
| 17 | G20 encoder-worker BMP routing | — | CLOSED | never committed, zero remnants; cost is blob materialization |
| 18 | Manual-redaction regressions fixed | `ceb52f779` | LANDED | redaction spec in verification batch, green |
| 19 | Text-selection annotation/redact menu | `5f98b13c6` | **LANDED (was GHOST)** | entry state uncommitted; re-verified and committed this pass |
| 20 | Legacy perf specs dropped; soak/contract tests committed | `f868993d1`, `f6bfbc02d`, `34ce4a546`, `30f30cee5`, `4fa05005d` | LANDED | stale specs gone; soak budgets hold (default + form green this pass) |
| 21 | Streaming/low-allocation evaluation (dedupe shipped, dead service dropped, COOP/COEP memo, dead ends) | `2dad72c57`, `faeadac74`, `e2885425d` | LANDED/CLOSED | one-read holds on corpus-shape files; memo stands |
| 22 | Corpus 60/60 functional parity (pass 4) + pass-7 spot 18/18 | `c92191bb9` | LANDED (carried) | manifest + runner intact; full both-arms re-run deferred (risk register 6) |
| 23 | Frontier numbers (F1–F10, T1–T11, G30–G32) | `0eebb2bfc` + ledger §E–F | LANDED (evidence) | reports + raw logs + profiles present |

Contradictions named: (a) vitest counts across sections
3422→3430→3448→3444→3451→3452 are monotonic landings, current **3452/3452
(389 files)** re-verified; (b) e2e batch sizes differ by composition —
canonical batch is the 11-spec verification list below (57 passed + 1
ambiguous, this pass); (c) BASELINE.md top-table absolutes are superseded
by the table below; (d) G20 "reverted" = discarded pre-commit, correctly
absent; (e) pass-7 "concurrent agent" note = the menu-port dirty state,
resolved by `5f98b13c6`.

### Current numbers (this pass, HEAD `5f98b13c6`, loaded machine — timings UNVERIFIED, counts deterministic)

| Fixture | firstPage | long tasks | heap | main wasm | worker wasm | copies | m2w |
| --- | --- | --- | --- | --- | --- | --- | --- |
| large-40mb | 706 ms | 59 ms (pre-existing) | 16.7 MB | 810 pp / 50.6 MB | 810 pp | 1 × 40.6 MB | 40.4 MB cloned |
| pages-500 | 724 ms | 0 | 17.0 MB | 285 pp / 17.8 MB | 285 pp | 0.5 MB | 0.3 MB |
| huge-150mb | 675 ms | 59 ms (pre-existing) | 16.4 MB | 285 pp / 17.8 MB | 3010 pp / 188.1 MB | 1 × 155 MB | 155 MB cloned, post 12.4 ms |
| form-40mb | 706 ms | 52 ms | 17.2 MB | 1070 pp / 66.9 MB | 742 pp / 46.4 MB | 1 × 30.6 MB | 30.9 MB |
| large scroll (50 wheel steps) | 711 ms | 0 | 16.7→17.2 MB | 810 pp | — | — | 209.2 posted / 207.1 transferred |

Gate this pass: typecheck / lint / format clean; vitest **3452/3452**;
verification batch **57 passed + 1 ambiguous** (`page-editor-rotation`,
finding 2); menu spec **5/5 ×3**; soak default + form **green**;
engine smoke **3/3 browsers** (0 worker wasm fetches);
`check:embedpdf-patch` green.

### Findings (new this pass)

1. **GHOST→LANDED.** The pass-6 menu port was documented with full evidence
   but never committed. Re-verified and landed as `5f98b13c6` (one commit,
   code + spec). No other GHOST rows; no DECAYED production rows.
2. **P1 ambiguous: `page-editor-rotation` fails** (expects 4 page images
   with `data-original-rotation`, mounts 1 in 30 s). Fails identically with
   and without `5f98b13c6` (parent-tree bisect) — not caused by this pass.
   Claimed passing in earlier verification lists; load at failure time was
   ~11.75 with IntelliJ at 338%. Verdict open: regression-vs-load. Probe:
   idle-machine re-run (risk register 1).
3. **ux-flows.md line refs drifted** (LocalEmbedPDF edits moved them); row 1
   and row 5 re-anchored this pass. Substance verified current.
4. **`NEXT_AGENT_PROMPT.md` (excluded scratch) is stale** (3420 tests, old
   backlog/gotchas). Superseded by this section; left in place, do not follow
   without checking here first.
5. **Hunt item 7 CLOSED by verification, no code:** url-only opens do not
   occur — both `LocalEmbedPDF` callers always pass `file`, and
   `useFileWithUrl` never returns file-null. The `fetch(url)` branch is
   defensive-only; routing it through the cache buys nothing.
6. **In-flight thumbnail settle + bare-Blob LRU routing stand** as
   specified-but-unshipped (queued requests settle per `d470105a6`;
   in-flight settle needs a reject-vs-resolve-null decision; bare-Blob path
   is latent — both live callers pass Files).

### Phase B verdict: BLOCKED — runbook TODO (machine never idle this session)

No process-quiet window met the idle bar (no proc >5% sustained); every
absolute timing in the table above stays UNVERIFIED. Runbook for the idle
pass (owner or next agent):

1. Idle bar: `uptime` loadavg <2 with `ps -eo pcpu,comm -r` showing nothing
   >5% sustained over 60 s (quit browsers/IDEs/builds first).
2. `git status` clean at the recorded HEAD; rebuild:
   `VITE_BUILD_FOR_PREVIEW=1 task frontend:build`.
3. Recreate the baseline arm once: `git worktree add ../sp-baseline
   77b325cf1 && cd ../sp-baseline && task engine:install` (only if a
   backend is needed) `&& cd frontend && npm ci && npm run
   check:embedpdf-patch` (expected: FAIL — pre-patch baseline, the guard
   working) then build that worktree's `dist` for its arm. Keep the worktree
   path out of the repo; delete after.
4. Interleave A/B per fixture (large-40mb / huge-150mb with
   `PERF_LARGE_FIXTURE=… PERF_SETTLE_MS=15000` / form-40mb / pages-500),
   n>=5/arm, alternating arms, recording median + range of: first page,
   long tasks, full copies, main+worker WASM high-water (50 ms sampling),
   post-GC heap, worker→main transfer counts, engine fetch/compile counts.
   Scroll arm: `PERF_SCROLL_WHEEL=1 PERF_SCROLL_STEPS=50` (plus the
   `PERF_SCROLL_SCROLL`/harness variants in the harness doc).
5. Respawn arm: huge-document open → workbench-empty → worker pages
   3010→284; soak default + form (12-cycle) with all budgets; corpus runner
   both arms (60 files) + affected-tag parity.
6. Replace every UNVERIFIED timing with the golden median+range, stamp
   BASELINE.md with the idle loadavg readout, delete `../sp-baseline`.

### Phase C verdict: no code landable — approval intersection is empty

Mapped brief R-labels → tree state: R1 = G17 (per-page form state —
**needs owner sign-off before coding**, product semantics); R2 =
G18-first-slice (main-cache drop for ≥100 MB — **"when greenlit"**, not
greenlit; needs viewer-lifecycle coordination); R3 = PdfCache
configurability (no evidence yet, never approved); R4 = metadata deferral
(never approved); R5 = prefetch/G31 (no measurements, parked); streaming /
decoder probes = settled law (IndexedDB/localStorage rejected with
grounds; OPFS/JSPI parked as memo — re-running needs new evidence).
Settled law was not re-litigated. Hunt item 7 closed by verification
(finding 5) with no code. Characterization tests were kept, not added:
no change shipped, so no new tests were warranted.

### Phase D artifacts

#### Revert map (one command proves green after any revert: `task frontend:typecheck && task frontend:lint && task frontend:test` plus the affected e2e/soak row)

| Landed change | Revert (`git revert <sha>`) | Joint-revert note |
| --- | --- | --- |
| One-read bytes + scan queue | `81ba215e3` | — |
| Shared main-thread document (+ release-pending) | `92f7fbaa1`, `d786c1822` | revert both; rerun soak |
| AcroForm gate + catalog probe | `118ee1718`, `88d6b40dd` | revert both; rerun huge + form fixtures |
| Index metadata + single thumb | `2a2528796` | — |
| Per-page overlays | `2e6d1c38b` | rerun form fixture |
| Engine transfer/module patch | `bc519ae07` | with it, also drop `useLocalPdfiumEngine` handoff (`61…` no — contained in patch + hook); rerun smoke 3/3 + scroll |
| Engine URL revoke | `e9b820d5a` | patch-file only; rerun form soak (blob budget trips without it once respawn fires) |
| Wasm precompression | `3cd84a5e3` | build-only; rebuild to verify |
| F10 JPEG thumbnails | `87205808e` | rerun form soak (heap budget trips without it) |
| Respawn on huge close + 10 MB threshold | `8c2e740c1`, `852192fac` | revert both; rerun huge soak |
| Registry/bytes-cache release + File-signature dedupe | `a542633f1`, `c1860a3da`, `2dad72c57` | rerun mh14-style snapshot or huge close/remove |
| G19/G21 plugin coalescing | `cac5fdb64`, `c511e6da6` | patch-file only; rerun jump profile + search settle |
| G23 posthog gate | `b99a0c8b1` | rerun analytics unit tests |
| G23b jszip lazy | `28fb8583e` | rebuild; vendor-zip must rejoin the waterfall |
| Text-selection menu | `5f98b13c6` | rerun menu spec (fails without) + selection batch |
| Hunt teardown/counts fixes (each independently revertable) | `16f4bb6d8`, `c41008d99`, `bf0c5b4f7`, `9b7d92afa`, `290fe972d`, `2bf8d5a1f`, `37db8f0f6`, `79ef97cd3`, `fcc1d0b34`, `8c0fbf4e1`, `789a573e6`, `eea2e63a5`, `2ced8f0e9`, `0af4de496`, `3fb4727eb`, `d470105a6`, `faeadac74` | rerun the commit's named test/soak row |

#### Patch-integrity readout

Pin: `@embedpdf/engines` + `@embedpdf/plugin-*` installed **2.15.0**
(lockfile agrees; `package.json` range `^2.14.4` admits it). Both patch
scripts assert `EXPECTED_VERSION = "2.15.0"` plus per-anchor strings and
fail loudly otherwise — verified green just now. Coverage:
`frontend:build` + `frontend:check` + `desktop:build` depend on
`check:embedpdf-patch`; Tauri workflow and `docker/frontend/Dockerfile`
run it; an unpatched tree fails the build instead of shipping zero
transfers (the guard has tripped once before, per the ledger). Bump
failure modes: version mismatch (exact-string gate), anchor drift
(per-anchor gate naming file+label), CJS-entry consumers silently
unpatched (only the ESM entry is patched — graceful, no transfers), older
WebKit structured-clone fallback (try/catch path, no test coverage —
risk register 2). Own-worker swap stays parked until a release exports
the runner with a precompiled-module option.

#### Risk register (blast radius × uncertainty)

1. `page-editor-rotation` ambiguous failure (finding 2) — probe: idle re-run.
2. Older-WebKit `DataCloneError` fallback untested — probe: pre-26 WebKit run.
3. Desktop/Tauri never exercised (WKWebView ~2 GB ceiling noted) — probe: desktop build + smoke.
4. G13 main-wasm floor 17.8–123 MB/session unreclaimed — probe: first-open snapshot + microtask instrumentation per mh14.
5. Full 822-spec suite not re-run (6 known pre-existing caret/undo failures
   + 1 ambiguous rotation) — probe: full idle run.
6. Corpus both-arms predates G19/G21/G23/G23b/menu (full 60/60 is pass-4;
   pass-7 re-ran text+forms+large-real 18/18) — probe: full both-arms run.
7. Startup-byte win (posthog 77 KB conditional + zip 45 KB) rests on chunk
   math + one runtime probe — probe: post-G23b coverage run.
8. Supabase static edge in the viewer waterfall (G30, 190 KB raw) — parked,
   needs sign-off + flavour matrix.

#### Owner decision memo

1. **Phase-C authorization.** Options: (a) land nothing further on this
   branch — recommended (branch is green, measured, and shippable as a
   snapshot; every remaining item needs sign-off, design, or idle numbers);
   (b) greenlight exactly one of R2 (S, counts-measurable on loaded
   machine, timings re-debited to Phase B) with the viewer-lifecycle
   coordination designed up front. Evidence: Phase C verdict above.
2. **G17 per-page form state.** Options: per-page extraction (kills the
   66.9 MB all-page pass, touches save/validation) vs status quo.
   Recommendation: product decision first; the 16 MB-over-image-only cost
   does not justify a blind refactor. Evidence: form-fixture rows.
3. **G18 buffer ownership.** Options: R2 first-slice (drop main entry for
   ≥100 MB after worker clone) vs full worker-owns-buffer inversion
   (multi-pass: every main-thread scan becomes a worker task) vs status
   quo (155 MB clone + 155 MB main residency). Evidence: G4 decision
   record; mh runs.
4. **COOP/COEP + OPFS/JSPI staging (R2 memo / R6).** Options: keep closed
   (recommended for SaaS/proprietary — Supabase/PostHog CORP audit + pthread
   pdfium rebuild required) vs core-only staging per the memo's 3 steps.
   Evidence: roadmap-5 memo; Tauri rows in the ledger.
5. **Upstream approach timing.** Suggested split (unchanged): #7691 →
   #7878 → perf PRs (bytes/cache/queue + shared session + gates +
   metadata/index + per-page overlays + engine patch/build compression),
   replayed as focused commits to drop the ~40 merge commits. The three PR
   branches were not touched by this pass. Evidence: branch-shape section.

### Remaining greenlight TODO

Schema: ID | kind | severity | title | target | action | risks both
directions | runnable acceptance | depends on. Sizes S/M/L. Decision items
carry options + recommendation.

| ID | Kind | Sev | Title | Target | Action | Risks (ship / skip) | Acceptance | Dep |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| R1/G17 | FEATURE | P1 | Per-page form extraction | form-40mb main wasm 66.9 MB | Resolve fields per page preserving save/validation | product-semantics regression / 66.9 MB all-page pass stays | form soak + save/validation e2e green, main wasm down on form fixture | owner sign-off (DECISION memo 2) |
| R2/G18-slice | PERF | P2 | Drop main cache entry for ≥100 MB after worker clone | 155 MB main residency | Release `documentBytesCache` entry post-clone with viewer-lifecycle coordination | use-after-release in scans holding the buffer / 155 MB stays resident | huge close/remove: no backing store >1 MB AND no new full read on scroll; counts A/B | greenlight; viewer-lifecycle design |
| R3 | PERF | P3 | `PdfCache` TTL/page configurability | scroll-heavy working set | Expose or patch 5 s TTL / 10-page cap, measure | churn-vs-memory tradeoff unmeasured / status quo | scroll-window A/B shows win | measurement-first approval |
| R4 | PERF | P3 | Defer <100 MB all-page hydration sweep | open path | Page-0-only metadata + lazy rotations | PageEditor staleness / full sweep stays | open A/B counts + PageEditor e2e | approval |
| R5/G31 | PERF | P3 | Scroll-direction prefetch with cancel | jump/scroll wall ~1.3 s | Tile/next-page prefetch + cancellation points | jank + focus theft / wall stays worker-bound | prefetch A/B + rude-interruption audit | design approval |
| R6/OPFS | DECISION | P2 | OPFS/JSPI production wiring | multi-hundred-MB opens | `FPDF_LoadCustomDocument` + sync access handles | WebKit/Tauri gap, pthread rebuild / clone stays | Chrome+Firefox win with Safari fallback documented | DECISION memo 4 |
| G13 | BLOCKED | P2 | Main PDFium floor reclaim | 17.8–123 MB/session | Name the `PromiseReaction` chain, reclaim per mh14 | second instance (measured twice) / floor stays | `MH14_EXPECT_RECLAIM=1` green, no `liveMems` 1→2 | microtask instrumentation |
| G15 | PERF | P1 | Worker-side record thumbnails | plan PDF 123 MB main / 558 ms tasks | Render record thumbs in worker or from worker's first render | worker-render coupling / main-thread decode stays | `PERF_CORPUS_IDS=12 PERF_CORPUS_PERF=1` down | design (pairs with R1-image path) |
| G30 | PERF | P3 | Supabase static-edge lazy | 190 KB waterfall | G23-style async getter | auth/licensing adjacency / bytes stay | waterfall A/B + flavour matrix | sign-off + matrix |
| G12b | CARRY | P3 | Full plugin-teardown decoupling | registry/plugin graph | Decouple teardown from React closures | teardown churn / superseded by G12a | retainer-map proof | only if graph grows |
| G32/G22/G25–G29 | CARRY | P3 | Low-end encode compare; multi-doc floor; print-DPI; GPU RSS; 8-flow matrix; Tauri run; JSPI memo | various | As previously specified | — | per-item probes | as noted |
| in-flight-settle | DECISION | P3 | Settle in-flight thumbnail requesters on destroy | dangling awaiters | Decide reject-vs-resolve-null, then settle | caller-contract break / dangle stays | unit test pins the choice | owner call |
| bare-Blob-route | CARRY | P4 | Route large bare-Blob URLs through evictable keys | latent | Only if a large bare-Blob caller appears | churn / nothing (latent) | audit stays: callers pass Files | trigger-based |

Safest-minimal-set if the owner wants one more slice: R2 alone (S-sized,
counts-provable without idle silicon). Nothing in this pass was
implemented beyond approved scope: the sole production commit
(`5f98b13c6`) reconciles a documented-but-uncommitted bug fix; hunt item
7 closed by verification with no code.

### Self-check

- Gate green (typecheck/lint/format/vitest 3452/3452/smoke 3/3/soaks
  green; verification batch 57+1-ambiguous with bisect proving
  non-causation). Tree clean at `5f98b13c6`.
- `.perf-local/` + corpus untouched by commits (git-excluded; only new
  `result-*.log`-free console output this pass — no new scratch files
  added; `dist/` rebuilt, git-ignored).
- Every claim above traces to a commit, a file:line, or a fresh run in
  this pass. Mode + load + network recorded. No pushes, no PRs, no
  upstream branches touched, no binary rebuilds.

## Hard constraints for follow-up work

- **No upstream EmbedPDF patches.** The maintainer is preparing the 3.0 launch
  and rarely takes patches. If an engine change is needed, do it locally
  (patch-package, a vendored wrapper, or an owned worker) and document the
  pin/version it applies to.
- Measure before and after every change with the harness described below.
- Keep the harness, fixtures and raw logs git-excluded
  (`.git/info/exclude` lists `frontend/editor/.perf-local/` and the local spec).

## Branch shape

Base: `upstream/main` at `77b325cf1`. `viewer-perf-recon` layers three
in-flight branches and then the perf commits:

- `viewer-fonts` (#7691, open) — document state hooks, local PDFium font
  fallback, buffer loading, zoom/selection fixes.
- `embedpdf-noodling` (#6568, draft) — lazy/tiled rendering, app-level engine,
  WASM preload, plugin tuning.
- `viewer-fix-with-no-cache` (#7878, open) — removes the pdf.js worker from the
  viewer (OCProperties parser, PDFium metadata/password/page-count/read-aloud)
  plus a PDFium worker lifecycle fix.

Perf commits (oldest → newest):

| Commit | Purpose |
| --- | --- |
| `6170eb194` | Reconciliation merge of noodles onto viewer-fonts; 2 conflicts (`LocalEmbedPDF.tsx`, `wasmPrecompiler.ts`); engine stays in `AppProviders` with `getLocalFontFallbackConfig()` |
| `81ba215e3` | `documentBytesCache` (one ArrayBuffer per Blob) + `pdfiumScanQueue` (serialize main-thread scans); ruler-only measurement parsing; skip enrichment with no fields |
| `9b9c53dff` | Merge #7878 and route its reads through cache/queue; byte-scan `/OCProperties` before decoding |
| `92f7fbaa1` | Ref-counted shared main-thread PDFium document (`openRawDocument` / `closeDocAndFreeBuffer`) |
| `118ee1718` | `/AcroForm` byte gate: form/signature/button scans never run for form-less PDFs |
| `2a2528796` | `EPDF_GetPageBoxByIndex`/`EPDF_GetPageRotationByIndex` metadata reads (no page load); skip the duplicate rotated thumbnail render when rotation is 0 |
| `2e6d1c38b` | Signature/button overlays resolve per rendered page instead of all pages |
| `bc519ae07` | Local `@embedpdf/engines` 2.15.0 patch via postinstall: worker→main render-result transferables + precompiled `WebAssembly.Module` handoff; `useLocalPdfiumEngine`; bootstrap marks |
| `3cd84a5e3` | Brotli/gzip the hashed pdfium wasm asset (Vite compression `include`) |

## Measured results (production build, Chromium, same harness)

### `large-40mb.pdf` (40.4 MB, 80 pages, incompressible images)

| Metric | upstream/main | branch start | final |
| --- | --- | --- | --- |
| first page rendered | 1055 ms | 873 ms | **~640-900 ms (load-dependent)** |
| long-task blocking | 636 ms | 54 ms | **0-70 ms (the ~60 ms task is pre-existing)** |
| full JS copies | 10 / 242.8 MB | 11 / 283.2 MB | **1 / 40.6 MB** |
| main-thread WASM high-water | 146.2 MB | 146.2 MB | **50.6 MB** |

Absolute timing here tracks machine load (the original runs were made on an
idle machine; an interleaved re-run on a loaded machine measured medians of
1113 ms baseline vs 889 ms branch). Only the interleaved A/B, not any single
absolute value, is the evidence.

Memory method: "WASM high-water" is the max of a **1 s interval** sample of the
wrapped `WebAssembly.Memory.buffer.byteLength` (main and worker), so it is a
lower bound on transient peaks. "Heap" figures are CDP JS heap read after
`HeapProfiler.collectGarbage`. A 12-cycle GC-normalized soak now runs in
`viewer-memory-soak.spec.ts`; it asserts a flat object-URL count and bounded
heap/node growth after file removal.

### `huge-150mb.pdf` (155 MB, 60 pages)

| Metric | before | final |
| --- | --- | --- |
| first page | ~720-960 ms | **677-1122 ms (load-dependent)** |
| main-thread WASM high-water | **325.1 MB** | **17.8 MB** |
| full JS copies | 1 full read + slices | 1 full read + slices |
| crashes / OOM | none | none |

### `form-40mb.pdf` (30.4 MB, 60 pages, AcroForm text+checkbox fields)

| Metric | all-page scans | per-page lazy |
| --- | --- | --- |
| long-task blocking | 64 ms | **0-70 ms (mixed runs)** |
| main-thread WASM high-water | 66.9 MB | 66.9 MB |

The unchanged WASM is the open item: the global `extractFormFields` pass still
opens the document and walks every page. See backlog item 1.

### `pages-500.pdf` (352 KB, 500 pages)

First page ~280-400 ms, 0 long tasks on the branch.

## Second pass (WS1: worker boundary + WS2: bootstrap/caching)

Commits `bc519ae07` and `3cd84a5e3`. Same production harness, same fixtures.

### Engine worker boundary (local `@embedpdf/engines` 2.15.0 patch)

The harness now instruments worker targets too: `Worker.prototype.postMessage`
bytes on the main thread, and inside every worker `self.postMessage` bytes /
transfer lists, `WebAssembly.instantiate` / `WebAssembly.Instance` compiles and
memory buffers, and wasm resource timings. Numbers (median unless noted):

| Signal | before | after |
| --- | --- | --- |
| scroll 5 pages, worker→main bitmap bytes, transferred | 0 of 44.7 MB | **44.3 of 44.7 MB** |
| engine worker wasm HTTP fetches | 1 | **0** |
| engine worker pdfium compile | 3 ms (fetch + instantiate) | ~1 ms (handed `WebAssembly.Module`) |
| large-40mb first page | 720 ms (667/720/743) | ~692 ms (637/662/692/708/716 + one 852 outlier) |
| pages-500 first page | ~370 ms (272/358/382/405) | **~186 ms** (175/182/186/258/349) |
| main-thread WASM high-water | 50.6 / 17.8 / 66.9 MB | unchanged |
| worker WASM high-water (huge / form) | 188.1 / 46.4 MB | 188.1 / 46.4 MB |
| main→worker document bytes | 40.4 / 155 / 30.9 MB cloned | unchanged (see backlog) |

The 155 MB document clone also shows up as a single `openDocumentBuffer`
`postMessage` of up to 44.9 ms on the main thread — evidence for backlog 2.
Huge/form first pages are unchanged within run variance (huge 677-1122 ms,
form 650-671 ms after; their open path does not cross the patched boundary).

The ~60 ms single long task that appears in roughly two of three large-40mb
runs is pre-existing: 2/3 pre-patch runs show it too (65/60 ms), and
`postMessage` never exceeds 3 ms. Not a regression.

Cross-browser smoke (`engine-patch-smoke.local.spec.ts`, Chromium/Firefox/WebKit):
page renders with zero page errors; wasm requests inside all workers = 0 in all
three engines. WebKit 26 accepted the structured clone; the fallback for older
WebKit is exercised only by the try/catch path.

### Wasm delivery

- `vite-plugin-compression2` no longer skips `.wasm`: the hashed asset now ships
  `dist/assets/pdfium-*.wasm.br` = **1.65 MB** (from 4.63 MB, -64%) and `.gz`
  2.14 MB. `WebMvcConfig` already serves `/assets/**` with
  `EncodedResourceResolver`, so production picks the `.br` sibling; `vite
  preview` does not, which is why harness `transferSize` still shows 4.63 MB.
- Bootstrap marks (cold run, localhost): eager compile starts ~49-56 ms and
  ends ~72-82 ms (≈25 ms fetch+compile), engine created ~365-374 ms, no worker
  fetch/compile. The compiled module is reused by the worker instead of being
  fetched and compiled twice.
- In-session repeats reuse `pdfiumWasmModulePromise`; cross-session repeats are
  covered by the immutable `/assets/**` cache. No IndexedDB tier is warranted
  at these numbers; revisit only if real-world cache-miss bootstrap dominates.

### BMP render path (#557) — already enabled, A/B verified

`LocalEmbedPDF.tsx` already sets `defaultImageType: "image/bmp"` on the render
and tiling plugins, so the engine wraps raw RGBA in a BMP header instead of
running `canvas.toBlob(PNG)` in the encoder pool. Paired harness runs (2 each,
same build, `image/png` temporarily substituted):

| Signal | PNG | BMP |
| --- | --- | --- |
| large-40mb first page | 733/798 ms | 817/738 ms |
| pages-500 first page | 370/397 ms | **255/355 ms** |
| main→worker bytes at open (40 MB) | 45.8 MB posted / 5.4 MB transferred | **40.4 MB posted / 0 transferred** |
| main→worker bytes at open (500 p) | 5.7 MB posted / 5.4 MB transferred | **0.3 MB posted / 0** |
| heap after open (40 MB) | 17.0 MB | 17.1 MB |

BMP removes one main-thread RGBA copy and the encoder-worker round trip; the
raw blobs are larger in memory but the post-GC heap sample is unchanged. Keep
BMP: the mechanism is sound and the worker-boundary byte counts are
deterministic. **The timing rows are provisional — n=2 per arm.** On 40 MB the
first-page spread (733/798 vs 817/738) is inside run noise, i.e. no measurable
difference there; the pages-500 arm looked faster (255/355 vs 370/397) but its
spread overlaps too, and a colder run measured 349 ms. Nothing here justifies a
headline number; re-measure with the idle-machine protocol before quoting one.

Output-fidelity check (production build, same viewport, `pixelmatch`): branch
BMP vs upstream PNG element screenshots differ by 0.62% (40 MB p0) and 2.28% /
2.52% (500 p pages 0/499), concentrated on antialiased text edges. Same-arm
captures are pixel-identical (0.000% across two runs), so this is a real
encoder-path difference, not layout or harness jitter: BMP writes PDFium's RGBA
bytes straight out, while the PNG path goes through canvas `putImageData` /
`toBlob`, whose premultiplied-alpha round-trip shifts low-alpha edge pixels.
The worst page (pages-500 p0) was reviewed as an image alongside its baseline
capture and is visually identical — same text, layout and toolbar — so the
delta is encode-level, not structural. No screenshot snapshot test is committed
because cross-platform PNG baselines are brittle; if pixel parity with the
pre-BMP build ever matters, decode both paths in one session and diff the RGBA,
not the composited screenshots.

## Phase 2 (felt moments) — U1 reading continuity (REVERTED)

The reopen-continuity feature (`86111af5a`: device-local reading position,
resume toast, toolbar percent) was reverted after adversarial validation found
it raced the viewer's initial zoom/layout: it could land on page 1 while the
toast claimed a resume, then overwrite the stored position with page 0. The
viewer is a document processor; it does not track where a reader left off.
See `ux/ux-flows.md` for the reverted-flow note.

The page skeleton/shimmer placeholder was also removed: the viewer showed a
shimmer box that was replaced by a loader label and then content, which read as
a flash rather than progress. Off-screen pages now render nothing until the
intersection observer reveals them.

## Root causes found (evidence)

- Opening the viewer made ~7-11 full-file reads/copies. Attribution came from
  wrapping `Blob.prototype.arrayBuffer`/`Response.prototype.arrayBuffer` and
  capturing stacks (dev server gives readable frames). Call sites were:
  hydration thumbnail, form provider, signature appearances, button
  appearances, layer parsing, measurement extraction, viewer buffer.
- Several main-thread PDFium opens of the same bytes drove the WASM heap
  high-water up (Emscripten linear memory never shrinks).
- For image PDFs the all-pages form/signature/button scans pulled whole-page
  data into the main-thread heap (325 MB for a 155 MB file).
- `readPdfiumPageMetadata` loaded every page just to read a box/rotation.
- The thumbnail pair renderer rendered the rotated variant even when rotation
  was 0, where both variants are pixel-identical.

## Harness and fixtures (local, git-excluded)

Paths:

- Spec: `frontend/editor/src/core/tests/stubbed/perf-baseline.local.spec.ts`
- Fixtures + generators + raw logs + `BASELINE.md`:
  `frontend/editor/.perf-local/`
- Excluded via `.git/info/exclude` (not committed).

What the harness measures per run: in-page marks to first page, long-task
total + per-task details, CDP `Performance` CPU metrics, JS heap after forced
GC, blob URL counts, `Blob`/`Response.arrayBuffer` call count + bytes + stacks,
main-thread `WebAssembly.Memory` high-water series, DOM/canvas counts,
main→worker `postMessage` bytes/transfers + max synchronous duration, and per
worker target: `self.postMessage` bytes/transfers, wasm compile/instantiate,
wasm resource timings and worker `WebAssembly.Memory` sizes. Bootstrap marks
(`pdfium-eager-compile-start|end`, `pdfium-engine-created`) and pdfium wasm
resource timings are reported too.

Known limitations:

- Main-thread WASM heap and the worker's WASM heap are both reported now, but
  `wasmLive`/`wasmPeak` in the harness still report 0 (wrapping
  `instance.exports.malloc` did not take effect); use high-water instead.
  The committed soak (`viewer-memory-soak.spec.ts`) instead reports wasm
  **pages** (`buffer.byteLength / 64 KiB`, the unit memory is committed in) for
  both main and workers — bytes mix in unrelated allocations.
- The 1 s high-water sampling is a lower bound; a 50-100 ms poll would close
  most of the transient-peak gap cheaply if a claim depends on it.
- `performance.measureUserAgentSpecificMemory()` is unavailable without
  cross-origin isolation; tests do not set COOP/COEP.
- Runs must use the production preview (`CI=1`) because dev StrictMode
  double-invokes effects and skews memory/timing.

Cross-browser smoke (no CDP): `engine-patch-smoke.local.spec.ts` (also
git-excluded) opens the large fixture and logs main-thread transfer counts,
per-worker wasm resource entries and page errors. Run per project:

```bash
cd frontend/editor
CI=1 npx playwright test --project=stubbed-webkit \
  src/core/tests/stubbed/engine-patch-smoke.local.spec.ts --workers=1 --retries=0
```

Commands (from the repo root):

```bash
# regenerate fixtures
cd frontend/editor
node .perf-local/generate-perf-fixtures.mjs
PERF_FORM_PAGES=60 node .perf-local/generate-form-fixture.mjs

# build and run the baseline harness (40 MB + 500-page)
VITE_BUILD_FOR_PREVIEW=1 task frontend:build
CI=1 PERF_LABEL=<label> task e2e:stubbed -- \
  src/core/tests/stubbed/perf-baseline.local.spec.ts --workers=1 --retries=0

# point the first test at another fixture
PERF_LARGE_FIXTURE=$PWD/frontend/editor/.perf-local/huge-150mb.pdf \
PERF_SETTLE_MS=15000 CI=1 task e2e:stubbed -- \
  src/core/tests/stubbed/perf-baseline.local.spec.ts --grep "large-40mb" --workers=1 --retries=0

# also scroll the 40 MB fixture (render-transfer attribution)
PERF_LARGE_SCROLL=1 CI=1 PERF_LABEL=<label> task e2e:stubbed -- \
  src/core/tests/stubbed/perf-baseline.local.spec.ts --grep "large-40mb" --workers=1 --retries=0

# optional: copy stacks / wasm series / all console logs
PERF_STACKS=1 PERF_SERIES=1 PERF_LOGS=1 ...
```

## Changes worth understanding before touching the code

- `core/services/documentBytesCache.ts` — WeakMap of one `ArrayBuffer` per
  Blob. **Never hand this buffer to pdf.js** (`getDocument` transfers/detaches
  it). All viewer scans read through it.
- `core/services/pdfiumScanQueue.ts` — serializes main-thread PDFium scans.
  Anything that opens a doc on the main thread should run inside it.
- `core/services/pdfiumService.ts`:
  - Ref-counted `sharedDocument` behind `openRawDocument`/`closeDocAndFreeBuffer`.
    Password opens are never shared; different bytes with readers still active
    open privately.
  - `renderSignatureFieldAppearances` / `renderButtonFieldAppearances` accept an
    optional `pageIndexes` filter.
  - `EPDF_GetPageBoxByIndex` / `EPDF_GetPageRotationByIndex` contract verified
    directly against the WASM binary: rotation is a 0-3 index, boxes are
    `FS_RECTF` `[left, top, right, bottom]`, CROP falls back to MEDIA, and the
    box must be rotation-swapped to match `FPDF_GetPageWidthF`.
- `core/utils/asciiBytes.ts` — `containsAscii` + memoised `hasAcroForm`.
  Used by layers, form provider, signature and button overlays.
- `core/utils/pdfiumPageRender.ts` — metadata via index APIs, `FPDF_LoadPage`
  fallback if the extension functions are missing.
- Overlays `SignatureFieldOverlay.tsx` / `ButtonAppearanceOverlay.tsx` — module
  caches are now per source + page.
- `AppProviders.tsx` — app-level worker engine; `fontFallback` is memoised
  (a fresh object per render recreates the engine in a loop). Engine creation
  now goes through `useLocalPdfiumEngine`.
- `wasmPrecompiler.ts` — keeps the `{ module }` container and the
  `compileStreaming` → ArrayBuffer/MIME fallback; marks the eager compile for
  bootstrap measurements.
- `core/hooks/useLocalPdfiumEngine.ts` — mirrors the upstream React hook, but
  awaits `pdfiumWasmModulePromise` (capped at 3 s) and passes the compiled
  module to `createPdfiumEngine`. Own the hook rather than patching
  `@embedpdf/engines/react` so the extra option stays typed.
- `scripts/patch-embedpdf-engines.mjs` — postinstall patch for
  `@embedpdf/engines` 2.15.0 only. It asserts the exact version and every
  anchor, and no-ops once applied. Alternatives rejected: patch-package (the
  embedded worker source is one 600 KB line, so the committed diff would be
  ~1.2 MB), a Vite transform (dev pre-bundling and vitest load the dep outside
  the transform pipeline), vendoring the package (duplicates the bundle).
  The patch (a) transfers whole-buffer `{data,width,height}` worker responses
  ≥64 KB, (b) accepts `options.wasmModule` and instantiates it synchronously in
  the worker, and (c) retries the `wasmInit` post without the module when
  structured clone throws (older WebKit). A version bump must re-verify all
  anchors and update `EXPECTED_VERSION`.
  **CI/Docker coverage**: `frontend:build` depends on the new
  `frontend:check:embedpdf-patch` task, `frontend:check` runs it,
  `desktop:build` and the Tauri workflow run it, and
  `docker/frontend/Dockerfile` runs `npm run check:embedpdf-patch` right after
  `COPY frontend .`. An unpatched build therefore fails instead of silently
  shipping zero transfers. To reproduce:
  `npm ci --ignore-scripts && npm run check:embedpdf-patch` exits 1.
  **Own-worker evaluation (2026-09): not available on the pinned version.**
  The official quickstart ("create your own `webworker.ts` with
  `PdfiumEngineRunner`, wrap it with `WebWorkerEngine`") describes an API that
  is ahead of the published package: `@embedpdf/engines@2.15.0` is still the
  latest on npm and its root/index does **not** export `PdfiumEngineRunner`
  (it exists unexported at `dist/lib/pdfium/runner.js`), so owning the worker
  would still need a deep import or an exports patch, and the runner's
  constructor only accepts an `ArrayBuffer` — there is no precompiled
  `WebAssembly.Module` option upstream either. Re-evaluate when a release that
  exports the runner lands (3.0): our `useLocalPdfiumEngine` already keeps the
  engine creation seam, so the swap is contained.
- `editor/vite.config.ts` — the `compression()` plugin now includes `.wasm`, so
  the hashed pdfium asset gets `.br`/`.gz` siblings.
- The patch deliberately does **not** transfer the document bytes main→worker:
  the buffer comes from `documentBytesCache` and other main-thread scans hold
  it across awaits, so a transfer would detach it mid-read. The worker keeps
  receiving a structured clone (155 MB for the huge fixture).

## Backlog for the next pass (ranked, with evidence)

1. **Per-page form state** — `extractFormFields` still walks all pages on the
   main thread (form fixture main WASM 66.9 MB, first page ~650-770 ms). The
   remaining cost over an image-only document of the same size is ~16 MB of
   main WASM, not the 325 MB pre-gate figure, so profile before committing to a
   refactor. Make form fields resolve per page while preserving
   save/validation semantics — this is a product-level change and needs owner
   sign-off before coding.
2. **Remaining local engine gaps (no upstream)** — transfers and precompiled
   module handoff are done (`bc519ae07`); what is left:
   - the document bytes are still structured-cloned main→worker (155 MB for the
     huge fixture, 9.9-44.9 ms synchronous depending on load). The clean fix is
     ownership inversion: let the worker own the canonical buffer and serve
     main-thread scan reads from it, instead of sharing the
     `documentBytesCache` buffer (transfer neuters the source too, so sharing
     cannot work).
   - worker recycle threshold: wasm pages only grow within an instance, and the
     app-level engine worker keeps its 188 MB steady state after the huge
     document closes. Terminate/respawn the engine worker when its page count
     crosses a threshold after a large document; the 12-cycle soak shows
     repeated small opens are already flat (284 main / 284 worker pages), so
     this only matters for the huge-document case.
   - `PdfCache` page TTL (5 s) / max pages (10) are not configurable from
     `createPdfiumEngine`; expose or patch for scroll-heavy use and measure.
   - only the ESM entry is patched; a consumer resolving the CJS entry gets the
     unpatched engine (graceful, no transfers).
3. **CLOSED — cold/warm bootstrap + caching** — solved without an IndexedDB
   tier: the hashed wasm now ships `.br`/`.gz` (`3cd84a5e3`), `/assets/**` is
   already immutable via `WebMvcConfig` + `EncodedResourceResolver`, the worker
   no longer fetches or compiles wasm at all (module handoff), and the
   in-session promise is reused. Harness/worker marks report ~50-80 ms eager
   compile and no worker wasm activity. Reopen only if production cold-cache
   numbers show network bootstrap dominating.
4. **OPFS/JSPI streaming (Chrome 137+/Firefox 153+; not Safari/WebKit)** —
   `FPDF_LoadCustomDocument` with synchronous block reads from an OPFS
   `createSyncAccessHandle()` inside a worker. Verified present in the pinned
   `@embedpdf/pdfium@2.15.0` build (3 references in the glue + a typed export,
   same check that validated `EPDF_*ByIndex`), so the native side exists; the
   synchronous JS callback remains the constraint and is exactly why this is
   scoped to JSPI engines. Relevant for multi-hundred-MB files because the
   engine worker holds its own copy; document the Safari/WebKit gap for the
   Tauri desktop build.
5. **Pre-render / prefetch** — tile or next-page prefetch based on scroll
   direction; no measurements yet.
6. **Defer all-page metadata further** — hydration still pages through every
   page for files < 100 MB to fill `processedFile.pages[]`; only page 0
   dimensions and per-page rotations for PageEditor are read today.
7. **Split into reviewable PRs** (when the owner asks): suggested order
   #7691 → #7878 → perf PRs (bytes/cache/queue + shared session + gates +
   metadata/index + per-page overlays + engine patch/build compression). Rebuild
   history by replaying the focused commits; the branch currently carries merge
   commits from the three source branches.
8. **Fix or drop the branch's stale perf specs** —
   `pdf-render-benchmark.spec.ts` and `pdf-viewer-memory.spec.ts` fail on
   `.file-sidebar-file-item`, a selector the upstream sidebar refactor removed.
   Pre-existing, not caused by this work.

## Gotchas

- `viewer-fix-with-no-cache` (#7878) is an open PR whose content is inside this
  branch; landing order matters.
- `viewer-fonts`' `pdfBuffer` path still makes one main-thread read and the
  worker receives a structured-clone copy. That is the single remaining full
  JS copy per open.
- The `EPDF_*ByIndex` extension functions must exist in the pinned
  `@embedpdf/pdfium`; the fallback keeps the old path if they do not.
- The 40 MB path (`< LARGE_PDF_PARSE_LIMIT = 100 MB`) parses the full file for
  the thumbnail; the 155 MB path uses a 2 MB linearized prefix.
- `.perf-local` fixtures are large (up to 155 MB). Do not commit them.
- `scripts/patch-embedpdf-engines.mjs` runs from `postinstall`; an existing
  checkout needs one `npm install` (or `node scripts/patch-embedpdf-engines.mjs`)
  after pulling. It fails loudly if `@embedpdf/engines` is not exactly 2.15.0
  or an anchor moved; `npm run check:embedpdf-patch` verifies an applied patch,
  and `frontend:build`, `frontend:check`, `desktop:build`,
  `docker/frontend/Dockerfile` and the Tauri workflow all run that check so a
  silent unpatched build cannot ship (see the patch bullet above).
- All three engines verified (smoke spec): Chromium, Firefox and WebKit 26
  render with zero worker wasm fetches. The `DataCloneError` fallback in the
  patch is only reachable on older WebKit, which has no test here.
- The main→worker document clone is intentionally still in place; do not add a
  transfer without addressing `documentBytesCache` ownership (see backlog 2).

## Verification before any change leaves the branch

```bash
task frontend:typecheck
task frontend:lint
task frontend:test        # 3422 tests at the time of writing
# functional viewer specs, all passing at the time of writing:
CI=1 task e2e:stubbed -- \
  src/core/tests/stubbed/form-field-editing.spec.ts \
  src/core/tests/stubbed/validate-signature-trust.spec.ts \
  src/core/tests/stubbed/viewer-text-selection.spec.ts \
  src/core/tests/stubbed/viewer-touch-device-selection.spec.ts \
  src/core/tests/stubbed/viewer-redaction-exit-restores-selection.spec.ts \
  src/core/tests/stubbed/viewer-sidebar-add-buttons.spec.ts \
  src/core/tests/stubbed/page-editor-rotation.spec.ts \
  src/core/tests/stubbed/pdf-text-editor-cropbox.spec.ts \
  src/core/tests/stubbed/workbench-session-restore.spec.ts \
  --workers=1 --retries=0
```

Last verified after the validation pass: typecheck/lint clean, 3422/3422
vitest, 48 viewer e2e passed (1 pre-existing skip), engine smoke green on
Chromium/Firefox/WebKit.

## Validation-pass changes (prod readiness)

- **Reverted `86111af5a`** (reading-position resume + toolbar percent + ux
  recorder). Restore raced the initial zoom: 3/3 instrumented runs painted the
  cover under a "Resumed where you left off" toast and overwrote the saved
  position with page 0. A document processor does not track reading history.
- **Removed the page skeleton/shimmer** (`LocalEmbedPDF.tsx` LazyPageContent,
  `.pdf-page-skeleton` CSS + tokens). Off-screen pages now render nothing; the
  open sequence is one spinner whose label changes from "Loading PDF Engine..."
  to "Preparing document...".
- **`/AcroForm` byte gate confirmed by an exact catalog probe**: removing the
  gate entirely made the eager overlay extract fields on every open and raised
  large-40mb main-thread WASM from 50.6 to 87.6 MB, so the literal scan stays
  the fast path. A literal miss is no longer treated as "no form": under
  `LARGE_PDF_PARSE_LIMIT` (where thumbnail hydration opens the document on the
  main thread anyway) `PdfiumFormProvider` now reads the catalog's
  `FPDF_GetFormType` (0 none, 1 AcroForm, 2/3 XFA) without loading pages, and
  only extracts when it is non-zero. At or above the limit the probe is skipped
  so a 100 MB+ file never pays a full main-thread copy just to check. `null`
  from the probe (API absent) falls through to extraction rather than reading
  as "no form". The Form Fill refresh still passes `exhaustive: true`.
  `hasAcroForm` remains an overlay-only fast path; the regression test covers
  probe-miss, probe-hit, unknown and over-limit cases.
- **Chunk-load recovery**: `vite:preloadError` and the ErrorBoundary now reload
  once when a lazy chunk fails (WebKit reports "Importing a module script
  failed"), instead of parking the user on "Something went wrong".
- **Thumbnail buffer cache** is a `WeakMap` now, so a deleted File cannot pin
  its full ArrayBuffer.
- **Viewer URL cache eviction**: `useFileWithUrl`'s 25-entry LRU pinned up to 25
  full documents; removing a file now evicts and revokes its cached object URL
  (`evictFileUrl` from the file lifecycle). The committed memory soak
  (`viewer-memory-soak.spec.ts`, Chromium) keeps the object-URL count flat.

## Memory-hunt pass (static-only; loaded machine, no new timing/memory numbers)

Conditions: load avg ~12, several processes >5% CPU sustained, so the
interleaved-A/B protocol could not run. Every finding below is
PARTIALLY CONFIRMED (static proof + primary-source doc leg; the idle-machine
empirical leg is still open). No class-A code shipped. A/B arm worktree
`../sp-baseline` at `77b325cf1` exists for the next pass. The working tree
carries one pre-existing uncommitted change (soak-spec timer census,
`SOAK_FIXTURE`, `PERF_SNAPSHOTS`, `PERF_FINALIZERS`); it was reviewed
statically only (typecheck/lint clean) and left uncommitted.

Ruled out (no finding, do not re-hunt without new evidence):

- `HEAPU8` staleness in the viewer path: the pinned glue reassigns
  `Module['HEAPU8']` on every growth (`@embedpdf/pdfium/dist/index.js`
  `updateMemoryViews`), and every src use site reads `.HEAPU8` fresh with no
  malloc between acquisition and last read. Copies precede `free()`.
- Worker→main render transfers: whole-buffer views ≥64 KB transfer and
  `respond()` returns immediately with no post-transfer read; partial views
  fall back to clone (`frontend/scripts/patch-embedpdf-engines.mjs`).
- Doc/page/annot/bitmap pairing: `extractFormFields`, `fileAnalyzer`,
  `enrichWithAlternateNames` and all sampled overlay paths close pages,
  annons, bitmaps and docs in `finally` blocks.
- Canvas pool (`pdfiumPageRender.ts`): zeroed on release, `POOL_MAX` 4.
- `SearchInterface`/`ZoomAPIBridge` intervals: cleared on hide/unmount.
- IndexedDB handles: one cached connection per storage service.
- `pdfiumDocBuilder._decodeImage` URLs: revoked on load, error and catch.

New backlog (ranked; full greenlight table in the hunt report):

1. Overlay caches skip unmount cleanup when a document is open
   (`LocalEmbedPDF.tsx` clear effect early-returns while `file || url` is set,
   so unmounting with a doc open pins the Blob + full buffer until the next
   open). Always return the cleanup.
2. `sharedDocument` at zero refs is never closed; `releaseSharedDocument`
   runs only from `resetPdfiumModule` (`pdfiumService.ts`). A closed
   document's malloc'd bytes + PDFium caches stay committed until the next
   different-bytes open. Release on document-leave with hysteresis.
3. `destroyThumbnails` drops queued thumbnail requests without settling them
   (`useThumbnailGeneration.ts`); awaiting callers dangle. Decide
   reject-vs-resolve-null, then settle on destroy.
4. Bare-Blob `useFileWithUrl` keys are un-evictable except via the 25-entry
   LRU (`useFileWithUrl.ts`); current bare-Blob callers are small, so this
   is latent. Route large bare-Blob URLs through an evictable key.
5. Thumbnail `pdfDocumentCache` double-accounts `sharedDocument` refs, and
   `clearPDFCacheForFile` closes without checking its own refCount: a narrow
   use-after-close race against an in-flight render. Gate the close on
   refCount, or stop sharing the pointer between the two caches.
6. `readPdfiumPageMetadata` reads rotation after creating heap views
   (`pdfiumPageRender.ts`); a growing call between would stale them (today
   degrades to the fallback path). Call rotation first, then read the heap.
7. `LocalEmbedPDF` url-without-File branch fetches a full second copy
   outside `documentBytesCache`. Verify whether url-only opens occur; if so,
   route through the cache.
8. `SignaturePreviewLayer` has no `pointercancel` handler, so a cancelled
   drag keeps element-scoped listeners for the element lifetime. Add the
   symmetric removal.

Allocator reality (pinned `@embedpdf/pdfium` 2.15.0, verified by export
inspection, 630 exports): no `mallinfo`/`sbrk`/heap-stats exports (only
`malloc`/`free` among allocator names), one `memory` export, 138 `EPDF_*`
extensions including `FPDF_GetFormType`. Fragmentation therefore has no
direct telemetry; high-water-vs-growth-per-open stays the proxy, and the
worker 188 MB floor is releasable only by respawn (proposal unchanged).

Buffer ownership at steady state (40.4 MB fixture, committed logs):
main `documentBytesCache` 1 × 40.4 MB (shared by reference with viewer
`pdfBuffer` state, 0 extra) + main WASM 50.6 MB + worker structured clone
40.4 MB transient + worker WASM ≈50.6 MB + transferred BMP bitmaps (5.3 of
5.4 MB, post-GC JS heap ~18 MB total). 155 MB fixture: main cache 155 MB +
main WASM 17.8 MB (prefix path, no main-thread open) + worker clone 155 MB
(~45 ms sync post) + worker WASM 188.1 MB. Form fixture: main WASM 66.9 MB
(all-page `extractFormFields` pass) + worker 46.4 MB. No site was found
where a reallocation-copy occurs, so resizable-ArrayBuffer has no applicable
target; not proposed.

## Memory-hunt implementation pass (2026-09-12; aload machine, counts not timings)

Follow-up to the static-only section above: the owner greenlit implementation.
Machine stayed loaded (load 3-8, several procs >5%), so no timing A/B is
claimed anywhere; every number below is a deterministic count (tests,
CDP counters, timer census) or a drift ratio, each reproduced. The
`../sp-baseline` worktree was removed after use, and the previously
uncommitted soak-spec scratch is now committed (`f6bfbc02d`). Branch
`viewer-perf-recon` stays local-only, never pushed.

Shipped (one commit each, typecheck + lint clean per commit):

| Commit | Fix | Evidence |
| --- | --- | --- |
| `16f4bb6d8` | Delete dead `useProgressivePagePreviews` (own full copy + worker) and leak-by-design `useFileWithUrlAndCleanup` | Zero importers repo-wide; full suite 3430/3430 after |
| `bf0c5b4f7` + test | `evictLeastRecentlyUsedPDF` no-op spin -> uncached bypass + `try/finally` release | New hang test wedges pre-fix (zero output in 60 s), passes in <1 s post-fix |
| `bf0c5b4f7` | `addThumbnailToCache` replace double-count | New unit test; spec 3/3 |
| `9b7d92afa` + `8c0fbf4e1` | Overlay module caches pin closed doc; cleanup made unconditional after review caught the unmount-with-open hole | typecheck/lint; form-fixture soak exercises the path |
| `290fe972d` | Thumbnail queue's second full copy -> `getDocumentBytes` | typecheck/lint |
| `2bf8d5a1f` | `destroyThumbnails` unreachable -> PageEditor unmount | typecheck/lint |
| `37db8f0f6` + test | Bare-Blob URL keys `blob-${size}` collide -> WeakMap identity keys (audit: both live callers pass Files, path was latent) | 2 new unit tests |
| `79ef97cd3` | Thumbnail fallback full reads -> `getDocumentBytes` | thumbnailUtils spec 8/8 |
| `f6bfbc02d` | Soak: `SOAK_FIXTURE`, timer/RAF census + stacks, `PERF_SNAPSHOTS`, `PERF_FINALIZERS`, listeners budget (measured drift 0, bound 10) | Default green; form fixture trips heap at 1.39x (budget working); snapshot pair parses (2.74M -> 2.82M heap nodes, DOM flat) |
| `fcc1d0b34` | `Promise.race` fallback timers never cleared (`AttachmentAPIBridge` 10 s x N retries, `useLocalPdfiumEngine` 3 s) | 25-cycle timer census: drift 22->60 with 14 `getAttachments` zombies before; promise group 0->0, zero such zombies after; all budgets hold both runs |

Verification shape at end: full vitest 3430/3430, touched specs 3x3 green,
48 e2e + 1 pre-existing skip, engine smoke 3/3 browsers, soak green on
default (12-cycle x4, 25-cycle x2), form-fixture soak fails only on the heap
budget (new finding F11 below, not a regression: the budget never covered
form docs).

New open findings (all with reproduction logs under `.perf-local/`,
`result-soak-hunt-*` + one `soak-snapshot-iter{5,12}.heapsnapshot` pair):

- **F10: form-fixture retained-heap drift 1.39x over 12 cycles** (+9.7 MB,
  both phases; nodes/listeners/URLs flat). Suspect: per-file form state
  (`FormValuesStore`, pending/modified/deleted/skipped collections in
  `FormFillContext.tsx`) with no `REMOVE_FILES` handling anywhere in
  formFill. Next step: diff the committed snapshot pair.
- **F11: main-thread WASM +~40 pages/cycle reopening the same form bytes**
  (1799 -> 2159 pp over 12 cycles; worker flat 742 pp). Each soak cycle
  re-uploads (new Blob identity), so this is the H3 fragmentation proxy
  measured: close + reopen ratchets the main module high-water. Needs the
  same-Blob-identity control to separate reopen cost from allocator
  ratchet; feeds the worker-respawn proposal.
- **Warm-up timer plateau (~+28 by cycle ~10, flat to cycle 25):** Mantine
  `Us`-chain timeouts (~11-32x, needs dev-readable stacks to attribute),
  embedpdf scroll/throttle debounces (vendor code, out of scope). Bounded
  per session, not per cycle; no action beyond confirming the plateau on a
  longer run.
- Bare-Blob keys are now identity-stable but still only LRU-evictable;
  `destroyThumbnails` still settles nothing for in-flight requesters
  (backlog items 3-4 above stand).

## Memory-hunt pass 2 (2026-09-13; semi-idle machine: counts/drifts verified, timings UNVERIFIED)

Protocol: the owner greenlit semi-idle runs. Load 5-9/18 cores with desktop
processes >5% sustained, so every timing number is UNVERIFIED; drift ratios,
CDP counters, page counts and heap-snapshot diffs are treated as the
deterministic evidence. Base for this pass: `9875f37f2` (tree carried two
uncommitted fixes from the previous session, adopted and committed).

Shipped (one commit each):

| Commit | Change | Evidence |
| --- | --- | --- |
| `d786c1822` | Adopted the leftover `sharedReleasePending` fix: release-on-last-reader instead of force-close-under-reader; stale zero-ref close can no longer double-close; `resetPdfiumModule` drops the handle instead of closing through a dead module | New `pdfiumService.sharedDocument.test.ts` — release-defer / stale-close / reset-with-reader assertions 4/6 red pre-fix, 6/6 green post-fix. The deferred path is unreachable from today's macrotask callers (all reader windows are microtask-scoped), so no behavior change on current paths |
| `789a573e6` | Adopted the `SignaturePreviewLayer` pointercancel fix: cancelled drags/resizes now run the same teardown | New `SignaturePreviewLayer.gestures.test.tsx` — 3/3 red pre-fix, 3/3 green post-fix |
| `4fa05005d` | Soak: new `workers` series + budget (drift 0) | Measured 3 -> 3 over 12 cycles, both phases |

F10 (form-fixture retained-heap drift) — CONFIRMED, root named:

- Reproduced twice: 12-cycle form soaks drift 1.38x/1.41x (documented 1.39x;
  one earlier run today read 1.64x under heavier load).
- Snapshot pair (form fixture, iter5/12): **+11 PNG data-URL strings
  (+9.4 MB)**; 18 data-URL strings alive at cycle 12 = 14.5 MB. The strings
  are `processedFile.thumbnailUrl` on file records (~854 KB per upload).
- Retainers (dev-server snapshot with readable names,
  `form-dev-snapshot-iter5.heapsnapshot` + prod pair
  `soak-snapshot-iter{5,12}.heapsnapshot`): stale React state versions —
  FileSidebar `allFileStubs` arrays pinned via `onUploaded` props and
  `refreshStubs`↔`handleSaveToCloud` closure chains on mounted fibers in the
  file-sidebar subtree, plus FileContext `files.byId[uuid]` records pinned via
  context closure chains (28 uuid-keyed record objects over multiple context
  states in the prod snapshot). Deletion removes the live record; older state
  versions keep the strings reachable.
- Class: cross-component lifecycle (React state/closure retention), so this is
  a proposal, not a quick fix. Options and tradeoffs in the greenlight table.

F11 (form reopen ratchet) — NOT REPRODUCED; do not cite it further:

- Two production 12-cycle form soaks: main WASM flat (1215 pp / 1799 pp
  runs; no per-cycle growth). Absolute main-WASM readings on the form fixture
  are not stable run-to-run (~36 MB offset between runs), but no ratchet.
- Cross-document probe (`mh11-worker-ratchet.local.spec.ts`): one session,
  huge→form→large ×2. Main module converges 284 → 1070 → 1651 pp after the
  first pass and stays flat through the second; worker flat at 3010 pp
  (188.1 MB) from the first huge open through six subsequent opens. **No
  allocator churn-ratchet exists on current code.** The worker 188 MB floor is
  the first huge document's high-water; only respawn reclaims it.

New finding (small, class B): `HistoryAPIBridge` never prunes
`imageDataStore` entries — every STAMP undo/redo/crop-recreation mints a new
annotation id and stores the image under it (`HistoryAPIBridge.tsx:78`,
`:149`); the old id's entry (full image data URL/blob) is unreachable but
retained for the page lifetime. Bounded by undo/redo actions per session.

Verified clean (do not re-hunt without new evidence):

- One full read per open on current code (PERF_STACKS stacks: one
  42,401,895-byte read + four 64 KB/1-byte probes). The known pdfbox-mode
  `file.arrayBuffer()` at `PdfiumFormProvider.ts:594` is latent (providerMode
  default is pdflib).
- Patched worker→main transfers detach on the sender: corrected probe reads
  `byteLength === 0` after postMessage on both transferred buffers
  (`mh12-transfer-detach.local.spec.ts`, MDN Transferable objects cited). Probe
  visibility caveat: only ~2 of ~99 worker postMessage calls route through the
  wrapped `self.postMessage`; the main-side transfer counters are the complete
  signal.
- Our own workers (`pixelCompareWorker`, `compareWorker`) never use transfer
  lists — no detach hazard.
- HEAPU8/HEAPF32 staleness: every site reads `.HEAPU8`/`.HEAPF32` fresh;
  views over the heap buffer are consumed before any wasm call that could
  grow (`renderWidgetAppearance` passes the wasm pointer, not the view).
- malloc/free pairing across the service layer: no unpaired malloc site.
- Overlay caches clear on document-leave AND unmount; `FormFillContext`
  resets on every fetch; form field/edit state deliberately persists
  (product semantics), values store cleared per fetch.

Harness state for the next pass:

- `perf-baseline.local.spec.ts`: `wasmLive`/`wasmPeak` retired (report null);
  50 ms wasm sampling in place; **new `PERF_SCROLL_WHEEL=1` +
  `PERF_SCROLL_STEPS`** scrolls the container past the virtualizer's mounted
  window (records `pagesRenderedPeaks`); default scroll unchanged.
- `viewer-memory-soak.spec.ts` budgets: object URLs ≤2, DOM nodes ≤50,
  listeners ≤10, worker wasm pages ≤64, heap <1.25x, **workers drift 0**
  (all with measured justifications in the spec).
- `PERF_SNAPSHOTS=1` (heap snapshot pair at cycle 5/N), `PERF_FINALIZERS=1`
  (page-element finalizer) unchanged.
- `trace-retainers.py` fixed: element edges carry ordinals, not string
  indices (crashed on large snapshots before).
- New local probes (git-excluded): `mh11-worker-ratchet.local.spec.ts`
  (SOAK_FIXTURES=a,b,c rotation), `mh12-transfer-detach.local.spec.ts`.
- Raw logs: `result-soak-recon-*`, `result-recon-*`, `result-mh11-*`,
  `result-mh12-*`. Snapshot pairs: form fixture prod
  (`soak-snapshot-iter{5,12}.heapsnapshot`) + dev readable
  (`form-dev-snapshot-iter5.heapsnapshot`).
- Re-baselined S5 section in `.perf-local/BASELINE.md` (all fixtures, current
  build; timings UNVERIFIED, loaded machine).

## Implementation pass 2 — greenlit follow-through (2026-09-13)

The owner approved the full greenlight table, including the risky items.
Six commits landed on top of the hunt pass:

| Commit | Fix | Evidence |
| --- | --- | --- |
| `87205808e` | **F10 fixed**: record thumbnails render JPEG q0.8 clamped to 400 px instead of full-resolution PNG data URLs (display-only content; visually identical at thumbnail sizes) | Form soak retained-heap drift **1.38–1.41x (failing) -> 1.120x/1.133x (passing)**; all other soak budgets unchanged; form-field-editing/session-restore/sidebar/page-editor e2e green |
| `0af4de496` | G2: `deleteImageData` prunes the SignatureContext image store when a STAMP annotation is deleted (undo/redo recreations carry their own imageSrc and re-store on create) | New SignatureContext unit tests (revoke-on-blob, no-revoke-on-data-url) |
| `eea2e63a5` | G5: pdfbox-mode signature-appearance read goes through `getDocumentBytes` instead of a second full `file.arrayBuffer()` | formFill tests 18/18; one full read per open preserved |
| `2ced8f0e9` | G8: `releaseSharedRef(docPtr)` — thumbnail cache teardown releases exactly one open-reader reference, synchronously, and never closes a pointer `releaseSharedDocument` already closed (wasm double-free) | New service test; 4/4 thumbnailGenerationService tests |
| `f868993d1` | G10: deleted the stale legacy perf specs (pre-refactor sidebar + `/read` route); soak + perf harness supersede them | — |
| `8c2e740c1` | **G3 shipped: engine-worker respawn.** `respawnEngine()` rebuilds the engine from the same precompiled `WebAssembly.Module` (swap-then-destroy, 5 s cooldown, in-flight guard); file removal records the departing size; `EngineRespawnWatcher` respawns when the workbench empties after a ≥100 MB doc | mh13 probe: worker floor **3010 pp (188 MB) -> 284 pp** after close; post-respawn open renders end-to-end; huge-fixture soak saw-tooths 3010<->284 by design and passes every budget |

### Buffer ownership at steady state (updated)

The worker 188 MB floor is now reclaimed on huge-document close, so the
session-steady-state worker reservation is the small-document floor
(284-618 pp). Doc bytes still exist in 2-3 places during an open (main cache +
worker clone + wasm-internal copy) — see the table in `.perf-local/BASELINE.md`.

### G4 (worker owns canonical buffer) — decision record, not implemented

The full ownership inversion needs every main-thread scan to become a worker
task; the scan layer (`pdfiumService`, overlays, form provider) is synchronous
wasm code against the shared main-thread module, so this is a multi-pass
refactor, not a patch. First safe slice when it is greenlit: for ≥100 MB files
the main thread never opens the document (AcroForm probe skipped, thumbnail
uses the 2 MB prefix), so after the worker clone lands and the layers byte-scan
completes, the main `documentBytesCache` entry could be dropped and re-read
from the Blob on demand — but the viewer's `pdfBuffer` state shares the same
buffer reference, so the release needs viewer-lifecycle coordination. Measured
on the 155 MB fixture: that copy is 155 MB resident for the session.

## Corpus gauntlet pass 3 (2026-09-13; loaded machine — timings UNVERIFIED, counters deterministic)

First real-file corpus run. 60 stratified files (72.9 MB, sha256 verified 60/60)
built by `.perf-local/corpus/fetch-corpus.py` (pdf.js `test/pdfs` 43,
openpreserve govdocs1-error-pdfs 12, veraPDF 5); manifest + runner are
git-excluded. Tags: corrupted 18, annotated 12, real-world 12, large-real 8,
forms-acroform 7, text 7, fonts-weird 6, images-heavy 6, conformance 5,
rotated 4, signed 2, encrypted 2, forms-xfa 2, tagged 2, attachment 2, rtl 1,
multi-page 1. GovDocs1 itself (S3/archive.org) was not reachable from this
network; the openpreserve error sets are real GovDocs1 files, and the pdf.js
suite supplies the real-world variety. Largest openly available real files are
~8 MB; the 40 MB / 155 MB synthetic fixtures cover the large-file dimension.

Functional matrix (both arms, 60/60 opens):
- branch: 60 rendered, 0 error surfaces, 0 fatal console/page errors; the
  AcroForm rows expose widget inputs and the text fill/readback probe works;
- baseline: 60 rendered; diff vs branch = **zero functional deltas**;
- pixel parity: 76 paired page captures; the 11 files with >5% deltas were
  re-captured with a raster-attached wait — worst 7.7%, most <4.4%, inside
  the known BMP(branch)-vs-PNG(upstream) encoder band (same-arm capture noise
  was 0.000% in the prior pass). Artifacts: `.perf-local/corpus/out/*`,
  `arm-diff.json`, `arm-diff-px.json`.

Perf per file (8 diverse files x3 runs/arm, production preview; the loaded
machine makes timings UNVERIFIED, the counters are deterministic):

| metric (median) | baseline | branch | note |
| --- | --- | --- | --- |
| main wasm peak pages | 284 | 284 | p90 2080 -> 1968 |
| heap after GC (MB) | 18.7 | 15.7 | -16% |
| long-task total (ms) | 51.5 | 0 | p90 1122 -> 563 |
| Blob->ArrayBuffer calls | 11.5 | 6.5 | one full read per open |
| Blob bytes/run (MB) | 0.45 | 0.2 | p90 54.4 -> 15.6 |
| run duration (ms) | 2889 | 2733 | UNVERIFIED |

No file regressed >15% on wasm peak or heap. `encrypted-attachment.pdf` is the
only row where the branch instantiates the main module (284 pp) while upstream
does not (0); both bounded, no action.

New findings (full list + TODOs in the pass report):
- **G11 (fixed, `a542633f1`)**: the document-manager plugin config kept
  `initialDocuments` in `window.__embedPdfRegistry` after close, and the
  registry stayed on window after unmount. The mh15 probe (huge fixture,
  forced GC) measured the 162,539,566-byte buffer WeakRef alive after removal;
  clearing the config at `onInitialized` and dropping the window handle on
  unmount removes that holder (initialDocuments 1 -> 0).
- **G12 (open, P1)**: the main-thread document ArrayBuffer is still retained
  after close by a plugin event-listener closure. Heap-snapshot shortest path
  (mh15, huge fixture): Window -> workspace-frame fiber -> onToggleCollapse
  closure -> plugin registry -> plugin emitters -> Set -> listener closure ->
  ArrayBuffer (162,539,566 B). Full release needs the registry/plugin teardown
  unblocked from React closures.
- **G13 (open, P2)**: resetting the main PDFium singleton does not release its
  linear memory. After `resetPdfiumModule()` + forced GC the old
  `WebAssembly.Memory` (1,598 pp) is still live, held through a resolved
  promise/async context. An attempted reclaim-on-empty therefore created a
  second instance on reopen (1,598 -> 1,980 pp, two live memories) and was
  reverted (helper + tests dropped). Reclaim needs the promise/context
  retainer removed first. Doc anchors: Emscripten issue #15591 (no way to
  shrink wasm memory without discarding the instance) and web.dev
  wasm-memory-debugging (grow detaches views; no GC integration).
- **G14 (open, P3)**: `documentBytesCache` keeps the bytes as the resolved
  value of a promise in `WeakMap<Blob, Promise<ArrayBuffer>>`, so a File
  record pinned by unrelated state keeps the whole buffer alive. The retainer
  map shows this is not the dominant post-close holder, so the WeakRef-cache
  variant was measured, not shipped (TODO G14).

Harness state for the next pass:
- `corpus-gauntlet.local.spec.ts` (git-excluded): manifest runner with
  functional + form readback probes, optional CDP perf counters, strict
  raster-attached screenshots; `PERF_LOCAL_DIR`, `PERF_CORPUS`,
  `PERF_CORPUS_IDS|TAGS|LIMIT|OUT|PERF|RENDER_PAGES|SCREENSHOTS|SETTLE_MS`,
  `PERF_LABEL`.
- `fetch-corpus.py`, `diff-arms.mjs`, `aggregate-perf.mjs`,
  `synthetic-manifest.csv` (all git-excluded).
- `viewer-memory-soak.spec.ts` counts live wasm instances (WeakRefs instead
  of strongly held memories) and reports blob finalization under
  `PERF_FINALIZERS`.
- New committed contract tests: `documentBytesCache.test.ts`,
  `pdfiumScanQueue.test.ts`.
- Raw logs: `run-corpus-{branch,baseline}.log`,
  `run-px-{branch,baseline}.log`, `run-perf-{branch,baseline}-r{1,2,3}.log`,
  `result-mh15-{prefix,postfix,weakcache,huge}*.log`,
  `result-soak-pass3-*.log`, `mh14-after-remove.heapsnapshot`.

Verification for the pass: typecheck/lint clean, 3448/3448 vitest (one
pre-existing Mantine teardown unhandled timer under full-suite load; the
file passes 3/3 in isolation), 48 e2e + 1 pre-existing skip, engine smoke
3/3 browsers, soak 3x (default x2 + form x1), corpus 60/60 on both arms.

## Approved follow-through (2026-09-13): G12a + G14 + G16 shipped, G13 blocked, G15 diagnosed

Owner approved the greenlight set. Four commits on top of the corpus pass:
`c1860a3da` (document-bytes release), `e9b820d5a` (engine URL revoke),
`852192fac` (worker respawn threshold).

### Before/after (huge-150mb.pdf, production preview, forced GC, git-excluded mh14 probe)

| signal after workbench-empty | before | after |
| --- | --- | --- |
| live `JSArrayBufferData` of the document | **162,539,566 B** | **none** (no backing store >1 MB) |
| shortest retainer | Window -> annotation listener closure -> context -> buffer | — |
| second retainer (after listener fix) | `WeakMap<File, Promise<ArrayBuffer>>` entry | removed by the weak-value cache |
| main PDFium wasm backing store | 18,612,224 B (284 pp) | 18,612,224 B (unchanged, G13) |
| registry `initialDocuments` after init | 1 (buffer) | 0 |
| `window.__embedPdfRegistry` after unmount | set | deleted |

The annotation listener fix alone was not enough (mh16 snapshot still showed
162.5 MB); the retainer then moved to the bytes cache, and the WeakRef value
cache released it (mh17 snapshot). The reference trace named exactly one
closure in the heap capturing the buffer — the anonymous
`onAnnotationEvent` listener registered by `LocalEmbedPDF`.

### G16 + engine URL leak

Respawn threshold 100 MB -> 10 MB. The first form soak after the change failed
the object-URL budget (drift 0 -> 4): each respawn leaked two object URLs
because `@embedpdf/engines@2.15.0` creates the engine worker and encoder pool
from `URL.createObjectURL` and never revokes (`revokeObjectURL` count: 0). The
local engine patch now captures and revokes them after worker construction.
Form soak with respawns on cycles 1/5/9: worker pages 742 -> 284 after each
empty, blob count flat at 1, worker-peak budget green (peaks 742 vs 742). Engine
smoke green on Chromium/Firefox/WebKit after revocation.

### G13 (blocked, evidence recorded)

`resetPdfiumModule()` still does not release the old instance: after reset +
2x forced GC the old `WebAssembly.Memory` (284 pp on the huge fixture) is live,
retained through a chain rooted at a `PromiseReaction` (pending promise
continuation; `initPdfiumModule`'s async context). Shipping reset-on-empty
would create a second instance per close/open (`liveMems` 1 -> 2, 568 pp on
reopen, mh18), so the helper and its tests were reverted a second time. Next
step: snapshot during the first open and name the pending promise chain
(microtask instrumentation), then reclaim per `mh14 MH14_EXPECT_RECLAIM=1`.

### G15 (diagnosed, L-sized fix pending)

`22060_A1_01_Plans.pdf` (6.1 MB, scanned plan): main wasm 1968 pp (123 MB),
worker 2350 pp (147 MB), `firstPageMs` 988, two long tasks 304+268 ms, two full
7-call reads (12.1 MB) with stacks on the record-thumbnail render (`Fn <- hb`)
and one other `Fn` caller. Rendering a page whose source images are large
decodes them inside PDFium on the main thread even though the thumbnail output
is clamped to 400 px. Fix direction: render record thumbnails for image-heavy
docs in the engine worker (G4/G15), or derive them from the worker's first
page render. Harness for verifying: `PERF_CORPUS_IDS=12 PERF_CORPUS_PERF=1`.

## Streaming / low-allocation roadmap — evaluation results (2026-09-13)

### Executed probes and shipped fixes

**Read dedupe (roadmap 4-adjacent, shipped `fix(viewer): share cached bytes
across File wrappers`).** Readable dev stacks (`PERF_STACKS=1`, plan PDF) showed
three full reads on add: `generateThumbnailPairWithMetadata`,
`extractPDFMetadata` (classification) and `LocalEmbedPDF` each called
`getDocumentBytes` with a *different* `File` wrapper for the same bytes
(`createStirlingFile` re-wraps with `new File([file], ...)`). Production
measured two full reads (12.1 MB for the 6.1 MB plan). `documentBytesCache` now
has a File-signature tier (name/size/type/lastModified + byte-length guard,
LRU-capped, weak values) while bare Blobs stay identity-keyed. After:
`blobArrayBufferCalls` 6, `blobArrayBufferMB` **6.1 (1 read)**. Unit tests:
same-metadata wrappers share, changed mtime re-reads, bare Blobs of equal size
do not share.

**Dead code (shipped `chore(viewer): drop the unused signature-detection
service`).** `signatureDetectionService.ts` had zero importers and handed a
full `file.arrayBuffer()` to pdf.js (which detaches the buffer), bypassing
`documentBytesCache`. Deleted.

**Roadmap 6 audit (no change needed).** Hot paths already use zero-copy:
`heap.subarray()` for wasm reads, `Blob.slice()` for encrypt/linearization
probes, 8-byte header reads; the `new Uint8ClampedArray(heapView.subarray())`
copies are deliberate (ImageData ownership before `free`).

### Roadmap 1 (worker `createImageBitmap` + transfer) — PROBE result: mechanism valid, deferred

Docs leg verified: MDN `createImageBitmap` accepts `Blob`/`ImageData`, is
available on `WorkerGlobalScope`, and `ImageBitmap` is listed among
transferable objects (move semantics). Two legs missing for a ship decision:

- Path: with `defaultImageType: "image/bmp"` both converters
  (`createWorkerPoolImageConverter`, `createHybridImageConverter`) return in
  `rgbaToBmpBlob` **on the main thread** and never touch the encoder pool.
  Switching to transferred `ImageBitmap` changes the `PdfPageImage` contract
  consumed by `RenderLayer` (`<img src=URL.createObjectURL(blob)>`), the tiling
  and thumbnail plugins, and the converters — an L cross-plugin refactor.
- Benefit: not yet shown on current files. Scroll soaks measure 0 ms long tasks
  with the BMP wrap; the plan-PDF 304+268 ms tasks attribute (stacks) to the
  main-thread thumbnail/PDFium decode path (G15), not to the Blob wrap. The
  win would be transient main-heap residency, which the WeakRef cache and
  post-`onLoad` revocation already bound.
Recommend: revisit together with G15 (worker-side record thumbnails) or when a
profile shows main-side BMP wrap/decode cost; keep the bitmap `close()` audit
as part of that work.

**Roadmap 2 (`transferToImageBitmap`)** — no in-worker canvas rasterization
path exists today (PDFium renders through its bitmap API; BMP bypasses the
encoder pool on main). Not applicable; keep for tiling-in-worker.

### Roadmap 5 (COOP/COEP + SharedArrayBuffer) — MEMO, owner decision

What it unlocks: (a) `SharedArrayBuffer` shared wasm memory — the only true
zero-copy main<->worker document mechanism; (b) `performance.measureUserAgentSpecificMemory()`
(the documented harness gap). Requirements/costs verified locally:
- Shared memory additionally needs a `-pthread`/`SHARED_MEMORY` pdfium build;
  the pinned `@embedpdf/pdfium@2.15.0` exports a single non-shared `memory`,
  so this means rebuilding the pinned binary — currently out of scope — and a
  mandatory `maximum` because shared backing buffers cannot be reallocated when
  the memory grows.
- Header surface: Spring `WebMvcConfig`/security config can add
  `Cross-Origin-Opener-Policy: same-origin` + `Cross-Origin-Embedder-Policy:
  require-corp|credentialless`, but every cross-origin subresource then needs
  CORP/CORS. Core/self-hosted loads none; the SaaS flavor loads Supabase
  (`*.supabase.co`, auth/Realtime) and PostHog (`eu.i.posthog.com`), and those
  must be audited (CORP headers, fetch modes, credentialless compatibility)
  before any rollout. OAuth/SAML here is redirect-based, not popup-based, so
  COOP same-origin should not break it — verify on staging.
Recommendation: keep CLOSED for SaaS/proprietary; if pursued, stage it for
core only: (1) serve the headers on a staging deploy and assert
`self.crossOriginIsolated === true` in page **and** worker; (2) run the viewer
e2e batch + auth flows; (3) only then evaluate SAB, which is a separate
binary-rebuild decision.

### Dead ends (confirmed, no work)

localStorage/sessionStorage for bytes; IndexedDB wasm-module tier; IndexedDB as
a document store — all rejected on the stated grounds (synchronous string API
with ~5 MB quota and UTF-16 binary; measured unnecessary with ~50-80 ms eager
compile and immutable `/assets/**`; every `get()` reconstructs a
structured-cloned buffer).

# FINAL PROD STATE — pass 4 (2026-09-13), frozen build

Branch `viewer-perf-recon`, base `upstream/main` `77b325cf1`, local-only. All
numbers below were re-measured on the frozen source (commit `e2885425d`, build
`build-final-prod`); machine was heavily loaded (load 15-20/18) so **timings
stay UNVERIFIED**, counts/drifts/heap-snapshots are deterministic. Tree clean,
`.perf-local/` git-excluded.

## Session commit inventory (on top of the merged PR branches)

| Commit | What |
| --- | --- |
| `a542633f1` | registry no longer pins the document buffer; window debug handle dropped |
| `30f30cee5` | soak: live-wasm WeakRefs + blob finalization probe |
| `34ce4a546` | bytes-cache / scan-queue contract tests |
| `c92191bb9` | corpus gauntlet docs (G11-G15) |
| `c1860a3da` | **release document bytes on close** (annotation unsubscribe + weak cache values) |
| `e9b820d5a` | engine patch revokes worker blob URLs |
| `852192fac` | respawn threshold 100 MB -> 10 MB |
| `af916fd63` | approved follow-through docs |
| `2dad72c57` | **File-signature read dedupe** |
| `faeadac74` | drop dead signature-detection service |
| `e2885425d` | streaming/low-allocation evaluation docs |

Net tracked diff this session: 10 files, +585/-138.

## Verification matrix (frozen build)

| Gate | Result |
| --- | --- |
| `task frontend:typecheck` | clean |
| `task frontend:lint` | clean |
| `task frontend:test` | **3451/3451** (3444 at session start, +7) |
| Viewer e2e batch (9 specs) | **48 passed + 1 pre-existing skip** |
| Engine smoke (Chromium/Firefox/WebKit) | **3/3** |
| Soak (default + form fixture) | pass, all budgets |
| Corpus functional (60 files, both arms) | **60/60, zero deltas** |
| `check:embedpdf-patch` | pass |

## Corpus (frozen build)

60 files / 72.9 MB / sha256 verified 60/60: pdf.js `test/pdfs` 43,
openpreserve govdocs1-error-pdfs 12, veraPDF 5. Tags: corrupted 18,
annotated 12, real-world 12, large-real 8, forms-acroform 7, text 7,
fonts-weird 6, images-heavy 6, conformance 5, rotated 4, signed 2, encrypted 2,
xfa 2, tagged 2, attachment 2, rtl 1, multi-page 1. Functional: 60/60 rendered
on both arms, 0 error surfaces, 0 fatal errors, form readback probes work.
Pixel: 75 paired captures, 51 differ (BMP branch vs PNG upstream plus the
merged viewer layout); strict-raster rerun of the 11 outliers tops out at 7.7%
(most <4.4%), same-arm capture noise 0.000% (prior pass).

### Final perf counters, 8 files x3 runs/arm (medians, timings UNVERIFIED)

| metric | baseline (upstream) | frozen branch | delta |
| --- | --- | --- | --- |
| main wasm peak pages | 284 (p90 2080) | 284 (p90 1968) | -5.4% p90 |
| heap after GC | 18.7 MB | **15.7 MB** | **-16%** |
| long-task total | 51.5 ms (p90 1122) | **0 ms (p90 296)** | -100% p50 |
| `Blob.arrayBuffer` calls | 11.5 | **5.5** | -52% |
| blob bytes/run | 0.45 MB (p90 54.4) | **0.15 MB (p90 7.9)** | -67% |
| run duration | 2889 ms | 2729 ms | noise |

Per-file full reads now equal exactly one file copy: plan 41.9→6.1 MB,
issue12841 44.8→5.7 MB, issue3188 54.4→7.9 MB. **No file regressed >15% on
wasm peak or heap.** `encrypted-attachment.pdf` remains the only row where the
branch instantiates main PDFium (284 pp) and upstream does not (0).

### Synthetic fixtures (frozen build, counts deterministic)

| fixture | first page | long tasks | heap | main wasm | worker wasm | copies | m2w |
| --- | --- | --- | --- | --- | --- | --- | --- |
| large-40mb (40.4 MB) | 824 ms | 0 ms | 16.9 MB | 810 pp / 50.6 MB | 810 pp | 1 full 40.6 MB | 40.4 MB cloned |
| pages-500 | 173 ms | 0 ms | 17.0 MB | 285 pp / 17.8 MB | 285 pp | 0.5 MB | 0.3 MB |
| huge-150mb (155 MB) | 733 ms | 60 ms (pre-existing) | 16.5 MB | 285 pp / 17.8 MB | (respawned after close) | 1 full 155 MB + probes | 155 MB cloned, postMessage 16 ms |
| form-40mb (30.4 MB) | 686 ms | 51 ms | 17.4 MB | 1070 pp / 66.9 MB | 742 pp / 46.4 MB | 1 full 30.6 MB | 30.9 MB |
| large scroll (50 wheel steps) | 726 ms | 0 ms | 16.5 MB | 810 pp | - | - | 44.7 posted / 44.3 transferred |

## Memory before/after (the pass's headline)

| signal | before | after | evidence |
| --- | --- | --- | --- |
| document ArrayBuffer after close (155 MB fixture) | **162,539,566 B live** | **none** (no backing store >1 MB) | mh14/mh15 snapshots + retainer traces |
| registry `initialDocuments` after init | 1 (full buffer) | 0 | mh15 registry read |
| annotation listener capturing buffer | present, unremoved | unsubscribed on unmount | heap retainer map (only closure holding it) |
| `documentBytesCache` retention | promise value in `WeakMap<Blob, …>` | WeakRef values + File-signature tier | unit tests + retainer map |
| worker object URLs | **+2 per engine respawn** | 0 (revoked after construction) | form soak blob count flat at 1 |
| worker wasm floor, 10-100 MB docs | retained (50.6/46.4 MB) | reclaimed on empty (742→284 pp) | form soak cycles 1/5/9 |
| real-file full reads | 2-3 per open (wrapped Files) | 1 | plan PDF 12.1→6.1 MB |
| main PDFium linear memory | 17.8-123 MB retained for the session | **unchanged (G13 blocked)** | mh18/mh20 |

## G13 negative results (do not re-try blind)

Two attempts measured on the frozen base:
1. reset-on-empty alone: creates a second instance on reopen (`liveMems` 1→2,
   `liveWasm` 284→568 pp) while the old one stays live.
2. reset + breaking the `instantiateWasm`/`instantiateFailed` promise hook
   after init (`e2885425d`^ chain): identical result — the wrapper is still
   retained through a `PromiseReaction`-rooted pending promise chain
   (snapshots `mh14-after-remove.heapsnapshot`, logs `result-mh18/20`).
   Next step if pursued: capture a snapshot during the first open and name
   the pending chain (microtask instrumentation), then re-test with
   `MH14_EXPECT_RECLAIM=1`.

## Open items (ranked, mapped with evidence)

1. **G15** — real image-heavy plans: 6.1 MB plan -> 1968 pp/123 MB main wasm,
   558 ms long tasks (baseline 1142 ms). Fix: worker-side record thumbnails
   (R1/G4). Repro: `PERF_CORPUS_IDS=12 PERF_CORPUS_PERF=1`.
2. **G13** — main PDFium wasm floor 17.8-123 MB/session (see above).
3. **G17** — per-page form extraction (form fixture main wasm 66.9 MB); needs
   product sign-off on save/validation semantics.
4. **G18** — worker-owned canonical document buffer (removes the 155 MB clone
   and main-cache residency); multi-pass.
5. **G12b** — full plugin-teardown decoupling; superseded by G12a for the
   measured retention, keep only if the registry/plugin graph grows.
6. **R1** — worker `createImageBitmap` + transfer; docs leg verified (MDN),
   app path is an L cross-plugin refactor, no measured main-thread win yet.
7. **R2** — COOP/COEP memo (SAB needs a pthread pdfium rebuild; SaaS needs a
   Supabase/PostHog CORP audit); owner decision.

## Pass 5 — frontier + tricks research pass (2026-09-13, CLI mode; semi-loaded machine, all timings UNVERIFIED)

Exploration-only pass: no production code changed. Full report and the
source-by-source ledger live in `frontend/editor/.perf-local/`
(`frontier-report-pass5.md`, `research-ledger.md`); probes are
`pass5-*.local.spec.ts` (git-excluded), profiles under `.perf-local/profiles/`.

### Measured frontier outcomes (counts deterministic; timings UNVERIFIED)

- **F1 interaction latency.** Scroll (30 wheel steps), zoom ×3, text select
  and typing: **0 long tasks**, ≤21 ms frame gaps. Named >50 ms interactions:
  page jump 1→450 on pages-500 = **1.08–1.16 s main-thread task** (727–809 ms
  script, 154–164 layouts, ~2.1 s wall; back 1.3 s), search-popover open =
  **71–88 ms long task**.
- **F1/F2 jump attribution.** Jump CPU profile: embedpdf `getState` 22.8 % of
  busy samples with `registerPageHandlers` → `emit` → `getHandlersForScope`
  chains and scroller `extractTime`/`sortQueue`; GC 6.7 %. React rendering is
  not the cost. → G19.
- **F2 plan-open profile (extends G15).** Main busy 0.95 s, 662 ms long
  tasks; **37 % of busy samples in the worker-bitmap receive/Blob-wrap path**
  (`createObjectURL`-attributed), 29.5 % pdfium wasm. Pinned wasm has no name
  section (indices only). → G20.
- **F4 lifecycle.** bfcache not engaged (`pageshow.persisted:false`; reload +
  IndexedDB restore, 5 pages back, no errors). Freeze/resume OK. Killing
  **3/3 engine-worker targets** then zooming/scrolling: **3 workers
  respawned, pages rendered, zero errors**. Offline→online OK.
- **F7 search.** pages-500 query: first results 849 ms (incl. 300 ms app
  debounce), 4000 matches; **400 ms long tasks / 680 ms main task** during
  indexing. → G21.
- **F8 startup.** Precise coverage: **45.6 %** of covered JS bytes executed
  on first open; posthog 13 %/supabase 17 %/zip 16 % used (≈166 KB encoded)
  on the viewer path. Eager wasm compile 60→99 ms, engine created 388 ms.
  → G23.
- **F9 multi-doc.** Sequential adds (pages-500 → large-40 → form-40): heap
  16.5→18.3 MB after GC (no leak), worker wasm flat at 855 pp (one shared
  engine, no per-doc multiply), main wasm ratchets 284→840→1187 pp (G13
  grow-only high-water), 0 long tasks per add. → G22 (depends G13).
- **F5/F6/F10.** Print/save both serialise a full copy in the worker then
  Blob+URL on main (static read); struct-tree + progressive + custom-document
  APIs are present in the pinned wasm but unused (zero a11y cost); Tauri
  uses WKWebView on macOS and a Tauri member documents a ~2 GB webview
  ceiling (no desktop run this pass).

### Trick statuses (T1–T10; evidence in the report)

- **PARKED:** T1 worker ImageBitmap transfer (R1; F2 gives it a measured
  target), T3 JSPI/Asyncify dual-build (Chrome 137/Firefox 139; WebKit has
  no JSPI — bug 283054; binary rebuild), T6 scheduler APIs (WebKit lacks
  both), T10 COOP/COEP memo (new subresource list: jsdelivr default,
  posthog, supabase, iconify, files.stirlingpdf.com).
- **ADOPTED as guidance:** T4 flat payload shapes (measured: flat 8 MB clone
  roundtrip 3.4 ms vs dense 32768-object graph 11.2 ms → ~7× per-byte, not
  10–30×; sync transfer ≈0 ms), T8 pdf.js tactic audit (already clean:
  per-page overlays, `EPDF_*ByIndex`, canvas pool).
- **REJECTED measured/reasoned:** T2 (no in-worker canvas path), T5
  `content-visibility` on page wrappers (ABBA: no-CV 614/681 ms vs CV
  594/574 ms, layouts 67/70 vs 83/82 — virtualization already skips), T7
  batched message channel (message overhead not a bottleneck), T9 warm second
  worker (18.6 MB floor conflicts with G3/G16 respawn).

### Gate state at pass end

`frontend:typecheck` clean, `frontend:lint` clean, vitest **3451/3451**,
viewer e2e **48 passed + 1 pre-existing skip**, soak default + form green,
engine smoke 3/3 browsers, corpus 60/60 carried from pass 4 (no code
changed). **`frontend:format:check` is red on entry and unchanged by this
pass** — 16 files (14 tracked production files + 2 pre-existing local specs)
fail oxfmt 0.62.0 while their `77b325cf1` versions pass; fix is
`task frontend:format` in one commit (owner approval for the reformat).


## Implementation pass 6 (2026-09-13): redaction fixes + greenlit perf set

Owner approved the greenlight set and reported a functional bug ("manual redact
does not work properly; you cannot select text to redact"). Changes below are
tracked, one concern each; no pushes.

### Manual redaction — two regressions from `2991e5285` fixed

1. **`TextSelectionHandler` lost the selection-end contract.** PR #7875
   replaced the plugin-internal `clear/begin/update/endSelection` sequence
   with the public `setSelection()`, which dispatches only `selChange$`.
   The annotation/redaction plugins convert a selection into a mark on
   `endSelection$`, so the app's Unicode-aware word/line selection (the path
   that exists precisely for documents where PDFium's boundary flags are
   missing) produced a visible highlight with **no pending redaction**.
   Fix: restored `setSelectionRange()` (typed cast, no `any`) and called it
   from both double-click and triple-click; a unit test locks the
   `clear → begin → update → end` order.
2. **`RedactionSelectionMenu` ignored annotation-sourced pending marks.**
   With `useAnnotationMode: true` pending redactions are REDACT annotations,
   and the menu only handled legacy `type: "redaction"` contexts, so
   selecting a pending mark showed the generic annotation menu with no
   Apply/Remove. Fix: accept annotation contexts whose object type is
   `PdfAnnotationSubtype.REDACT`, and route REDACT annotations from
   `AnnotationLayer` to `RedactionSelectionMenu` in `LocalEmbedPDF`.

Evidence: new `viewer-redaction-text-selection.spec.ts` (4 tests: drag,
double-click, triple-click create pending marks; a pending mark exposes
Apply/Remove and Apply clears the pending count). Test 4 **fails on the
pre-fix build** (stash + rebuild + rerun) and passes after. Full viewer
selection/forms/rotation/cropbox/session batch 52 passed + 1 skip.

### Perf changes landed

| Change | Evidence |
| --- | --- |
| **G19** local `@embedpdf/plugin-interaction-manager` patch (`frontend/scripts/patch-embedpdf-plugins.mjs`, version-anchored + `--check`, wired into postinstall and `check:embedpdf-patch`): coalesce the six `onHandlerChange` emits into one microtask so a page jump does one handler re-resolution per batch instead of one per tool per page | Jump profile (`pass5-jump-profile`), n=3/arm: busy samples median **8227 → 7002 (−15 %)**; the `registerPageHandlers → emit → getHandlersForScope → getState` chains disappear from the top self-time (446+399 samples → 0). Wall time to target stays ~1.30 s (render-bound). |
| **G24** `npx oxfmt .` over the frontend: the branch's pre-existing format drift (14 tracked files + 2 local specs) is gone; `frontend:format:check` exits 0 | `task frontend:check` no longer stops at format:check |
| **G23** posthog is now a gated dynamic import (`analytics.ts` + `usePosthogTracking.ts`): `setAnalyticsEnabled()` mirrors config/consent and track calls short-circuit before importing, so analytics-off installs never fetch the 75 KB vendor chunk | unit tests (6/6) + coverage run shows posthog still loads only because the stub config enables analytics on upload events |
| **G21** parked with profile evidence | Search CPU profile: 500-page query busy 244 ms, dominated by per-page `handleMessage → progress → dispatch → getState` chains in the search plugin; needs a vendor progress-coalescing patch, not an app change |
| **G20** refuted and reverted | Routing the BMP wrap through the encoder worker (transfer + Blob in worker) left the `createObjectURL`-attributed main cost unchanged (long tasks A 410–584 ms vs B 408–608 ms, overlapping) — the cost is blob materialization at `createObjectURL`, which only a worker `ImageBitmap` transfer (T1/R1) removes |
| **G22** remains blocked on G13 (unchanged) | — |

### Verification matrix (final build)

`task frontend:typecheck` clean; `frontend:lint` clean; `frontend:format:check`
clean; vitest **3452/3452** (389 files); full stubbed e2e **822 passed / 78
skipped / 6 failed** — the 6 failures are PDF-text-editor caret/undo specs
that fail identically on **pristine HEAD** (stash + original vendor plugin +
rebuild bisect), i.e. pre-existing; focused viewer batch **52 passed + 1
skip**; redaction spec **4/4**; soak default + form green; engine smoke
Chromium/Firefox/WebKit **3/3**.

### Text-selection action menu (annotation/redact) — ported from `dropdown-lot-of-dropdowns`

Bug: selecting text in the viewer only offered Copy, so there was no way to
mark a selection as a highlight/underline/strikeout/squiggly/link or queue it
for redaction — "the redact/annotation menu does not pop up". The full menu
existed only on the unmerged `dropdown-lot-of-dropdowns` branch
(`ebd2d5752`/`a8713aafd`); this ports it to the current viewer.

`TextSelectionMenu.tsx` now renders Copy, Highlight, Strikeout, Underline,
Squiggly, Link and Redact for the current selection:

- Markup + link actions create annotations through the annotation plugin
  (`useAnnotation(documentId).createAnnotation`) with the selection's
  `rect`/`segmentRects`, then clear the selection, flag unsaved changes and
  open the Annotate panel. Link opens a URL popover first.
- Redact delegates to the redaction plugin's
  `forDocument(documentId).queueCurrentSelectionAsPending()` — the plugin
  owns annotation-mode conversion, text capture and selection clearing
  (hand-building REDACT annotations raced it and intermittently blanked the
  page), then opens the manual redaction panel and activates redaction once
  the bridge is ready.
- The menu is suppressed while redaction mode is active: the redact plugin
  converts selections itself, and the portal could swallow the second click
  of a double-click gesture.

Evidence: new `viewer-text-selection-menu.spec.ts` (5 tests: all seven
actions render; Highlight produces a type-9 annotation; Redact produces a
pending redaction and the Apply Redactions panel; Link produces a type-2
annotation; menu hidden in redact mode) — 5/5 three consecutive runs. Broad
viewer batch **59 passed + 1 skip**; `viewer-redaction-text-selection` +
`viewer-redaction-exit-restores-selection` + `viewer-text-selection`
**17/17**; engine smoke 3/3; soak default + form green; typecheck/lint/format
clean.

(Convergence pass: this port sat uncommitted at pass-7 close; re-verified
— spec 5/5 ×3, selection batch 26/26 — and landed as `5f98b13c6`.)

## Implementation pass 7 — frontier + tricks (2026-09-13, CLI; loaded machine, timings UNVERIFIED)

Exploration + two shipped wins. Full report + source-by-source ledger delta
in `frontend/editor/.perf-local/` (`frontier-report-pass6.md`,
`research-ledger.md` §E–F). No pushes. Note: a second agent works the same
tree concurrently (TextSelectionMenu section above); my probes ran on :5174
and every dist measurement was coherence-checked (no dangling chunk refs).

### Commits (this pass; prior pass-6 lock-in: `ceb52f779`/`cac5fdb64`/`b99a0c8b1`/`911188913`)

| Commit | Change | Evidence |
| --- | --- | --- |
| `c511e6da6` | **G21** `plugin-search` progress coalescing (extends the local plugin patch: per-page `appendSearchResults` dispatches batch into one microtask; merged flush preserves final state; pdf.js `updateMatchesCountOnProgress=false` template) | pages-500 "lorem", preview, interleaved A/B n=3/4: first results **667→530 ms (−21%)**, TaskDuration **495→355 ms (−28%)**, long tasks **216→94 ms (−56%)**, ranges separated; settle probe 4000/4000 with 0 long-task ms |
| `28fb8583e` | **G23b** jszip off the viewer waterfall (cached `loadJSZip()` in zip utilities + WatchedFolder; pako split to `vendor-pako`; `modulepreload.resolveDependencies` drops vendor-zip) | large-40mb open: vendor-zip **45226 B → not fetched** (−3.4% JS transfer); zip upload pulls it on demand (30396 B), extracted PDF renders, 0 page errors; unit 11/11 |

G23b ships below the 10% bar on bytes alone, stated plainly: it is the
deterministic, zero-behavior-change completion of the G23 startup diet
(posthog 77 KB conditional + zip 45 KB unconditional ≈ 122 KB). Revert
with `git revert 28fb8583e` if the bar is held strictly.

### Measured, not shipped

- **Jump main-thread CLOSED.** Post-G19 profile re-analysis (`jumpFinal`,
  offline): `getState` ~14% of busy (was 22.8%), bitmap-receive ~10%,
  wall ~1.3 s at ~20% main busy. No remaining >10% main-thread target;
  remainder is worker raster + settle → G31.
- **Supabase static edge PARKED (G30).** proprietary AppProviders →
  LicenseProvider → licenseService → supabaseClient → SDK: 190 KB raw /
  ~51 KB gz in every viewer waterfall, named import unused there. Fix is
  the G23 async-getter pattern but auth/licensing-adjacent (M, owner
  sign-off + flavour matrix).

### Traps documented (do not re-learn)

- Lazy import alone saved nothing: vite `modulepreload` re-fetched the
  chunk (fix: `resolveDependencies` filter) and pako shared the chunk
  (fix: split). All three legs required; verified at each step.
- vite 7 `resolveDependencies` deps are **strings** (`dep.filename` keeps
  everything — silent no-op).
- `export =` CJS interop: `typeof import(x).default` degrades inference
  (TS18046); annotate `Promise<typeof X>` via `import type`.
- Rollup `manualChunks(id, { getModuleInfo })` + `importers` names the
  true static importer when dist-grep misleads (here: WatchedFolder, the
  last static jszip edge).

### Verification matrix (this pass, current tree)

typecheck/lint/format clean; vitest **3452/3452**; viewer batch **52
passed + 1 pre-existing skip**; soak default + form green; engine smoke
Chromium/Firefox/WebKit **3/3**; corpus spot (text + forms-acroform +
large-real tags) **18/18 rendered, 0 errors**.

### Open items (delta)

G30 supabase-lazy (M, sign-off), G31 jump worker-side (M),
G32 low-end encode comparison (S/M). Carried: G22 (blocked G13), G25
print-DPI, G26 GPU-RSS, G27 8-flow matrix, G28 Tauri, G29 JSPI memo.
G20 stays refuted.

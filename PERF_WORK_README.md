# Viewer performance work — branch `viewer-perf-recon` (local handoff)

Status: **local only, never pushed**. The working tree is clean and the branch is
kept for a 2nd/3rd agent pass. Do not push or open PRs without the owner's
explicit approval.

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
| first page rendered | 1055 ms | 873 ms | **715 ms** |
| long-task blocking | 636 ms | 54 ms | **0 ms** |
| full JS copies | 10 / 242.8 MB | 11 / 283.2 MB | **1 / 40.6 MB** |
| main-thread WASM high-water | 146.2 MB | 146.2 MB | **50.6 MB** |

### `huge-150mb.pdf` (155 MB, 60 pages)

| Metric | before | final |
| --- | --- | --- |
| first page | ~720-960 ms | **691 ms** |
| main-thread WASM high-water | **325.1 MB** | **17.8 MB** |
| full JS copies | 1 full read + slices | 1 full read + slices |
| crashes / OOM | none | none |

### `form-40mb.pdf` (30.4 MB, 60 pages, AcroForm text+checkbox fields)

| Metric | all-page scans | per-page lazy |
| --- | --- | --- |
| long-task blocking | 64 ms | **0 ms** |
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
BMP. (n=2 per arm — a human trial or a longer run would firm this up.)

## Phase 2 (felt moments) — U1 reading continuity

Phase 2 audits flows, not milliseconds: `ux/ux-flows.md` ranks every viewer
flow by tier gap × frequency. The first implemented flow is reopen continuity
(`86111af5a`): device-local reading position (page + in-page fraction + zoom)
keyed by the file's `quickKey`, restored on reopen with a non-blocking
"Resumed where you left off — [Go to start]" toast. Anchors are fractions of
the page box, so they survive zoom/viewport changes; rotated pages and spreads
restore the page only. `?uxstudy=1` records intent→truth events on
`window.__uxSession`.

Files: `core/components/viewer/ReadingPositionBridge.tsx`,
`readingPositionAnchor.ts` (+ unit tests), `core/services/readingPositionStore.ts`,
`uxSession.ts`, store `stirling-pdf-reading` in `indexedDBManager.ts`. Toolbar
now shows page N/M + a document percent.

Verification: `viewer-reading-position.spec.ts`, 3426 vitest, 49 viewer e2e,
typecheck/lint/comment-lint clean. Rails: pages-500 first page A/B (bridge
disabled vs enabled) measured no delta; the absolute numbers during that run
were environment-loaded (the machine had a busy Firefox at 23% CPU), which is
why the A/B, not the raw value, is the evidence.

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
  `@embedpdf/engines` 2.15.0 only (`npm run check:embedpdf-patch` verifies an
  applied patch, e.g. after `npm ci --ignore-scripts`). It asserts the exact
  version and every anchor, and no-ops once applied. Alternatives rejected:
  patch-package (the embedded worker source is one 600 KB line, so the committed
  diff would be ~1.2 MB), a Vite transform (dev pre-bundling and vitest load the
  dep outside the transform pipeline), vendoring the package (duplicates the
  bundle). The newest published engines version is 2.15.0; revisit on 3.0.
  The patch (a) transfers whole-buffer `{data,width,height}` worker responses
  ≥64 KB, (b) accepts `options.wasmModule` and instantiates it synchronously in
  the worker, and (c) retries the `wasmInit` post without the module when
  structured clone throws (older WebKit). A version bump must re-verify all
  anchors and update `EXPECTED_VERSION`.
- `editor/vite.config.ts` — the `compression()` plugin now includes `.wasm`, so
  the hashed pdfium asset gets `.br`/`.gz` siblings.
- The patch deliberately does **not** transfer the document bytes main→worker:
  the buffer comes from `documentBytesCache` and other main-thread scans hold
  it across awaits, so a transfer would detach it mid-read. The worker keeps
  receiving a structured clone (155 MB for the huge fixture).

## Backlog for the next pass (ranked, with evidence)

1. **Per-page form state** — `extractFormFields` still walks all pages on the
   main thread (form fixture WASM 66.9 MB, first page ~650-770 ms). Make form
   fields resolve per page while preserving save/validation semantics. Highest
   remaining memory win for form documents.
2. **Remaining local engine gaps (no upstream)** — transfers and precompiled
   module handoff are done (`bc519ae07`); what is left:
   - the document bytes are still structured-cloned main→worker (155 MB for the
     huge fixture). Doing this safely needs an ownership handoff that survives
     the main-thread scans reading through `documentBytesCache` (re-read on
     detach plus auditing every consumer that holds the buffer across awaits).
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
   `createSyncAccessHandle()` inside a worker. Relevant for multi-hundred-MB
   files because the engine worker holds its own copy; document the Safari
   gap for the Tauri desktop build.
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
  or an anchor moved; `npm run check:embedpdf-patch` verifies an applied patch.
- All three engines verified (smoke spec): Chromium, Firefox and WebKit 26
  render with zero worker wasm fetches. The `DataCloneError` fallback in the
  patch is only reachable on older WebKit, which has no test here.
- The main→worker document clone is intentionally still in place; do not add a
  transfer without addressing `documentBytesCache` ownership (see backlog 2).

## Verification before any change leaves the branch

```bash
task frontend:typecheck
task frontend:lint
task frontend:test        # 3420 tests at the time of writing
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
  --workers=1 --retries=0
```

Last verified after `3cd84a5e3`: typecheck/lint clean, 3420/3420 vitest,
46/46 viewer e2e, engine smoke green on Chromium/Firefox/WebKit.

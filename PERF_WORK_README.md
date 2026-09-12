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
total, CDP `Performance` CPU metrics, JS heap after forced GC, blob URL
counts, `Blob`/`Response.arrayBuffer` call count + bytes + stacks, main-thread
`WebAssembly.Memory` high-water series, DOM/canvas counts.

Known limitations:

- `wasmBuffersMB` covers the main thread only. The engine worker's WASM heap
  and the structured-clone copy into the worker are not measured yet.
- `wasmLive`/`wasmPeak` in the harness report 0 (wrapping
  `instance.exports.malloc` did not take effect); use high-water instead.
- `performance.measureUserAgentSpecificMemory()` is unavailable without
  cross-origin isolation; tests do not set COOP/COEP.
- Runs must use the production preview (`CI=1`) because dev StrictMode
  double-invokes effects and skews memory/timing.

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
  (a fresh object per render recreates the engine in a loop).
- `wasmPrecompiler.ts` — keeps the `{ module }` container and the
  `compileStreaming` → ArrayBuffer/MIME fallback.

## Backlog for the next pass (ranked, with evidence)

1. **Per-page form state** — `extractFormFields` still walks all pages on the
   main thread (form fixture WASM 66.9 MB, first page ~770 ms). Make form
   fields resolve per page while preserving save/validation semantics. Highest
   remaining memory win for form documents.
2. **Local engine patches (no upstream)** — measured/known gaps:
   - worker `postMessage` copies render results and the request payload
     (`WebWorkerEngine.proxy` already accepts `transferables`; the exported
     `createPdfiumEngine` never passes them). A locally owned worker around
     `@embedpdf/engines/worker` internals, or patch-package, are the options.
   - the worker fetches + `arrayBuffer()` + compiles the wasm (no streaming);
     pass a precompiled `WebAssembly.Module` (structured-cloneable) or use
     `compileStreaming` when the server sends `application/wasm`.
   - `PdfCache` page TTL (5 s) / max pages (10) are not configurable from
     `createPdfiumEngine`; expose or patch for scroll-heavy use.
3. **Cold/warm bootstrap + caching measurements** — measure first-open vs
   reload with the wasm HTTP cache, check cache headers on the served
   `pdfium.wasm`, and evaluate an IndexedDB copy of the compiled module or
   bytes. No numbers exist yet.
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
   metadata/index + per-page overlays). Rebuild history by replaying the
   focused commits; the branch currently carries merge commits from the three
   source branches.
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

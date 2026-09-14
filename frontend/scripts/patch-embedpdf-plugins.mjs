#!/usr/bin/env node
// Local patches for pinned @embedpdf plugins (see package.json).
//
// 1. interaction-manager: emits `onHandlerChange` synchronously from every
//    registerHandlers/registerAlways call, and every mounted
//    InteractionManager scope re-resolves `getHandlersForScope` on each emit.
//    Page mounting registers one handler per annotation tool, so a page jump
//    on a large document turns into hundreds of emits x scope recomputes
//    (22.8% of main-thread busy samples in the pass-5 jump profile).
//    Coalescing emits into one microtask keeps the notification contract
//    (listeners run before the next task/event) while doing one recompute
//    per batch.
// 2. search: `searchAllPages` dispatches `appendSearchResults` (+ a
//    `setActiveResultIndex`/`notifyActiveResultChange` pair) synchronously
//    from every engine progress callback, and the engine reports progress
//    per page — a 500-page query is ~500 dispatches x subscriber recompute
//    (680 ms main task, 400 ms long tasks on pages-500). Coalescing progress
//    batches into one microtask keeps incremental streaming (flushes run
//    before the next task) while dispatching once per batch. Same template
//    as pdf.js `updateMatchesCountOnProgress=false` (GeckoView): only the
//    final counts must be exact, intermediate updates may batch.
//
// Delete this script and its postinstall hook once the pinned plugins batch
// these notifications natively.
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const EXPECTED_VERSION = "2.15.0";
const MARKER = "STIRLING_LOCAL_EMBEDPDF_PLUGIN_PATCH";
const checkOnly = process.argv.includes("--check");
const nodeModulesDir = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../node_modules",
);

function loadPackage(name, relTarget = "dist/index.js") {
  const dir = path.join(nodeModulesDir, name);
  const packageJsonPath = path.join(dir, "package.json");
  const target = path.join(dir, relTarget);
  if (!existsSync(target) || !existsSync(packageJsonPath)) {
    console.error(
      `[patch-embedpdf-plugins] ${name} is not installed; skipping`,
    );
    process.exit(checkOnly ? 1 : 0);
  }
  const installedVersion = JSON.parse(
    readFileSync(packageJsonPath, "utf8"),
  ).version;
  if (installedVersion !== EXPECTED_VERSION) {
    console.error(
      `[patch-embedpdf-plugins] expected ${name}@${EXPECTED_VERSION}, found ${installedVersion}. ` +
        "Re-verify every patch anchor against the new version before updating EXPECTED_VERSION.",
    );
    process.exit(1);
  }
  return { name, target, source: readFileSync(target, "utf8") };
}

function fail(name, label) {
  console.error(
    `[patch-embedpdf-plugins] anchor not found for "${label}" in ${name}@${EXPECTED_VERSION}. ` +
      "The patch must be re-verified against this version.",
  );
  process.exit(1);
}

// --- interaction-manager: batch onHandlerChange emits -----------------------
const emitSnippet = "this.onHandlerChange$.emit({ ...this.state });";
const callSnippet = "this.__stirlingScheduleHandlerChange();";
const emitHelperSnippet = `__stirlingScheduleHandlerChange() {
    if (this.__stirlingHandlerChangeQueued) return;
    this.__stirlingHandlerChangeQueued = true;
    queueMicrotask(() => {
      this.__stirlingHandlerChangeQueued = false;
      this.onHandlerChange$.emit({ ...this.state });
    });
  } /* ${MARKER} */`;

function checkInteractionManager(pkg) {
  const rawEmits = pkg.source.split(emitSnippet).length - 1;
  const callSites = pkg.source.split(callSnippet).length - 1;
  return pkg.source.includes(MARKER) && rawEmits === 1 && callSites === 6;
}

function applyInteractionManager(pkg) {
  if (pkg.source.includes(MARKER)) return pkg.source;
  const registerAnchor = `  registerHandlers({
    documentId,
    modeId,
    handlers,
    pageIndex
  }) {`;
  if (!pkg.source.includes(registerAnchor)) fail(pkg.name, "registerHandlers");
  const occurrences = pkg.source.split(emitSnippet).length - 1;
  if (occurrences !== 6) {
    console.error(
      `[patch-embedpdf-plugins] expected 6 emit sites, found ${occurrences}; re-verify against the installed version.`,
    );
    process.exit(1);
  }
  // Replace the call sites first: the helper below still needs a real emit,
  // and a blanket replace after insertion would rewrite the helper into a
  // self-call.
  let source = pkg.source.split(emitSnippet).join(callSnippet);
  source = source.replace(
    registerAnchor,
    `  ${emitHelperSnippet}\n${registerAnchor}`,
  );
  return source;
}

// --- tiling: abort stale tile renders and never mint an orphan blob URL ------
// TileImg's cleanup aborted only when no URL had been produced yet, so a tile
// that resolved after unmount still created an object URL that nothing revoked
// (fast scroll = orphan blobs). Guard the success callback, always mark the
// task aborted, and keep TilingLayer SSR-safe (window may be undefined in
// unsupported/embedded deployments).
const tileBlockFind = `    const task = scope.renderTile({ pageIndex, tile, dpr });
    task.wait((blob) => {
      const objectUrl = URL.createObjectURL(blob);
      urlRef.current = objectUrl;
      setUrl(objectUrl);
    }, ignore);
    return () => {
      if (urlRef.current) {
        URL.revokeObjectURL(urlRef.current);
        urlRef.current = null;
      } else {
        task.abort({
          code: PdfErrorCode.Cancelled,
          message: "canceled render task"
        });
      }
    };`;
const tileBlockReplace = `    let stirlingCancelled = false; /* ${MARKER} */
    const task = scope.renderTile({ pageIndex, tile, dpr });
    task.wait((blob) => {
      if (stirlingCancelled) return;
      const objectUrl = URL.createObjectURL(blob);
      urlRef.current = objectUrl;
      setUrl(objectUrl);
    }, ignore);
    return () => {
      stirlingCancelled = true;
      task.abort({
        code: PdfErrorCode.Cancelled,
        message: "canceled render task"
      });
      if (urlRef.current) {
        URL.revokeObjectURL(urlRef.current);
        urlRef.current = null;
      }
    };`;
const tileDprFind = "dpr: window.devicePixelRatio,";
const tileDprReplace =
  'dpr: typeof window !== "undefined" ? window.devicePixelRatio : 1,';

function checkTiling(pkg) {
  return (
    pkg.source.includes(MARKER) &&
    pkg.source.includes("if (stirlingCancelled) return;") &&
    pkg.source.includes(tileDprReplace) &&
    !pkg.source.includes(tileDprFind)
  );
}

function applyTiling(pkg) {
  let source = pkg.source;
  if (!source.includes(tileBlockFind) && !source.includes(MARKER)) {
    fail(pkg.name, "TileImg render/cleanup");
  }
  if (source.includes(tileBlockFind)) {
    source = source.replace(tileBlockFind, () => tileBlockReplace);
  } else if (
    source.includes("stirlingCancelled") &&
    source.includes("} else {\n        task.abort({")
  ) {
    source = source.replace(
      /if \(urlRef\.current\) \{\s+URL\.revokeObjectURL\(urlRef\.current\);\s+urlRef\.current = null;\s+\} else \{\s+task\.abort\(\{\s+code: PdfErrorCode\.Cancelled,\s+message: "canceled render task"\s+\}\);\s+\}/,
      `task.abort({
        code: PdfErrorCode.Cancelled,
        message: "canceled render task"
      });
      if (urlRef.current) {
        URL.revokeObjectURL(urlRef.current);
        urlRef.current = null;
      }`,
    );
  }
  if (source.includes(tileDprFind)) {
    source = source.replace(tileDprFind, () => tileDprReplace);
  }
  return source;
}

// --- render: abort stale page renders and never mint an orphan blob URL ------
const renderBlockFind = `    const task = renderProvides.forDocument(documentId).renderPage({
      pageIndex,
      options: {
        scaleFactor: actualScale,
        dpr: actualDpr
      }
    });
    task.wait((blob) => {
      const url = URL.createObjectURL(blob);
      setImageUrl(url);
      urlRef.current = url;
    }, ignore);
    return () => {
      if (urlRef.current) {
        URL.revokeObjectURL(urlRef.current);
        urlRef.current = null;
      } else {
        task.abort({
          code: PdfErrorCode.Cancelled,
          message: "canceled render task"
        });
      }
    };`;

const renderBlockReplace = `    let stirlingCancelled = false; /* ${MARKER} */
    const task = renderProvides.forDocument(documentId).renderPage({
      pageIndex,
      options: {
        scaleFactor: actualScale,
        dpr: actualDpr
      }
    });
    task.wait((blob) => {
      if (stirlingCancelled) return;
      const url = URL.createObjectURL(blob);
      setImageUrl(url);
      urlRef.current = url;
    }, ignore);
    return () => {
      stirlingCancelled = true;
      task.abort({
        code: PdfErrorCode.Cancelled,
        message: "canceled render task"
      });
      if (urlRef.current) {
        URL.revokeObjectURL(urlRef.current);
        urlRef.current = null;
      }
    };`;

const renderDprFind = "return window.devicePixelRatio;";
const renderDprReplace =
  'return typeof window !== "undefined" ? window.devicePixelRatio : 1;';

function checkRender(pkg) {
  return (
    pkg.source.includes(MARKER) &&
    pkg.source.includes("if (stirlingCancelled) return;") &&
    pkg.source.includes(renderDprReplace) &&
    !pkg.source.includes(renderDprFind)
  );
}

function applyRender(pkg) {
  if (pkg.source.includes(MARKER)) return pkg.source;
  if (!pkg.source.includes(renderBlockFind))
    fail(pkg.name, "RenderLayer render/cleanup");
  if (!pkg.source.includes(renderDprFind)) fail(pkg.name, "devicePixelRatio");
  let source = pkg.source.replace(renderBlockFind, () => renderBlockReplace);
  source = source.replace(renderDprFind, () => renderDprReplace);
  return source;
}

// --- search: batch searchAllPages progress dispatches ------------------------
const searchProgressFind = `    task.onProgress((p) => {
      var _a2;
      if ((_a2 = p == null ? void 0 : p.results) == null ? void 0 : _a2.length) {
        if (this.currentTask.get(documentId) === task) {
          this.dispatch(appendSearchResults(documentId, p.results));
          if (this.state.documents[documentId].activeResultIndex === -1) {
            this.dispatch(setActiveResultIndex(documentId, 0));
            this.notifyActiveResultChange(documentId, 0);
          }
        }
      }
    });`;
const searchProgressReplace = `    task.onProgress((p) => {
      var _a2;
      if ((_a2 = p == null ? void 0 : p.results) == null ? void 0 : _a2.length) {
        if (this.currentTask.get(documentId) === task) {
          this.__stirlingQueueSearchProgress(documentId, task, p.results);
        }
      }
    });`;
const searchHelperAnchor = "  stopSearchSession(documentId) {";
const searchHelperSnippet = `  __stirlingQueueSearchProgress(documentId, task, results) {
    if (!this.__stirlingSearchQueue) this.__stirlingSearchQueue = /* @__PURE__ */ new Map();
    let entry = this.__stirlingSearchQueue.get(documentId);
    if (!entry || entry.task !== task) {
      entry = { task, batches: [], queued: false };
      this.__stirlingSearchQueue.set(documentId, entry);
    }
    entry.batches.push(results);
    if (entry.queued) return;
    entry.queued = true;
    queueMicrotask(() => {
      entry.queued = false;
      const pending = entry.batches.splice(0);
      if (this.__stirlingSearchQueue.get(documentId) === entry) this.__stirlingSearchQueue.delete(documentId);
      if (this.currentTask.get(documentId) !== task || pending.length === 0) return;
      const merged = pending.length === 1 ? pending[0] : pending.flat();
      this.dispatch(appendSearchResults(documentId, merged));
      const docState = this.state.documents[documentId];
      if (docState && docState.activeResultIndex === -1) {
        this.dispatch(setActiveResultIndex(documentId, 0));
        this.notifyActiveResultChange(documentId, 0);
      }
    });
  } /* ${MARKER} */
`;

function checkSearch(pkg) {
  return (
    pkg.source.includes(searchHelperSnippet) &&
    pkg.source.includes(
      "this.__stirlingQueueSearchProgress(documentId, task, p.results);",
    )
  );
}

function applySearch(pkg) {
  if (pkg.source.includes(MARKER)) return pkg.source;
  if (!pkg.source.includes(searchProgressFind)) fail(pkg.name, "onProgress");
  if (!pkg.source.includes(searchHelperAnchor))
    fail(pkg.name, "stopSearchSession");
  let source = pkg.source.replace(
    searchProgressFind,
    () => searchProgressReplace,
  );
  source = source.replace(
    searchHelperAnchor,
    () => `${searchHelperSnippet}${searchHelperAnchor}`,
  );
  return source;
}

// --- scroll: memoize pushScrollerLayout to avoid 120Hz redundant React re-renders ---
const scrollPushLayoutFindEsm = `  pushScrollerLayout(documentId) {
    const emitter = this.scrollerLayoutEmitters.get(documentId);
    if (!emitter) return;
    try {
      const layout = this.getScrollerLayout(documentId);
      emitter.emit(layout);
    } catch (error) {
    }
  }`;

const scrollPushLayoutReplaceEsm = `  pushScrollerLayout(documentId) {
    const emitter = this.scrollerLayoutEmitters.get(documentId);
    if (!emitter) return;
    try {
      const layout = this.getScrollerLayout(documentId);
      if (this.__stirlingLastLayouts) {
        const prev = this.__stirlingLastLayouts.get(documentId);
        if (
          prev &&
          prev.startSpacing === layout.startSpacing &&
          prev.endSpacing === layout.endSpacing &&
          prev.totalWidth === layout.totalWidth &&
          prev.totalHeight === layout.totalHeight &&
          prev.pageGap === layout.pageGap &&
          prev.strategy === layout.strategy &&
          prev.items.length === layout.items.length &&
          prev.items.every((it, i) => it.id === layout.items[i].id)
        ) {
          return;
        }
      } else {
        this.__stirlingLastLayouts = /* @__PURE__ */ new Map();
      }
      this.__stirlingLastLayouts.set(documentId, layout);
      emitter.emit(layout);
    } catch (error) {
    }
  } /* ${MARKER} */`;

function checkScrollEsm(pkg) {
  return (
    pkg.source.includes(MARKER) &&
    pkg.source.includes("this.__stirlingLastLayouts")
  );
}

function applyScrollEsm(pkg) {
  if (pkg.source.includes(MARKER)) return pkg.source;
  if (!pkg.source.includes(scrollPushLayoutFindEsm))
    fail(pkg.name, "pushScrollerLayout ESM");
  return pkg.source.replace(
    scrollPushLayoutFindEsm,
    () => scrollPushLayoutReplaceEsm,
  );
}

const scrollPushLayoutFindCjs = `pushScrollerLayout(t){const e=this.scrollerLayoutEmitters.get(t);if(e)try{const a=this.getScrollerLayout(t);e.emit(a)}catch(a){}}`;
const scrollPushLayoutReplaceCjs = `pushScrollerLayout(t){const e=this.scrollerLayoutEmitters.get(t);if(e)try{const a=this.getScrollerLayout(t);if(this.__stirlingLastLayouts){const e=this.__stirlingLastLayouts.get(t);if(e&&e.startSpacing===a.startSpacing&&e.endSpacing===a.endSpacing&&e.totalWidth===a.totalWidth&&e.totalHeight===a.totalHeight&&e.pageGap===a.pageGap&&e.strategy===a.strategy&&e.items.length===a.items.length&&e.items.every((e,t)=>e.id===a.items[t].id))return}else this.__stirlingLastLayouts=new Map;this.__stirlingLastLayouts.set(t,a);e.emit(a)}catch(a){}}/* ${MARKER} */`;

function checkScrollCjs(pkg) {
  return (
    pkg.source.includes(MARKER) &&
    pkg.source.includes("this.__stirlingLastLayouts")
  );
}

function applyScrollCjs(pkg) {
  if (pkg.source.includes(MARKER)) return pkg.source;
  if (!pkg.source.includes(scrollPushLayoutFindCjs))
    fail(pkg.name, "pushScrollerLayout CJS");
  return pkg.source.replace(
    scrollPushLayoutFindCjs,
    () => scrollPushLayoutReplaceCjs,
  );
}

// --- tiling: optimize onScroll throttle to avoid 50ms trailing lag -----------
const tilingThrottleFindEsm = `    this.scrollCapability.onScroll(
      (event) => this.calculateVisibleTiles(event.documentId, event.metrics),
      {
        mode: "throttle",
        wait: 50,
        throttleMode: "trailing"
      }
    );`;
const tilingThrottleReplaceEsm = `    this.scrollCapability.onScroll(
      (event) => this.calculateVisibleTiles(event.documentId, event.metrics),
      {
        mode: "throttle",
        wait: 16,
        throttleMode: "leading-trailing"
      }
    ); /* ${MARKER} */`;

function checkTilingThrottleEsm(pkg) {
  return (
    pkg.source.includes(MARKER) &&
    pkg.source.includes('throttleMode: "leading-trailing"')
  );
}

function applyTilingThrottleEsm(pkg) {
  if (pkg.source.includes(MARKER)) return pkg.source;
  if (!pkg.source.includes(tilingThrottleFindEsm))
    fail(pkg.name, "tiling onScroll throttle ESM");
  return pkg.source.replace(
    tilingThrottleFindEsm,
    () => tilingThrottleReplaceEsm,
  );
}

const tilingThrottleFindCjs = `this.scrollCapability.onScroll(e=>this.calculateVisibleTiles(e.documentId,e.metrics),{mode:"throttle",wait:50,throttleMode:"trailing"})`;
const tilingThrottleReplaceCjs = `this.scrollCapability.onScroll(e=>this.calculateVisibleTiles(e.documentId,e.metrics),{mode:"throttle",wait:16,throttleMode:"leading-trailing"})/* ${MARKER} */`;

function checkTilingThrottleCjs(pkg) {
  return (
    pkg.source.includes(MARKER) &&
    pkg.source.includes('throttleMode:"leading-trailing"')
  );
}

function applyTilingThrottleCjs(pkg) {
  if (pkg.source.includes(MARKER)) return pkg.source;
  if (!pkg.source.includes(tilingThrottleFindCjs))
    fail(pkg.name, "tiling onScroll throttle CJS");
  return pkg.source.replace(
    tilingThrottleFindCjs,
    () => tilingThrottleReplaceCjs,
  );
}

const jobs = [
  {
    name: "@embedpdf/plugin-interaction-manager",
    check: checkInteractionManager,
    apply: applyInteractionManager,
  },
  { name: "@embedpdf/plugin-search", check: checkSearch, apply: applySearch },
  {
    name: "@embedpdf/plugin-tiling",
    file: "dist/react/index.js",
    check: checkTiling,
    apply: applyTiling,
  },
  {
    name: "@embedpdf/plugin-tiling",
    file: "dist/index.js",
    check: checkTilingThrottleEsm,
    apply: applyTilingThrottleEsm,
  },
  {
    name: "@embedpdf/plugin-tiling",
    file: "dist/index.cjs",
    check: checkTilingThrottleCjs,
    apply: applyTilingThrottleCjs,
  },
  {
    name: "@embedpdf/plugin-scroll",
    file: "dist/index.js",
    check: checkScrollEsm,
    apply: applyScrollEsm,
  },
  {
    name: "@embedpdf/plugin-scroll",
    file: "dist/index.cjs",
    check: checkScrollCjs,
    apply: applyScrollCjs,
  },
  {
    name: "@embedpdf/plugin-render",
    file: "dist/react/index.js",
    check: checkRender,
    apply: applyRender,
  },
];

if (checkOnly) {
  let failed = false;
  for (const job of jobs) {
    const pkg = loadPackage(job.name, job.file);
    if (!job.check(pkg)) {
      console.error(
        `[patch-embedpdf-plugins] check failed for ${job.name}@${EXPECTED_VERSION}. ` +
          "Run `npm install` (or `npm run postinstall`) to apply the local plugin patch.",
      );
      failed = true;
    } else {
      console.log(
        `[patch-embedpdf-plugins] check passed for ${job.name}@${EXPECTED_VERSION}`,
      );
    }
  }
  process.exit(failed ? 1 : 0);
}

for (const job of jobs) {
  const pkg = loadPackage(job.name, job.file);
  const patched = job.apply(pkg);
  if (patched !== pkg.source) {
    writeFileSync(pkg.target, patched);
    console.log(
      `[patch-embedpdf-plugins] applied local patches to ${job.name}@${EXPECTED_VERSION}`,
    );
  } else {
    console.log(
      `[patch-embedpdf-plugins] already applied for ${job.name}@${EXPECTED_VERSION}`,
    );
  }
}

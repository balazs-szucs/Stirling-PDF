#!/usr/bin/env node
// Local patches for pinned @embedpdf plugins (see package.json). The
// interaction-manager emits `onHandlerChange` synchronously from every
// registerHandlers/registerAlways call, and every mounted InteractionManager
// scope re-resolves `getHandlersForScope` on each emit. Page mounting registers
// one handler per annotation tool, so a page jump on a large document turns
// into hundreds of emits x scope recomputes (22.8% of main-thread busy samples
// in the pass-5 jump profile). Coalescing emits into one microtask keeps the
// notification contract (listeners run before the next task/event) while doing
// one recompute per batch.
//
// Delete this script and its postinstall hook once the pinned plugin batches
// handler-change notifications natively.
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const EXPECTED_VERSION = "2.15.0";
const MARKER = "STIRLING_LOCAL_EMBEDPDF_PLUGIN_PATCH";
const checkOnly = process.argv.includes("--check");
const pluginsDir = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../node_modules/@embedpdf/plugin-interaction-manager",
);
const packageJsonPath = path.join(pluginsDir, "package.json");
const target = path.join(pluginsDir, "dist/index.js");

if (!existsSync(target) || !existsSync(packageJsonPath)) {
  console.error(
    "[patch-embedpdf-plugins] @embedpdf/plugin-interaction-manager is not installed; skipping",
  );
  process.exit(checkOnly ? 1 : 0);
}

const installedVersion = JSON.parse(
  readFileSync(packageJsonPath, "utf8"),
).version;
if (installedVersion !== EXPECTED_VERSION) {
  console.error(
    `[patch-embedpdf-plugins] expected @embedpdf/plugin-interaction-manager@${EXPECTED_VERSION}, found ${installedVersion}. ` +
      "Re-verify every patch anchor against the new version before updating EXPECTED_VERSION.",
  );
  process.exit(1);
}

const emitSnippet = "this.onHandlerChange$.emit({ ...this.state });";
const callSnippet = "this.__stirlingScheduleHandlerChange();";
const helperSnippet = `__stirlingScheduleHandlerChange() {
    if (this.__stirlingHandlerChangeQueued) return;
    this.__stirlingHandlerChangeQueued = true;
    queueMicrotask(() => {
      this.__stirlingHandlerChangeQueued = false;
      this.onHandlerChange$.emit({ ...this.state });
    });
  } /* ${MARKER} */`;

let source = readFileSync(target, "utf8");
const callSites = source.split(callSnippet).length - 1;

if (checkOnly) {
  const rawEmits = source.split(emitSnippet).length - 1;
  if (!source.includes(MARKER) || rawEmits !== 1 || callSites !== 6) {
    console.error(
      "[patch-embedpdf-plugins] check failed: expected the batched helper and 6 call sites " +
        `(helper=${source.includes(MARKER)}, raw emits=${rawEmits}, batched calls=${callSites}). ` +
        "Run `npm install` (or `npm run postinstall`) to apply the local plugin patch.",
    );
    process.exit(1);
  }
  console.log(
    `[patch-embedpdf-plugins] check passed for @embedpdf/plugin-interaction-manager@${installedVersion}`,
  );
  process.exit(0);
}

if (source.includes(MARKER)) {
  console.log(
    `[patch-embedpdf-plugins] already applied (${callSites} call sites) for @embedpdf/plugin-interaction-manager@${installedVersion}`,
  );
  process.exit(0);
}

const registerAnchor = `  registerHandlers({
    documentId,
    modeId,
    handlers,
    pageIndex
  }) {`;
if (!source.includes(registerAnchor)) {
  console.error(
    "[patch-embedpdf-plugins] anchor not found for registerHandlers; re-verify against the installed version.",
  );
  process.exit(1);
}
const occurrences = source.split(emitSnippet).length - 1;
if (occurrences !== 6) {
  console.error(
    `[patch-embedpdf-plugins] expected 6 emit sites, found ${occurrences}; re-verify against the installed version.`,
  );
  process.exit(1);
}
// Replace the call sites first: the helper below still needs a real emit, and a
// blanket replace after insertion would rewrite the helper into a self-call.
source = source.split(emitSnippet).join(callSnippet);
source = source.replace(
  registerAnchor,
  `  ${helperSnippet}\n${registerAnchor}`,
);
writeFileSync(target, source);
console.log(
  `[patch-embedpdf-plugins] applied local patches to @embedpdf/plugin-interaction-manager@${installedVersion}`,
);

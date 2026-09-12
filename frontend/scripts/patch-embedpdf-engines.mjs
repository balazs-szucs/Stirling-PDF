#!/usr/bin/env node
// Local patches for the pinned @embedpdf/engines (see package.json). The upstream
// worker engine copies every rendered bitmap across the worker boundary and makes
// the worker fetch + compile pdfium.wasm even when the main thread already holds a
// compiled WebAssembly.Module. Upstream does not take patches while 3.0 is being
// prepared, so the two gaps are closed here, against the exact pinned version.
//
// Delete this script and the postinstall hook once the pinned engine provides
// transferables and precompiled-module handoff natively.
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const EXPECTED_VERSION = "2.15.0";
const MARKER = "STIRLING_LOCAL_EMBEDPDF_PATCH";
const enginesDir = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../node_modules/@embedpdf/engines",
);
const packageJsonPath = path.join(enginesDir, "package.json");
const target = path.join(enginesDir, "dist/lib/pdfium/web/worker-engine.js");

if (!existsSync(target) || !existsSync(packageJsonPath)) {
  console.error(
    "[patch-embedpdf-engines] @embedpdf/engines is not installed; skipping",
  );
  process.exit(0);
}

const installedVersion = JSON.parse(readFileSync(packageJsonPath, "utf8")).version;
if (installedVersion !== EXPECTED_VERSION) {
  console.error(
    `[patch-embedpdf-engines] expected @embedpdf/engines@${EXPECTED_VERSION}, found ${installedVersion}. ` +
      "Re-verify every patch anchor against the new version before updating EXPECTED_VERSION.",
  );
  process.exit(1);
}

let source = readFileSync(target, "utf8");
if (source.includes(MARKER)) {
  process.exit(0);
}

// Text inside the embedded worker bundle is stored with `\n` escape sequences, so
// patterns that span lines use a literal backslash-n. Patterns outside it use real
// newlines. Every anchor is asserted: a silent miss would ship an unpatched engine.
const replacements = [
  {
    label: "worker: accept precompiled module in wasmInit",
    find: 'type === "wasmInit" && wasmUrl && !runner',
    replace: `type === "wasmInit" && (wasmUrl || event.data.wasmModule) && !runner /* ${MARKER} */`,
  },
  {
    label: "worker: use module bytes or fetch",
    find:
      "const response = await fetch(wasmUrl);\\n      const wasmBinary = await response.arrayBuffer();",
    replace:
      "let wasmBinary = event.data.wasmModule;\\n      if (!wasmBinary) {\\n        const response = await fetch(wasmUrl);\\n        wasmBinary = await response.arrayBuffer();\\n      }",
  },
  {
    label: "worker: instantiate precompiled module without recompiling",
    find:
      "async prepare() {\\n    const wasmBinary = this.wasmBinary;\\n    const wasmModule = await init({ wasmBinary });",
    replace:
      'async prepare() {\\n    const wasmBinary = this.wasmBinary;\\n    const isPrecompiled = typeof WebAssembly === "object" && WebAssembly.Module && wasmBinary instanceof WebAssembly.Module;\\n    const wasmModule = isPrecompiled ? await init({\\n      instantiateWasm: (imports, successCallback) => {\\n        const instance = new WebAssembly.Instance(wasmBinary, imports);\\n        successCallback(instance, wasmBinary);\\n        return instance.exports;\\n      }\\n    }) : await init({ wasmBinary });',
  },
  {
    label: "worker: transfer whole-buffer render results",
    find:
      'respond(response) {\\n    this.logger.debug(LOG_SOURCE, LOG_CATEGORY, "Sending response:", response.type);\\n    self.postMessage(response);\\n  }',
    replace:
      'respond(response) {\\n    this.logger.debug(LOG_SOURCE, LOG_CATEGORY, "Sending response:", response.type);\\n    const imagePayload = response && response.data;\\n    const imageData = imagePayload && imagePayload.data;\\n    if (imageData && typeof imagePayload.width === "number" && typeof imagePayload.height === "number" && imageData.byteLength >= 65536) {\\n      const imageBuffer = imageData.buffer;\\n      if (imageBuffer instanceof ArrayBuffer && imageData.byteOffset === 0 && imageData.byteLength === imageBuffer.byteLength) {\\n        self.postMessage(response, [imageBuffer]);\\n        return;\\n      }\\n    }\\n    self.postMessage(response);\\n  }',
  },
  {
    label: "engine: read wasmModule option",
    find: "const { logger, encoderPoolSize, fontFallback } = config;",
    replace:
      "const { logger, encoderPoolSize, fontFallback, wasmModule: precompiledWasmModule } = config;",
  },
  {
    label: "engine: pass wasmModule to executor",
    find: "const remoteExecutor = new RemoteExecutor(worker, { wasmUrl, logger, fontFallback });",
    replace:
      "const remoteExecutor = new RemoteExecutor(worker, { wasmUrl, logger, fontFallback, wasmModule: precompiledWasmModule });",
  },
  {
    label: "engine: post wasmModule with clone fallback",
    find: `    this.worker.postMessage({
      id: _RemoteExecutor.READY_TASK_ID,
      type: "wasmInit",
      wasmUrl: options.wasmUrl,
      logger: options.logger ? serializeLogger(options.logger) : void 0,
      fontFallback: options.fontFallback
    });`,
    replace: `    const wasmInitMessage = {
      id: _RemoteExecutor.READY_TASK_ID,
      type: "wasmInit",
      wasmUrl: options.wasmUrl,
      logger: options.logger ? serializeLogger(options.logger) : void 0,
      fontFallback: options.fontFallback
    };
    // WebAssembly.Module is structured-cloneable in Chromium/Firefox but not
    // WebKit; when cloning fails the worker fetches the URL itself.
    if (options.wasmModule) wasmInitMessage.wasmModule = options.wasmModule;
    try {
      this.worker.postMessage(wasmInitMessage);
    } catch (cloneError) {
      if (!wasmInitMessage.wasmModule) throw cloneError;
      delete wasmInitMessage.wasmModule;
      this.worker.postMessage(wasmInitMessage);
    }`,
  },
];

for (const { label, find, replace } of replacements) {
  if (!source.includes(find)) {
    console.error(
      `[patch-embedpdf-engines] anchor not found for "${label}" in @embedpdf/engines@${installedVersion}. ` +
        "The patch must be re-verified against this version.",
    );
    process.exit(1);
  }
  source = source.replace(find, () => replace);
}

writeFileSync(target, source);
console.log(`[patch-embedpdf-engines] applied local patches to @embedpdf/engines@${installedVersion}`);

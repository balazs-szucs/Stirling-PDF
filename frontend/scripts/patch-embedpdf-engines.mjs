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
const checkOnly = process.argv.includes("--check");
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
  process.exit(checkOnly ? 1 : 0);
}

const installedVersion = JSON.parse(
  readFileSync(packageJsonPath, "utf8"),
).version;
if (installedVersion !== EXPECTED_VERSION) {
  console.error(
    `[patch-embedpdf-engines] expected @embedpdf/engines@${EXPECTED_VERSION}, found ${installedVersion}. ` +
      "Re-verify every patch anchor against the new version before updating EXPECTED_VERSION.",
  );
  process.exit(1);
}

let source = readFileSync(target, "utf8");
const patchedSnippets = [
  "(wasmUrl || event.data.wasmModule)",
  "let wasmBinary = event.data.wasmModule;",
  "new WebAssembly.Instance(wasmBinary, imports)",
  "imageData.byteOffset === 0",
  "wasmModule: precompiledWasmModule",
  "delete wasmInitMessage.wasmModule",
  "__stirlingCreatedUrls",
  "URL.revokeObjectURL(__stirlingUrl)",
  "cache: cacheConfig",
  "if (options.cache) wasmInitMessage.cache = options.cache;",
  "this.cacheConfig = cacheConfig",
  "new PdfCache(this.pdfiumModule, this.memoryManager, cacheConfig)",
];

if (checkOnly) {
  const missing = patchedSnippets.filter(
    (snippet) => !source.includes(snippet),
  );
  if (missing.length > 0) {
    console.error(
      `[patch-embedpdf-engines] check failed: ${missing.length} patched snippet(s) missing. ` +
        "Run `npm install` (or `npm run postinstall`) to apply the local engine patch.",
    );
    process.exit(1);
  }
  console.log(
    `[patch-embedpdf-engines] check passed for @embedpdf/engines@${installedVersion}`,
  );
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
    find: "const response = await fetch(wasmUrl);\\n      const wasmBinary = await response.arrayBuffer();",
    replace:
      "let wasmBinary = event.data.wasmModule;\\n      if (!wasmBinary) {\\n        const response = await fetch(wasmUrl);\\n        wasmBinary = await response.arrayBuffer();\\n      }",
  },
  {
    label: "worker: instantiate precompiled module without recompiling",
    find: "async prepare() {\\n    const wasmBinary = this.wasmBinary;\\n    const wasmModule = await init({ wasmBinary });",
    replace:
      'async prepare() {\\n    const wasmBinary = this.wasmBinary;\\n    const isPrecompiled = typeof WebAssembly === "object" && WebAssembly.Module && wasmBinary instanceof WebAssembly.Module;\\n    const wasmModule = isPrecompiled ? await init({\\n      instantiateWasm: (imports, successCallback) => {\\n        const instance = new WebAssembly.Instance(wasmBinary, imports);\\n        successCallback(instance, wasmBinary);\\n        return instance.exports;\\n      }\\n    }) : await init({ wasmBinary });',
  },
  {
    label: "worker: transfer whole-buffer render results",
    find: 'respond(response) {\\n    this.logger.debug(LOG_SOURCE, LOG_CATEGORY, "Sending response:", response.type);\\n    self.postMessage(response);\\n  }',
    replace:
      'respond(response) {\\n    this.logger.debug(LOG_SOURCE, LOG_CATEGORY, "Sending response:", response.type);\\n    const imagePayload = response && response.data;\\n    const imageData = imagePayload && imagePayload.data;\\n    if (imageData && typeof imagePayload.width === "number" && typeof imagePayload.height === "number" && imageData.byteLength >= 65536) {\\n      const imageBuffer = imageData.buffer;\\n      if (imageBuffer instanceof ArrayBuffer && imageData.byteOffset === 0 && imageData.byteLength === imageBuffer.byteLength) {\\n        self.postMessage(response, [imageBuffer]);\\n        return;\\n      }\\n    }\\n    self.postMessage(response);\\n  }',
  },
  {
    label: "engine: read wasmModule and cache options",
    find: /const \{ logger, encoderPoolSize, fontFallback(, wasmModule: precompiledWasmModule)? \} = config;/,
    replace:
      "const { logger, encoderPoolSize, fontFallback, wasmModule: precompiledWasmModule, cache: cacheConfig } = config;",
  },
  {
    label: "engine: pass wasmModule and cache to executor",
    find: /const remoteExecutor = new RemoteExecutor\(worker, \{ wasmUrl, logger, fontFallback(, wasmModule: precompiledWasmModule)? \}\);/,
    replace:
      "const remoteExecutor = new RemoteExecutor(worker, { wasmUrl, logger, fontFallback, wasmModule: precompiledWasmModule, cache: cacheConfig });",
  },
  {
    label: "engine: post wasmModule with clone fallback and cache",
    find: /( {4}this\.worker\.postMessage\({\n {6}id: _RemoteExecutor\.READY_TASK_ID,\n {6}type: "wasmInit",\n {6}wasmUrl: options\.wasmUrl,\n {6}logger: options\.logger \? serializeLogger\(options\.logger\) : void 0,\n {6}fontFallback: options\.fontFallback\n {4}}\);| {4}const wasmInitMessage = {\n {6}id: _RemoteExecutor\.READY_TASK_ID,\n {6}type: "wasmInit",\n {6}wasmUrl: options\.wasmUrl,\n {6}logger: options\.logger \? serializeLogger\(options\.logger\) : void 0,\n {6}fontFallback: options\.fontFallback\n {4}};\n( {4}if \(options\.cache\) wasmInitMessage\.cache = options\.cache;\n)? {4}\/\/ WebAssembly\.Module is structured-cloneable in Chromium\/Firefox but not\n {4}\/\/ WebKit; when cloning fails the worker fetches the URL itself\.\n {4}if \(options\.wasmModule\) wasmInitMessage\.wasmModule = options\.wasmModule;\n {4}try {\n {6}this\.worker\.postMessage\(wasmInitMessage\);\n {4}} catch \(cloneError\) {\n {6}if \(!wasmInitMessage\.wasmModule\) throw cloneError;\n {6}delete wasmInitMessage\.wasmModule;\n {6}this\.worker\.postMessage\(wasmInitMessage\);\n {4}})/,
    replace: `    const wasmInitMessage = {
      id: _RemoteExecutor.READY_TASK_ID,
      type: "wasmInit",
      wasmUrl: options.wasmUrl,
      logger: options.logger ? serializeLogger(options.logger) : void 0,
      fontFallback: options.fontFallback
    };
    if (options.cache) wasmInitMessage.cache = options.cache;
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
  {
    label: "engine: capture worker blob URLs",
    find:
      / {2}const \{ logger, encoderPoolSize, fontFallback, wasmModule: precompiledWasmModule(, cache: cacheConfig)? \} = config;\n( {2}const __stirlingCreatedUrls = \[\];\n {2}const __stirlingCreateObjectURL = URL\.createObjectURL\.bind\(URL\);\n {2}URL\.createObjectURL = \(obj\) => \{\n {4}const url = __stirlingCreateObjectURL\(obj\);\n {4}__stirlingCreatedUrls\.push\(url\);\n {4}return url;\n {2}\};\n)? {2}const worker = new Worker\(/,
    replace: `  const { logger, encoderPoolSize, fontFallback, wasmModule: precompiledWasmModule, cache: cacheConfig } = config;
  const __stirlingCreatedUrls = [];
  const __stirlingCreateObjectURL = URL.createObjectURL.bind(URL);
  URL.createObjectURL = (obj) => {
    const url = __stirlingCreateObjectURL(obj);
    __stirlingCreatedUrls.push(url);
    return url;
  };
  const worker = new Worker(`,
  },
  {
    label: "worker: receive cache configuration in wasmInit",
    find: 'const { type, wasmUrl, logger: serializedLogger, fontFallback } = event.data;\\n  if (type === "wasmInit"',
    replace:
      'const { type, wasmUrl, logger: serializedLogger, fontFallback, cache: cacheConfig } = event.data;\\n  if (type === "wasmInit"',
  },
  {
    label: "worker: pass cache to PdfiumEngineRunner",
    find: "runner = new PdfiumEngineRunner(wasmBinary, logger, effectiveFontFallback);",
    replace:
      "runner = new PdfiumEngineRunner(wasmBinary, logger, effectiveFontFallback, cacheConfig);",
  },
  {
    label: "worker: accept cache in PdfiumEngineRunner",
    find: "constructor(wasmBinary, logger, fontFallback) {\\n    super(logger);\\n    this.wasmBinary = wasmBinary;\\n    this.fontFallback = fontFallback;\\n  }",
    replace:
      "constructor(wasmBinary, logger, fontFallback, cacheConfig) {\\n    super(logger);\\n    this.wasmBinary = wasmBinary;\\n    this.fontFallback = fontFallback;\\n    this.cacheConfig = cacheConfig;\\n  }",
  },
  {
    label: "worker: forward cache to PdfiumNative in prepare",
    find: "this.native = new PdfiumNative(wasmModule, {\\n      logger: this.logger,\\n      fontFallback: this.fontFallback\\n    });",
    replace:
      "this.native = new PdfiumNative(wasmModule, {\\n      logger: this.logger,\\n      fontFallback: this.fontFallback,\\n      cache: this.cacheConfig\\n    });",
  },
  {
    label: "worker: forward cache configuration to PdfCache",
    find: "const { logger = new NoopLogger(), fontFallback } = options;\\n    this.logger = logger;\\n    this.memoryManager = new MemoryManager(this.pdfiumModule, this.logger);\\n    this.cache = new PdfCache(this.pdfiumModule, this.memoryManager);",
    replace:
      "const { logger = new NoopLogger(), fontFallback, cache: cacheConfig } = options;\\n    this.logger = logger;\\n    this.memoryManager = new MemoryManager(this.pdfiumModule, this.logger);\\n    this.cache = new PdfCache(this.pdfiumModule, this.memoryManager, cacheConfig);",
  },
  {
    label: "engine: revoke worker blob URLs after construction",
    find: `  const __stirlingEngine = new PdfEngine(remoteExecutor, {
    imageConverter: createHybridImageConverter(encoderPool),
    logger
  });
  URL.createObjectURL = __stirlingCreateObjectURL;
  // The worker scripts are Blob URLs and this build never revokes them, so
  // every engine (re)creation leaked one object URL per worker. All workers
  // have been constructed synchronously by now; release the URLs on a
  // macrotask so the platform has resolved them.
  setTimeout(() => {
    for (const __stirlingUrl of __stirlingCreatedUrls) {
      try {
        URL.revokeObjectURL(__stirlingUrl);
      } catch {
        /* already revoked */
      }
    }
  }, 0);
  return __stirlingEngine;
}
export {
  createPdfiumEngine
};`,
    replace: `  const __stirlingEngine = new PdfEngine(remoteExecutor, {
    imageConverter: createHybridImageConverter(encoderPool),
    logger
  });
  URL.createObjectURL = __stirlingCreateObjectURL;
  // The worker scripts are Blob URLs and this build never revokes them, so
  // every engine (re)creation leaked one object URL per worker. All workers
  // have been constructed synchronously by now; release the URLs on a
  // macrotask so the platform has resolved them.
  setTimeout(() => {
    for (const __stirlingUrl of __stirlingCreatedUrls) {
      try {
        URL.revokeObjectURL(__stirlingUrl);
      } catch {
        /* already revoked */
      }
    }
  }, 0);
  return __stirlingEngine;
}
export {
  createPdfiumEngine
};`,
  },
];

for (const { label, find, replace } of replacements) {
  if (source.includes(replace)) {
    continue;
  }
  const match = typeof find === "string" ? source.includes(find) : find.test(source);
  if (!match) {
    console.error(
      `[patch-embedpdf-engines] anchor not found for "${label}" in @embedpdf/engines@${installedVersion}. ` +
        "The patch must be re-verified against this version.",
    );
    process.exit(1);
  }
  source = source.replace(find, () => replace);
}

writeFileSync(target, source);

console.log(
  `[patch-embedpdf-engines] applied local patches to @embedpdf/engines@${installedVersion}`,
);

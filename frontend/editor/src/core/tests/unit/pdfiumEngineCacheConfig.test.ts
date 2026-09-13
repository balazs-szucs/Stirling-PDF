import { describe, it, expect } from "vitest";

describe("PdfCache and engine cache configuration contract", () => {
  it("pins DEFAULT_CONFIG in @embedpdf/engines cache implementation", async () => {
    // Read the engines direct-engine or cache bundle to pin default cache constants
    const fs = await import("fs");
    const path = await import("path");
    const engineFile = path.resolve(
      __dirname,
      "../../../../../node_modules/@embedpdf/engines/dist/lib/pdfium/web/worker-engine.js",
    );
    const content = fs.readFileSync(engineFile, "utf8");

    // Pin that upstream default is 5000ms TTL and 10 max pages
    expect(content).toContain("pageTtl: 5e3");
    expect(content).toContain("maxPagesPerDocument: 10");
  });

  it("pins that unpatched createPdfiumEngine omits cache configuration from wasmInit", async () => {
    const originalWorker = globalThis.Worker;
    const originalUrl = globalThis.URL;
    const originalBlob = globalThis.Blob;

    const postedMessages: unknown[] = [];

    class MockWorker {
      constructor(public url: string) {}
      addEventListener() {}
      removeEventListener() {}
      postMessage(msg: unknown) {
        postedMessages.push(msg);
      }
      terminate() {}
    }

    // @ts-expect-error mock worker
    globalThis.Worker = MockWorker;
    // @ts-expect-error mock URL
    globalThis.URL = {
      createObjectURL: () => "blob:mock",
      revokeObjectURL: () => {},
    };
    // @ts-expect-error mock Blob
    globalThis.Blob = class {
      constructor() {}
    };

    try {
      const { createPdfiumEngine } = await import(
        "@embedpdf/engines/pdfium-worker-engine"
      );

      // Call createPdfiumEngine with cache option
      // @ts-expect-error testing extra cache option
      createPdfiumEngine("https://example.com/pdfium.wasm", {
        cache: { pageTtl: 10000, maxPagesPerDocument: 25 },
      });

      expect(postedMessages.length).toBeGreaterThan(0);
      const initMessage = postedMessages.find(
        (msg): msg is { type: string; cache?: unknown } =>
          typeof msg === "object" &&
          msg !== null &&
          "type" in msg &&
          (msg as { type: string }).type === "wasmInit",
      );

      expect(initMessage).toBeDefined();
      // In unpatched state, cache is omitted from wasmInit
      expect(initMessage?.cache).toBeUndefined();
    } finally {
      globalThis.Worker = originalWorker;
      globalThis.URL = originalUrl;
      globalThis.Blob = originalBlob;
    }
  });
});

import { describe, it, expect } from "vitest";

describe("PdfCache and engine cache configuration contract", () => {
  it("pins DEFAULT_CONFIG in @embedpdf/engines cache implementation", async () => {
    // Read the engines worker-engine bundle to pin default cache constants
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

  it("forwards cache configuration to worker wasmInit when provided, and preserves default when omitted", async () => {
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

      // 1. With cache option provided
      // @ts-expect-error testing exposed cache option
      createPdfiumEngine("https://example.com/pdfium.wasm", {
        cache: { pageTtl: 10000, maxPagesPerDocument: 25 },
      });

      expect(postedMessages.length).toBeGreaterThan(0);
      const msgWithCache = postedMessages[0] as {
        type: string;
        cache?: { pageTtl: number; maxPagesPerDocument: number };
      };
      expect(msgWithCache.type).toBe("wasmInit");
      expect(msgWithCache.cache).toEqual({
        pageTtl: 10000,
        maxPagesPerDocument: 25,
      });

      // 2. Without cache option provided
      postedMessages.length = 0;
      createPdfiumEngine("https://example.com/pdfium.wasm", {});
      const msgWithoutCache = postedMessages[0] as {
        type: string;
        cache?: unknown;
      };
      expect(msgWithoutCache.type).toBe("wasmInit");
      expect(msgWithoutCache.cache).toBeUndefined();
    } finally {
      globalThis.Worker = originalWorker;
      globalThis.URL = originalUrl;
      globalThis.Blob = originalBlob;
    }
  });

  it("verifies worker script bundle wires cache into PdfCache instantiation", async () => {
    const fs = await import("fs");
    const path = await import("path");
    const engineFile = path.resolve(
      __dirname,
      "../../../../../node_modules/@embedpdf/engines/dist/lib/pdfium/web/worker-engine.js",
    );
    const content = fs.readFileSync(engineFile, "utf8");

    // Pin that the worker bundle extracts cache from event.data, passes to runner, and supplies to PdfCache
    expect(content).toContain("cache: cacheConfig");
    expect(content).toContain("this.cacheConfig = cacheConfig");
    expect(content).toContain(
      "new PdfCache(this.pdfiumModule, this.memoryManager, cacheConfig)",
    );
  });
});

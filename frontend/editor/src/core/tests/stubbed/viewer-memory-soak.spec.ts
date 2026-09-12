/** GC-normalized open/render/delete soak. Catches retained-memory regressions
 *  that a single open cannot: leaked object URLs, growing JS heap, DOM nodes or
 *  listeners that survive document removal.
 *
 *  Chromium-only because the retained-heap reading needs CDP GC; the other
 *  engines skip. Mirrors the loop used during the viewer perf pass; keep the
 *  iteration count small enough for PR CI. */

import path from "path";
import { test, expect } from "@app/tests/helpers/stub-test-base";

const SAMPLE_PDF = path.join(
  import.meta.dirname,
  "../test-fixtures/annotation-text-sample.pdf",
);
const ITERATIONS = Number(process.env.SOAK_ITERATIONS ?? 12);

type Sample = {
  iter: number;
  phase: "open" | "removed";
  jsHeapMB: number;
  nodes: number;
  listeners: number;
  blobs: number;
  mainWasmPages: number;
  workerWasmPages: number;
};

/** Same probe inside the engine workers, installed before the module loads. */
const installWorkerMemoryProbe = () => {
  const w = self as unknown as { __soakWorkerMemories?: WebAssembly.Memory[] };
  if (w.__soakWorkerMemories) return;
  w.__soakWorkerMemories = [];
  const record = (instance: unknown) => {
    try {
      const memory = (instance as WebAssembly.Instance).exports?.memory as
        | WebAssembly.Memory
        | undefined;
      if (memory && !w.__soakWorkerMemories!.includes(memory)) {
        w.__soakWorkerMemories!.push(memory);
      }
    } catch {
      /* ignore */
    }
  };
  const instantiate = WebAssembly.instantiate.bind(WebAssembly);
  WebAssembly.instantiate = ((...args: Parameters<typeof WebAssembly.instantiate>) => {
    const result = instantiate(...(args as [BufferSource, WebAssembly.Imports?]));
    return Promise.resolve(result).then((res) => {
      record((res as WebAssembly.WebAssemblyInstantiatedSource).instance ?? res);
      return res;
    });
  }) as typeof WebAssembly.instantiate;
  const OrigInstance = WebAssembly.Instance as unknown as new (
    m: WebAssembly.Module,
    i?: WebAssembly.Imports,
  ) => WebAssembly.Instance;
  const ProbeInstance = function (
    this: unknown,
    m: WebAssembly.Module,
    i?: WebAssembly.Imports,
  ) {
    const instance = new OrigInstance(m, i);
    record(instance);
    return instance;
  } as unknown as typeof WebAssembly.Instance;
  ProbeInstance.prototype = OrigInstance.prototype;
  WebAssembly.Instance = ProbeInstance;
};

test.describe("viewer memory soak", { tag: "@memory-soak" }, () => {
  test.skip(
    ({ browserName }) => browserName !== "chromium",
    "retained-heap readings need CDP",
  );

  test("open/render/delete cycles do not grow retained memory", async ({
    page,
  }) => {
    test.setTimeout(600_000);

    await page.addInitScript(() => {
      const w = window as unknown as {
        __soak: {
          created: number;
          revoked: number;
          memories: WebAssembly.Memory[];
        };
      };
      w.__soak = { created: 0, revoked: 0, memories: [] };
      const create = URL.createObjectURL.bind(URL);
      URL.createObjectURL = (obj: Blob | MediaSource) => {
        w.__soak.created++;
        return create(obj);
      };
      const revoke = URL.revokeObjectURL.bind(URL);
      URL.revokeObjectURL = (url: string) => {
        w.__soak.revoked++;
        return revoke(url);
      };
      // Page-count telemetry: bytes mix in unrelated allocations, pages are the
      // unit wasm memory is actually committed in (64 KiB each).
      const record = (instance: unknown) => {
        try {
          const memory = (instance as WebAssembly.Instance).exports?.memory as
            | WebAssembly.Memory
            | undefined;
          if (memory && !w.__soak.memories.includes(memory)) w.__soak.memories.push(memory);
        } catch {
          /* ignore */
        }
      };
      const instantiate = WebAssembly.instantiate.bind(WebAssembly);
      WebAssembly.instantiate = ((...args: Parameters<typeof WebAssembly.instantiate>) => {
        const result = instantiate(...(args as [BufferSource, WebAssembly.Imports?]));
        return Promise.resolve(result).then((res) => {
          record((res as WebAssembly.WebAssemblyInstantiatedSource).instance ?? res);
          return res;
        });
      }) as typeof WebAssembly.instantiate;
      const OrigInstance = WebAssembly.Instance as unknown as new (
        m: WebAssembly.Module,
        i?: WebAssembly.Imports,
      ) => WebAssembly.Instance;
      const ProbeInstance = function (
        this: unknown,
        m: WebAssembly.Module,
        i?: WebAssembly.Imports,
      ) {
        const instance = new OrigInstance(m, i);
        record(instance);
        return instance;
      } as unknown as typeof WebAssembly.Instance;
      ProbeInstance.prototype = OrigInstance.prototype;
      WebAssembly.Instance = ProbeInstance;
    });
    page.on("worker", (worker) => {
      void worker.evaluate(installWorkerMemoryProbe).catch(() => undefined);
    });

    const cdp = await page.context().newCDPSession(page);
    await cdp.send("HeapProfiler.enable");
    await cdp.send("Runtime.enable");

    const read = async (iter: number, phase: Sample["phase"]): Promise<Sample> => {
      await cdp.send("HeapProfiler.collectGarbage");
      const { usedSize } = await cdp.send("Runtime.getHeapUsage");
      const { nodes, jsEventListeners } = await cdp.send("Memory.getDOMCounters");
      const main = await page.evaluate(() => {
        const soak = (
          window as unknown as {
            __soak: { created: number; revoked: number; memories: WebAssembly.Memory[] };
          }
        ).__soak;
        let pages = 0;
        for (const memory of soak.memories) {
          try {
            pages += memory.buffer.byteLength / 65536;
          } catch {
            /* detached */
          }
        }
        return { blobs: soak.created - soak.revoked, mainWasmPages: Math.round(pages) };
      });
      let workerWasmPages = 0;
      for (const worker of page.workers()) {
        try {
          workerWasmPages += await worker.evaluate(() => {
            const memories =
              (self as unknown as { __soakWorkerMemories?: WebAssembly.Memory[] })
                .__soakWorkerMemories ?? [];
            let pages = 0;
            for (const memory of memories) {
              try {
                pages += memory.buffer.byteLength / 65536;
              } catch {
                /* detached */
              }
            }
            return Math.round(pages);
          });
        } catch {
          /* worker may have terminated */
        }
      }
      return {
        iter,
        phase,
        jsHeapMB: +(usedSize / 1024 / 1024).toFixed(2),
        nodes,
        listeners: jsEventListeners,
        blobs: main.blobs,
        mainWasmPages: main.mainWasmPages,
        workerWasmPages,
      };
    };

    const samples: Sample[] = [];
    await page.goto("/editor", { waitUntil: "domcontentloaded" });

    for (let iter = 1; iter <= ITERATIONS; iter++) {
      await page.getByTestId("files-button").click();
      await page.locator('[data-testid="file-input"]').first().setInputFiles(SAMPLE_PDF);
      await expect(page.locator('[data-page-index="0"]').first()).toBeAttached({
        timeout: 120_000,
      });
      await expect(
        page
          .locator('[data-page-index="0"] img[src^="blob:"], [data-page-index="0"] canvas')
          .first(),
      ).toBeAttached({ timeout: 120_000 });
      await page.waitForTimeout(500);
      samples.push(await read(iter, "open"));

      const row = page.locator(".file-sidebar-file-item").first();
      await expect(row).toBeVisible({ timeout: 30_000 });
      await row.hover();
      await row.locator(".file-sidebar-kebab-btn").click({ timeout: 30_000 });
      await page.getByRole("menuitem", { name: "Delete" }).click({ timeout: 30_000 });
      const confirm = page.getByRole("button", { name: /^(Delete|Confirm|Yes)/i }).last();
      if (await confirm.isVisible({ timeout: 1_000 }).catch(() => false)) {
        await confirm.click();
      }
      await page.waitForTimeout(500);
      samples.push(await read(iter, "removed"));
    }

    console.log(`[MEMORY-SOAK] ${JSON.stringify(samples)}`);
    for (const phase of ["open", "removed"] as const) {
      const series = samples.filter((s) => s.phase === phase);
      const early = series.filter((s) => s.iter <= 3);
      const late = series.filter((s) => s.iter > ITERATIONS - 3);
      const median = (values: number[]) => {
        const sorted = [...values].sort((a, b) => a - b);
        const mid = Math.floor(sorted.length / 2);
        return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
      };
      const heapEarly = median(early.map((s) => s.jsHeapMB));
      const heapLate = median(late.map((s) => s.jsHeapMB));
      const blobsEarly = median(early.map((s) => s.blobs));
      const blobsLate = median(late.map((s) => s.blobs));
      const nodesEarly = median(early.map((s) => s.nodes));
      const nodesLate = median(late.map((s) => s.nodes));
      const workerPagesEarly = median(early.map((s) => s.workerWasmPages));
      const workerPagesLate = median(late.map((s) => s.workerWasmPages));

      // Object URLs are deterministic: the eviction on file removal must keep
      // the count flat (a small delta covers cache warm-up).
      expect(
        blobsLate - blobsEarly,
        `${phase}: leaked object URLs over ${ITERATIONS} cycles`,
      ).toBeLessThanOrEqual(2);
      // DOM nodes are also deterministic; a growing count means the viewer or
      // sidebar keeps a removed document mounted.
      expect(
        nodesLate - nodesEarly,
        `${phase}: DOM nodes grew over ${ITERATIONS} cycles`,
      ).toBeLessThanOrEqual(50);
      // Wasm memory only grows, so a growing worker page count means PDFium
      // caches are not being recycled with the document. The bound tolerates
      // allocator slack while catching per-cycle accumulation.
      expect(
        workerPagesLate - workerPagesEarly,
        `${phase}: worker wasm pages grew over ${ITERATIONS} cycles`,
      ).toBeLessThanOrEqual(64);
      // Heap is noisier than node counts, so it is a wider sentinel than the
      // 10% manual gate; it still catches a monotonic per-cycle leak.
      expect(
        heapLate / heapEarly,
        `${phase}: retained heap grew over ${ITERATIONS} cycles`,
      ).toBeLessThan(1.25);
    }
  });
});

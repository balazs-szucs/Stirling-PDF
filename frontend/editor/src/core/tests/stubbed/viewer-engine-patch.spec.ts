/** Contract for the local @embedpdf/engines patch (scripts/patch-embedpdf-engines.mjs).
 *
 *  The patch is applied by postinstall into node_modules, so nothing else in the
 *  suite would notice if it silently stopped applying. Runs on all three
 *  engines, tagged like the other engine-capability specs. Keep small - it is
 *  paid for three times per PR. */

import path from "path";
import { test, expect } from "@app/tests/helpers/stub-test-base";
import { uploadFiles } from "@app/tests/helpers/ui-helpers";

const SAMPLE_PDF = path.join(import.meta.dirname, "../test-fixtures/sample.pdf");

/** Collect every blob URL created on the page, keyed by MIME type. */
const installBlobProbe = () => {
  const w = window as unknown as {
    __blobProbe: { types: Record<string, number>; bmp: Blob | null };
  };
  w.__blobProbe = { types: {}, bmp: null };
  const original = URL.createObjectURL.bind(URL);
  URL.createObjectURL = (obj: Blob | MediaSource) => {
    if (obj instanceof Blob) {
      const type = obj.type || "(empty)";
      w.__blobProbe.types[type] = (w.__blobProbe.types[type] ?? 0) + 1;
      if (obj.type === "image/bmp" && !w.__blobProbe.bmp) w.__blobProbe.bmp = obj;
    }
    return original(obj);
  };
};

test.describe("embedpdf engine patch", { tag: "@engine-capability" }, () => {
  // The probes must exist before the app boots; the fixture's default auto-goto
  // would load the app before addInitScript runs.
  test.use({ autoGoto: false });

  test("renders from a precompiled module and emits decodable BMP tiles", async ({
    page,
  }) => {
    test.setTimeout(120_000);
    const pageErrors: string[] = [];
    page.on("pageerror", (err) => pageErrors.push(err.message));
    await page.addInitScript(installBlobProbe);
    await page.goto("/editor", { waitUntil: "domcontentloaded" });
    await expect(page.getByTestId("files-button")).toBeVisible({ timeout: 60_000 });

    await uploadFiles(page, SAMPLE_PDF);
    const firstPage = page.locator('[data-page-index="0"]').first();
    await expect(firstPage).toBeAttached({ timeout: 60_000 });
    await expect(
      firstPage.locator('canvas, img[src^="blob:"], img[src^="data:"]').first(),
    ).toBeAttached({ timeout: 60_000 });
    await page.waitForTimeout(1_500);

    // The patched engine instantiates WebAssembly.Instance synchronously from
    // the module handed over in wasmInit; if the handoff regressed, the worker
    // fetches the .wasm URL itself and this count is > 0.
    const workerWasmFetches: Array<number | null> = [];
    for (const worker of page.workers()) {
      try {
        workerWasmFetches.push(
          await worker.evaluate(
            () =>
              performance
                .getEntriesByType("resource")
                .filter((entry) => entry.name.includes(".wasm")).length,
          ),
        );
      } catch {
        workerWasmFetches.push(null);
      }
    }
    expect(workerWasmFetches.length).toBeGreaterThan(0);
    expect(workerWasmFetches.every((n) => n === 0)).toBe(true);

    const probe = await page.evaluate(async () => {
      const w = window as unknown as {
        __blobProbe: { types: Record<string, number>; bmp: Blob | null };
      };
      const bmp = w.__blobProbe.bmp;
      let decoded: [number, number] | null = null;
      let decodeError: string | null = null;
      if (bmp) {
        try {
          const bitmap = await createImageBitmap(bmp);
          decoded = [bitmap.width, bitmap.height];
        } catch (err) {
          decodeError = String(err);
        }
      }
      return { types: w.__blobProbe.types, decoded, decodeError };
    });

    // image/bmp is the configured render type on the render and tiling plugins.
    expect(probe.types["image/bmp"] ?? 0).toBeGreaterThan(0);
    expect(probe.decodeError).toBeNull();
    expect(probe.decoded?.[0] ?? 0).toBeGreaterThan(0);
    expect(probe.decoded?.[1] ?? 0).toBeGreaterThan(0);
    expect(pageErrors).toEqual([]);
  });

  test("falls back to fetching wasm when the module cannot be structured-cloned", async ({
    page,
  }) => {
    test.setTimeout(120_000);
    const pageErrors: string[] = [];
    page.on("pageerror", (err) => pageErrors.push(err.message));

    await page.addInitScript(() => {
      const w = window as unknown as { __cloneFailures: number };
      w.__cloneFailures = 0;
      const original = Worker.prototype.postMessage;
      Worker.prototype.postMessage = function (
        this: Worker,
        message: unknown,
        transfer?: Transferable[],
      ) {
        const shape = message as { type?: string; wasmModule?: unknown } | null;
        if (shape && shape.type === "wasmInit" && shape.wasmModule !== undefined) {
          w.__cloneFailures++;
          throw new DOMException("Forced DataCloneError", "DataCloneError");
        }
        return original.call(this, message as never, transfer as never);
      };
    });

    await page.goto("/editor", { waitUntil: "domcontentloaded" });
    await expect(page.getByTestId("files-button")).toBeVisible({
      timeout: 60_000,
    });
    await uploadFiles(page, SAMPLE_PDF);
    const firstPage = page.locator('[data-page-index="0"]').first();
    await expect(firstPage).toBeAttached({ timeout: 60_000 });
    await expect(
      firstPage.locator('canvas, img[src^="blob:"], img[src^="data:"]').first(),
    ).toBeAttached({ timeout: 60_000 });

    const forced = await page.evaluate(
      () => (window as unknown as { __cloneFailures: number }).__cloneFailures,
    );
    expect(forced).toBeGreaterThan(0);
    expect(pageErrors).toEqual([]);
  });
});

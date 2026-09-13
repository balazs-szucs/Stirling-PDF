import path from "path";
import type { Page } from "@playwright/test";
import { test, expect } from "@app/tests/helpers/stub-test-base";

const SAMPLE_PDF = path.join(
  import.meta.dirname,
  "../test-fixtures/sample.pdf",
);

type PendingState = {
  pendingCount: number;
  activeDocumentId: string | null;
  diag?: unknown;
};

/**
 * Reads the redaction plugin's pending count through the app registry that
 * `LocalEmbedPDF` exposes while a document is mounted. This is the contract
 * the mark has to satisfy: a selection gesture must end the selection and the
 * redaction plugin must turn it into a pending item.
 */
function readPending(page: Page): Promise<PendingState> {
  return page.evaluate(() => {
    const w = window as unknown as { __embedPdfRegistry?: unknown };
    const reg = w.__embedPdfRegistry as
      | {
          getPlugin: (id: string) => {
            provides: () => {
              getState: () => { pendingCount: number };
              forDocument: (id: string) => {
                getState: () => { pendingCount: number };
              };
            };
          } | null;
          getStore: () => {
            getState: () => { core: { activeDocumentId: string | null } };
          };
          configurations?: Map<string, unknown>;
        }
      | undefined;
    if (!reg)
      return { pendingCount: -1, activeDocumentId: null, diag: "no-registry" };
    const core = reg.getStore().getState().core;
    const pluginIds = Array.from(reg.configurations?.keys?.() ?? []);
    const plugin = reg.getPlugin("redaction");
    if (!core.activeDocumentId)
      return {
        pendingCount: -1,
        activeDocumentId: null,
        diag: { pluginIds, reason: "no-doc" },
      };
    if (!plugin)
      return {
        pendingCount: -1,
        activeDocumentId: core.activeDocumentId,
        diag: { pluginIds, reason: "no-plugin" },
      };
    const cap = plugin.provides();
    const count =
      cap?.forDocument(core.activeDocumentId)?.getState().pendingCount ??
      cap?.getState().pendingCount ??
      -1;
    return {
      pendingCount: count,
      activeDocumentId: core.activeDocumentId,
      diag: { pluginIds },
    };
  });
}

async function loadViewerAndEnterRedaction(page: Page) {
  await page.goto("/editor");
  await page.locator('input[type="file"]').first().setInputFiles(SAMPLE_PDF);

  const firstPage = page.locator('[data-page-index="0"]').first();
  await expect(firstPage).toBeVisible({ timeout: 30_000 });
  await expect(firstPage.locator(".pdf-selection-layer")).toBeAttached({
    timeout: 15_000,
  });
  await page.waitForTimeout(2_000);

  const redactButton = page.getByRole("button", { name: /redact/i }).first();
  await expect(redactButton).toBeVisible({ timeout: 10_000 });
  await redactButton.click();

  // Redaction activation is deferred a tick after the tool switch.
  await expect
    .poll(
      () =>
        page.evaluate(() => {
          const el = document.querySelector('[data-page-index="0"]');
          return el?.parentElement
            ? getComputedStyle(el.parentElement).cursor
            : null;
        }),
      { timeout: 15_000 },
    )
    .toBe("crosshair");
  await page.waitForTimeout(600);

  return firstPage;
}

test.describe("manual redaction text selection", () => {
  test("drag-selecting text in redact mode creates a pending mark", async ({
    page,
  }) => {
    test.setTimeout(120_000);
    const firstPage = await loadViewerAndEnterRedaction(page);

    const box = await firstPage.boundingBox();
    if (!box) throw new Error("Page wrapper has no bounding box");
    const y = box.y + box.height * 0.105;
    await page.mouse.move(box.x + box.width * 0.15, y);
    await page.mouse.down();
    await page.mouse.move(box.x + box.width * 0.6, y, { steps: 15 });
    await page.mouse.up();

    await expect
      .poll(async () => (await readPending(page)).pendingCount, {
        timeout: 10_000,
      })
      .toBeGreaterThan(0);
  });

  test("double-clicking a word in redact mode creates a pending mark", async ({
    page,
  }) => {
    test.setTimeout(120_000);
    const firstPage = await loadViewerAndEnterRedaction(page);

    const box = await firstPage.boundingBox();
    if (!box) throw new Error("Page wrapper has no bounding box");
    await page.mouse.dblclick(
      box.x + box.width * 0.21,
      box.y + box.height * 0.105,
    );

    await expect
      .poll(async () => (await readPending(page)).pendingCount, {
        timeout: 10_000,
      })
      .toBeGreaterThan(0);
  });

  test("triple-clicking a line in redact mode creates a pending mark", async ({
    page,
  }) => {
    test.setTimeout(120_000);
    const firstPage = await loadViewerAndEnterRedaction(page);

    const box = await firstPage.boundingBox();
    if (!box) throw new Error("Page wrapper has no bounding box");
    const x = box.x + box.width * 0.21;
    const y = box.y + box.height * 0.105;
    await page.mouse.dblclick(x, y);
    await page.mouse.click(x, y);

    await expect
      .poll(async () => (await readPending(page)).pendingCount, {
        timeout: 10_000,
      })
      .toBeGreaterThan(0);
  });

  test("a pending redaction mark offers Apply and Remove for the mark", async ({
    page,
  }) => {
    test.setTimeout(120_000);
    const firstPage = await loadViewerAndEnterRedaction(page);

    const box = await firstPage.boundingBox();
    if (!box) throw new Error("Page wrapper has no bounding box");
    const y = box.y + box.height * 0.105;
    const startX = box.x + box.width * 0.15;
    const endX = box.x + box.width * 0.6;
    await page.mouse.move(startX, y);
    await page.mouse.down();
    await page.mouse.move(endX, y, { steps: 15 });
    await page.mouse.up();

    await expect
      .poll(async () => (await readPending(page)).pendingCount, {
        timeout: 10_000,
      })
      .toBeGreaterThan(0);

    // Select the mark: the redaction menu must replace the generic annotation
    // menu so the mark can be applied or removed individually.
    await page.mouse.click((startX + endX) / 2, y);
    const applyButton = page.getByRole("button", {
      name: /apply \(permanent\)/i,
    });
    await expect(applyButton).toBeVisible({ timeout: 10_000 });
    await expect(
      page.getByRole("button", { name: /remove this mark/i }),
    ).toBeVisible();

    await applyButton.click();
    await expect
      .poll(async () => (await readPending(page)).pendingCount, {
        timeout: 10_000,
      })
      .toBe(0);
  });
});

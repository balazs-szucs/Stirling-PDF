import path from "path";
import type { Page } from "@playwright/test";
import { test, expect } from "@app/tests/helpers/stub-test-base";

const SAMPLE_PDF = path.join(
  import.meta.dirname,
  "../test-fixtures/sample.pdf",
);

async function loadAndSelect(page: Page) {
  await page.goto("/editor");
  await page.locator('input[type="file"]').first().setInputFiles(SAMPLE_PDF);
  const firstPage = page.locator('[data-page-index="0"]').first();
  await expect(firstPage).toBeVisible({ timeout: 30_000 });
  await page.waitForTimeout(2_500);

  const box = await firstPage.boundingBox();
  if (!box) throw new Error("Page wrapper has no bounding box");
  const y = box.y + box.height * 0.105;
  await page.mouse.move(box.x + box.width * 0.15, y);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width * 0.6, y, { steps: 15 });
  await page.mouse.up();
  await page.waitForTimeout(800);
}

function annotationCount(page: Page, type: number): Promise<number> {
  return page.evaluate((annotationType) => {
    const reg = (
      window as unknown as {
        __embedPdfRegistry?: {
          getStore: () => {
            getState: () => {
              core: { activeDocumentId: string | null };
              plugins: {
                annotation?: {
                  documents?: Record<
                    string,
                    { byUid?: Record<string, { object?: { type?: number } }> }
                  >;
                };
              };
            };
          };
        };
      }
    ).__embedPdfRegistry;
    if (!reg) return -1;
    const state = reg.getStore().getState();
    const id = state.core.activeDocumentId;
    if (!id) return -1;
    const byUid = state.plugins.annotation?.documents?.[id]?.byUid ?? {};
    return Object.values(byUid).filter((a) => a.object?.type === annotationType)
      .length;
  }, type);
}

function pendingRedactions(page: Page): Promise<number> {
  return page.evaluate(() => {
    const reg = (
      window as unknown as {
        __embedPdfRegistry?: {
          getPlugin: (id: string) => {
            provides: () => {
              forDocument: (id: string) => {
                getState: () => { pendingCount: number };
              };
            };
          } | null;
          getStore: () => {
            getState: () => { core: { activeDocumentId: string | null } };
          };
        };
      }
    ).__embedPdfRegistry;
    if (!reg) return -1;
    const id = reg.getStore().getState().core.activeDocumentId;
    if (!id) return -1;
    return (
      reg.getPlugin("redaction")?.provides().forDocument(id).getState()
        .pendingCount ?? -1
    );
  });
}

test.describe("text selection menu", () => {
  test("drag-selecting text offers annotation and redaction actions", async ({
    page,
  }) => {
    test.setTimeout(120_000);
    await loadAndSelect(page);

    const menu = page.locator("[data-text-selection-menu]");
    await expect(menu).toBeVisible({ timeout: 5_000 });
    for (const label of [
      "Copy",
      "Highlight",
      "Strikeout",
      "Underline",
      "Squiggly",
      "Add link",
      "Redact",
    ]) {
      await expect(menu.getByLabel(label, { exact: true })).toBeVisible({
        timeout: 5_000,
      });
    }
  });

  test("Highlight creates a highlight annotation from the selection", async ({
    page,
  }) => {
    test.setTimeout(120_000);
    await loadAndSelect(page);

    await page
      .locator("[data-text-selection-menu]")
      .getByLabel("Highlight", { exact: true })
      .click();
    await expect
      .poll(async () => annotationCount(page, 9), { timeout: 10_000 })
      .toBeGreaterThan(0);
  });

  test("Redact creates a pending redaction and opens the redaction panel", async ({
    page,
  }) => {
    test.setTimeout(120_000);
    await loadAndSelect(page);

    await page
      .locator("[data-text-selection-menu]")
      .getByLabel("Redact", { exact: true })
      .click();

    await expect
      .poll(async () => pendingRedactions(page), { timeout: 15_000 })
      .toBeGreaterThan(0);
    await expect(
      page.getByRole("button", { name: /apply redactions/i }).first(),
    ).toBeVisible({ timeout: 10_000 });
  });

  test("Link adds a URI annotation from the selection", async ({ page }) => {
    test.setTimeout(120_000);
    await loadAndSelect(page);

    await page
      .locator("[data-text-selection-menu]")
      .getByLabel("Add link", { exact: true })
      .click();
    const input = page.getByPlaceholder("https://...");
    await expect(input).toBeVisible({ timeout: 5_000 });
    await input.fill("https://example.com/");
    await page
      .locator(".mantine-Popover-dropdown")
      .getByRole("button", { name: "Add link" })
      .click();

    await expect
      .poll(async () => annotationCount(page, 2), { timeout: 10_000 })
      .toBeGreaterThan(0);
  });

  test("the menu stays hidden while redaction mode is active", async ({
    page,
  }) => {
    test.setTimeout(120_000);
    await page.goto("/editor");
    await page.locator('input[type="file"]').first().setInputFiles(SAMPLE_PDF);
    const firstPage = page.locator('[data-page-index="0"]').first();
    await expect(firstPage).toBeVisible({ timeout: 30_000 });
    await page.waitForTimeout(2_500);

    await page
      .getByRole("button", { name: /redact/i })
      .first()
      .click();
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

    const box = await firstPage.boundingBox();
    if (!box) throw new Error("Page wrapper has no bounding box");
    const y = box.y + box.height * 0.105;
    await page.mouse.move(box.x + box.width * 0.15, y);
    await page.mouse.down();
    await page.mouse.move(box.x + box.width * 0.6, y, { steps: 15 });
    await page.mouse.up();
    await page.waitForTimeout(1_000);

    await expect(page.locator("[data-text-selection-menu]")).toHaveCount(0);
  });
});

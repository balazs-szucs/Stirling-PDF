import path from "path";
import type { Page } from "@playwright/test";
import { test, expect } from "@app/tests/helpers/stub-test-base";

const BIG_PDF = path.join(
  import.meta.dirname,
  "../test-fixtures/big-sample.pdf",
);

async function upload(page: Page) {
  await page.getByTestId("files-button").click();
  await page
    .locator('[data-testid="file-input"]')
    .first()
    .setInputFiles(BIG_PDF);
}

async function expectFirstPageRendered(page: Page) {
  await expect(page.locator('[data-page-index="0"]').first()).toBeAttached({
    timeout: 120_000,
  });
  await expect(
    page
      .locator(
        '[data-page-index="0"] img[src^="blob:"], [data-page-index="0"] canvas',
      )
      .first(),
  ).toBeAttached({ timeout: 120_000 });
}

async function removeFile(page: Page) {
  const row = page.locator(".file-sidebar-file-item").first();
  await expect(row).toBeVisible({ timeout: 30_000 });
  await row.hover();
  await row.locator(".file-sidebar-kebab-btn").click({ timeout: 30_000 });
  await page
    .getByRole("menuitem", { name: "Delete" })
    .click({ timeout: 30_000 });
  const confirm = page
    .getByRole("button", { name: /^(Delete|Confirm|Yes)/i })
    .last();
  if (await confirm.isVisible({ timeout: 1_000 }).catch(() => false)) {
    await confirm.click();
  }
}

test.describe("viewer document lifecycle", () => {
  test("opening renders the first page and settles without a loader", async ({
    page,
  }) => {
    test.setTimeout(180_000);
    await page.goto("/editor", { waitUntil: "domcontentloaded" });
    await upload(page);
    await expectFirstPageRendered(page);
    await page.waitForTimeout(2_000);
    await expect(page.getByText("Loading PDF Engine...")).toHaveCount(0);
    await expect(page.getByText("Preparing document...")).toHaveCount(0);
  });

  test("scrolling renders pages beyond the initial window", async ({
    page,
  }) => {
    test.setTimeout(180_000);
    await page.goto("/editor", { waitUntil: "domcontentloaded" });
    await upload(page);
    await expectFirstPageRendered(page);
    await page.mouse.move(400, 500);
    for (let step = 0; step < 12; step++) {
      await page.mouse.wheel(0, 1400);
      await page.waitForTimeout(400);
      if ((await page.locator('[data-page-index="5"]').count()) > 0) break;
    }
    await expect(page.locator('[data-page-index="5"]').first()).toBeAttached({
      timeout: 60_000,
    });
  });

  test("removing and reopening the same file renders again", async ({
    page,
  }) => {
    test.setTimeout(240_000);
    await page.goto("/editor", { waitUntil: "domcontentloaded" });
    await upload(page);
    await expectFirstPageRendered(page);
    await removeFile(page);
    await upload(page);
    await expectFirstPageRendered(page);
  });
});

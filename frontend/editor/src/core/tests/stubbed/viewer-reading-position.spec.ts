import { test, expect } from "@app/tests/helpers/stub-test-base";
import path from "path";

const FIXTURES_DIR = path.join(import.meta.dirname, "../test-fixtures");
const MULTIPAGE_PDF = path.join(FIXTURES_DIR, "annotations_out_of_order.pdf");

test.describe("Reading position continuity", () => {
  test("reopens at the last read page and offers Go to start", async ({
    page,
  }) => {
    test.setTimeout(150_000);

    await page.locator('input[type="file"]').first().setInputFiles(MULTIPAGE_PDF);
    await expect(page.locator('[data-page-index="0"]').first()).toBeVisible({
      timeout: 30_000,
    });

    // A cover position is not "resumed" — no toast on a fresh open.
    await expect(page.getByText("Resumed where you left off")).toHaveCount(0);

    const pageInput = page.getByRole("textbox", { name: /page navigation/i });
    await pageInput.fill("2");
    await expect(page.locator('[data-page-index="1"]').first()).toBeVisible({
      timeout: 15_000,
    });
    // Let the debounced writer land in IndexedDB.
    await page.waitForTimeout(1_200);

    await page.reload({ waitUntil: "domcontentloaded" });

    await expect(page.locator('[data-page-index="1"]').first()).toBeVisible({
      timeout: 45_000,
    });
    await expect(page.getByText("Resumed where you left off")).toBeVisible({
      timeout: 10_000,
    });

    await page.getByRole("button", { name: "Go to start" }).click();
    await expect(page.locator('[data-page-index="0"]').first()).toBeVisible({
      timeout: 15_000,
    });
  });
});

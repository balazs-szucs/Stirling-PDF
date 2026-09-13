import { describe, it, expect } from "vitest";
import { createProcessedFile } from "@app/contexts/file/fileActions";
import {
  getPageDimensions,
  getFirstPageDimensionsFromMetadata,
  getFirstPageAspectRatioFromMetadata,
} from "@app/utils/pageMetadata";

describe("processedFile.pages consumer contracts (R4)", () => {
  it("preserves totalPages and pages array length matching pageCount", () => {
    const processed = createProcessedFile(
      50,
      "data:image/jpeg;base64,thumb",
      [0, 90, 180, 270],
      [{ width: 612, height: 792 }],
    );

    expect(processed.totalPages).toBe(50);
    expect(processed.pages).toHaveLength(50);
    expect(processed.pages[0].pageNumber).toBe(1);
    expect(processed.pages[49].pageNumber).toBe(50);
  });

  it("provides page 0 dimensions eagerly to getFirstPageDimensions and aspect ratio", () => {
    const processed = createProcessedFile(
      10,
      "data:image/jpeg;base64,thumb",
      [0, 0, 0],
      [{ width: 612, height: 792 }],
    );

    const dims = getFirstPageDimensionsFromMetadata(processed);
    expect(dims).toEqual({ width: 612, height: 792 });

    const ratio = getFirstPageAspectRatioFromMetadata(processed);
    expect(ratio).toBeCloseTo(792 / 612);
  });

  it("preserves page rotations across all pages for PageEditor upright rendering", () => {
    const rotations = [0, 90, 180, 270, 0, 90];
    const processed = createProcessedFile(
      6,
      "data:image/jpeg;base64,thumb",
      rotations,
      [{ width: 612, height: 792 }],
    );

    rotations.forEach((expectedRotation, index) => {
      expect(processed.pages[index].rotation).toBe(expectedRotation);
    });
  });

  it("safely handles deferred (undefined) dimensions for pages > 0", () => {
    const processed = createProcessedFile(
      5,
      "data:image/jpeg;base64,thumb",
      [0, 90, 0, 0, 0],
      [{ width: 612, height: 792 }], // only page 0
    );

    // Page 0 has full dimensions
    expect(getPageDimensions(processed.pages[0])).toEqual({
      width: 612,
      height: 792,
    });

    // Page 1 has deferred dimensions (returns null safely)
    expect(getPageDimensions(processed.pages[1])).toEqual({
      width: null,
      height: null,
    });
  });
});

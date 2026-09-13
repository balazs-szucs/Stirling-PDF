import { describe, expect, it, vi, afterEach } from "vitest";
import type { Mock } from "vitest";
import { renderHook } from "@testing-library/react";

vi.mock("@app/services/thumbnailGenerationService", () => ({
  thumbnailGenerationService: {
    getThumbnailFromCache: vi.fn(() => null),
    addThumbnailToCache: vi.fn(),
    generateThumbnails: vi.fn(
      async (_fileId: unknown, _bytes: unknown, pageNumbers: number[]) =>
        pageNumbers.map((pageNumber) => ({
          pageNumber,
          thumbnail: `blob:thumb-${pageNumber}`,
          success: true,
        })),
    ),
    destroy: vi.fn(),
    stopGeneration: vi.fn(),
  },
}));

import { thumbnailGenerationService } from "@app/services/thumbnailGenerationService";
import { useThumbnailGeneration } from "@app/hooks/useThumbnailGeneration";

const generateMock = thumbnailGenerationService.generateThumbnails as Mock;
const destroyMock = thumbnailGenerationService.destroy as Mock;

const pdfFile = (name: string) =>
  new File(["%PDF-1.4 fake"], name, { type: "application/pdf" });

describe("useThumbnailGeneration destroy", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("settles still-queued requests with null instead of dropping them", async () => {
    const { result } = renderHook(() => useThumbnailGeneration());
    const pending = result.current.requestThumbnail(
      "mh5-queued",
      pdfFile("queued.pdf"),
      1,
    );
    result.current.destroyThumbnails();
    await expect(pending).resolves.toBeNull();
    expect(destroyMock).toHaveBeenCalledTimes(1);
  });

  it("leaves the normal queued path resolving service thumbnails", async () => {
    const { result } = renderHook(() => useThumbnailGeneration());
    const thumbnail = await result.current.requestThumbnail(
      "mh5-normal",
      pdfFile("normal.pdf"),
      2,
    );
    expect(thumbnail).toBe("blob:thumb-2");
    expect(generateMock).toHaveBeenCalledTimes(1);
    result.current.destroyThumbnails();
  });
});

import { describe, expect, it, vi, beforeEach } from "vitest";
import type { Mock } from "vitest";

vi.mock("@app/services/pdfiumService", () => ({
  openRawDocumentSafe: vi.fn(),
  closeRawDocument: vi.fn(),
}));

vi.mock("@app/utils/pdfiumPageRender", () => ({
  renderPdfiumPageDataUrl: vi.fn(),
}));

import {
  ThumbnailGenerationService,
  thumbnailGenerationService,
} from "@app/services/thumbnailGenerationService";
import {
  openRawDocumentSafe,
  closeRawDocument,
} from "@app/services/pdfiumService";
import { renderPdfiumPageDataUrl } from "@app/utils/pdfiumPageRender";
import type { FileId } from "@app/types/file";

const openMock = openRawDocumentSafe as Mock;
const closeMock = closeRawDocument as Mock;
const renderMock = renderPdfiumPageDataUrl as Mock;

const fileId = (n: number | string) => `test-file-${n}` as FileId;
const pdfBytes = () => new ArrayBuffer(8);
const settle = (ms = 0) => new Promise((r) => setTimeout(r, ms));

describe("ThumbnailGenerationService document cache", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    thumbnailGenerationService.clearCache();
    thumbnailGenerationService.clearPDFCache();
    let nextPtr = 0;
    openMock.mockImplementation(async () => ++nextPtr);
    renderMock.mockImplementation(async () => "data:image/jpeg;base64,x");
  });

  it("serves an 11th concurrent document instead of hanging the queue", async () => {
    const service = new ThumbnailGenerationService();
    let releaseRenders!: () => void;
    const gate = new Promise<void>((resolve) => {
      releaseRenders = resolve;
    });
    renderMock.mockImplementation(() => gate.then(() => "data:image/jpeg;base64,x"));

    const firstTen = Array.from({ length: 10 }, (_, i) =>
      service.generateThumbnails(fileId(i), pdfBytes(), [1], {}),
    );
    await settle();
    expect(openMock).toHaveBeenCalledTimes(10);

    releaseRenders();
    const eleventh = service.generateThumbnails(fileId("late"), pdfBytes(), [1], {});
    const winner = await Promise.race([
      eleventh.then(() => "resolved"),
      settle(5000).then(() => "hung"),
    ]);
    expect(winner).toBe("resolved");

    const results = await eleventh;
    expect(results).toHaveLength(1);
    expect(results[0]?.success).toBe(true);
    // The uncached document opens privately and closes straight after use.
    expect(openMock).toHaveBeenCalledTimes(11);
    expect(closeMock).toHaveBeenCalledWith(11);
    await Promise.all(firstTen);
  }, 15000);

  it("releases the document reference when progress reporting throws", async () => {
    const service = new ThumbnailGenerationService();
    const id = fileId("throwing");
    await expect(
      service.generateThumbnails(id, pdfBytes(), [1], {}, () => {
        throw new Error("listener blew up");
      }),
    ).rejects.toThrow("listener blew up");

    // The failed call must not pin its cache entry: the next call reopens.
    renderMock.mockImplementation(async () => "data:image/jpeg;base64,x");
    const retry = await service.generateThumbnails(id, pdfBytes(), [1], {});
    expect(retry[0]?.success).toBe(true);
    expect(openMock).toHaveBeenCalledTimes(2);
  });
});

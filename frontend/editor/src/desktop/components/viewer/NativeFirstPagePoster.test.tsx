/**
 * Contract for the first-paint poster: it shows page 1 rendered natively as
 * soon as a file with a disk path lands, revokes its blob URL on unmount, and
 * gets out of the way once the engine has painted a page of its own.
 */
import { act, render, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  blob: null as Blob | null,
  calls: [] as Array<[string, number, number]>,
  revoked: [] as string[],
}));

vi.mock("@app/services/nativePdfRender", () => ({
  renderNativePdfPageBlob: async (
    path: string,
    page: number,
    width: number,
  ) => {
    mocks.calls.push([path, page, width]);
    return mocks.blob;
  },
}));

import { NativeFirstPagePoster } from "@app/components/viewer/NativeFirstPagePoster";

const posterImage = () =>
  document.querySelector<HTMLImageElement>('img[src^="blob:poster"]');

describe("NativeFirstPagePoster (desktop)", () => {
  beforeEach(() => {
    mocks.blob = new Blob(["jpeg"], { type: "image/jpeg" });
    mocks.calls.length = 0;
    mocks.revoked.length = 0;
    let sequence = 0;
    URL.createObjectURL = vi.fn(() => `blob:poster-${(sequence += 1)}`) as never;
    URL.revokeObjectURL = vi.fn((url: string) => {
      mocks.revoked.push(url);
    }) as never;
    document
      .querySelectorAll("[data-page-index]")
      .forEach((element) => element.remove());
  });

  test("shows page 1 natively for a file with a disk path", async () => {
    render(<NativeFirstPagePoster filePath="/tmp/report.pdf" />);
    await waitFor(() => expect(posterImage()).not.toBeNull());
    expect(mocks.calls).toEqual([["/tmp/report.pdf", 1, 1200]]);
  });

  test("shows nothing without a disk path", async () => {
    render(<NativeFirstPagePoster filePath={null} />);
    await act(() => Promise.resolve());
    expect(posterImage()).toBeNull();
    expect(mocks.calls).toHaveLength(0);
  });

  test("revokes its blob URL on unmount", async () => {
    const { unmount } = render(
      <NativeFirstPagePoster filePath="/tmp/report.pdf" />,
    );
    await waitFor(() => expect(posterImage()).not.toBeNull());
    unmount();
    expect(mocks.revoked).toEqual(["blob:poster-1"]);
  });

  test("yields to the engine once a page paints", async () => {
    vi.useFakeTimers();
    try {
      render(<NativeFirstPagePoster filePath="/tmp/report.pdf" />);
      await act(() => Promise.resolve());
      expect(posterImage()).not.toBeNull();

      const page = document.createElement("div");
      page.setAttribute("data-page-index", "0");
      page.appendChild(document.createElement("canvas"));
      document.body.appendChild(page);

      await act(() => vi.advanceTimersByTimeAsync(300));
      expect(posterImage()).toBeNull();
      expect(mocks.revoked).toEqual(["blob:poster-1"]);
    } finally {
      vi.useRealTimers();
    }
  });
});

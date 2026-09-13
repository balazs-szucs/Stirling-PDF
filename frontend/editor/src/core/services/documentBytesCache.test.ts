/** Contract of the shared document-bytes cache: one read per live Blob,
 * concurrent callers share it, and a failed read is retryable. */
import { describe, expect, it, vi } from "vitest";
import { getDocumentBytes } from "@app/services/documentBytesCache";

describe("documentBytesCache", () => {
  it("serves one read per live Blob and returns the same bytes", async () => {
    const blob = new Blob([new Uint8Array([1, 2, 3])]);
    const spy = vi.spyOn(blob, "arrayBuffer");

    const first = await getDocumentBytes(blob);
    const second = await getDocumentBytes(blob);

    expect(spy).toHaveBeenCalledTimes(1);
    expect(second).toBe(first);
    expect(Array.from(new Uint8Array(first))).toEqual([1, 2, 3]);
  });

  it("deduplicates concurrent reads", async () => {
    const blob = new Blob([new Uint8Array([9])]);
    const spy = vi.spyOn(blob, "arrayBuffer");

    const [a, b] = await Promise.all([
      getDocumentBytes(blob),
      getDocumentBytes(blob),
    ]);

    expect(spy).toHaveBeenCalledTimes(1);
    expect(a).toBe(b);
  });

  it("does not cache a failed read, so a retry re-reads", async () => {
    const blob = new Blob([new Uint8Array([1])]);
    const spy = vi
      .spyOn(blob, "arrayBuffer")
      .mockRejectedValueOnce(new Error("nope"));

    await expect(getDocumentBytes(blob)).rejects.toThrow("nope");
    const buffer = await getDocumentBytes(blob);
    expect(buffer.byteLength).toBe(1);
    expect(spy).toHaveBeenCalledTimes(2);
  });
});

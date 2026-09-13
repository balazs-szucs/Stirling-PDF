/** Contract of the shared document-bytes cache: one read per live Blob (or per
 * identical File wrapper), concurrent callers share it, and a failed read is
 * retryable. */
import { describe, expect, it, vi } from "vitest";
import {
  getDocumentBytes,
  getFormVerdict,
  noteFormVerdict,
  releaseDocumentBytes,
} from "@app/services/documentBytesCache";

const makeFile = (name: string, bytes: number[], lastModified = 1000) =>
  new File([new Uint8Array(bytes)], name, {
    type: "application/pdf",
    lastModified,
  });

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

  it("shares one read across File wrappers with identical metadata", async () => {
    const first = makeFile("wrapped-once.pdf", [1, 2, 3]);
    const second = makeFile("wrapped-once.pdf", [1, 2, 3]);
    const firstSpy = vi.spyOn(first, "arrayBuffer");
    const secondSpy = vi.spyOn(second, "arrayBuffer");

    const [a, b] = await Promise.all([
      getDocumentBytes(first),
      getDocumentBytes(second),
    ]);

    expect(a).toBe(b);
    expect(firstSpy.mock.calls.length + secondSpy.mock.calls.length).toBe(1);
  });

  it("re-reads when a File's metadata changes", async () => {
    const original = makeFile("mtime.pdf", [1, 2, 3], 1000);
    const edited = makeFile("mtime.pdf", [4, 5, 6], 2000);

    const a = await getDocumentBytes(original);
    const b = await getDocumentBytes(edited);

    expect(b).not.toBe(a);
    expect(Array.from(new Uint8Array(b))).toEqual([4, 5, 6]);
  });

  it("keeps bare Blobs identity-keyed (size alone must not share)", async () => {
    const a = new Blob([new Uint8Array([1, 2, 3])]);
    const b = new Blob([new Uint8Array([4, 5, 6])]);

    const first = await getDocumentBytes(a);
    const second = await getDocumentBytes(b);

    expect(second).not.toBe(first);
    expect(Array.from(new Uint8Array(second))).toEqual([4, 5, 6]);
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

  it("releases cached ArrayBuffer entry via releaseDocumentBytes and re-reads on demand", async () => {
    const file = makeFile("release-test.pdf", [10, 20, 30]);
    const spy = vi.spyOn(file, "arrayBuffer");

    const first = await getDocumentBytes(file);
    expect(first.byteLength).toBe(3);
    expect(spy).toHaveBeenCalledTimes(1);

    // Explicit release
    releaseDocumentBytes(file);

    // Next getDocumentBytes must re-read from the File
    const second = await getDocumentBytes(file);
    expect(second.byteLength).toBe(3);
    expect(spy).toHaveBeenCalledTimes(2);
    expect(second).not.toBe(first);
    expect(Array.from(new Uint8Array(second))).toEqual([10, 20, 30]);
  });
});

describe("form verdict sharing", () => {
  it("returns undefined for files nobody judged", () => {
    expect(
      getFormVerdict(makeFile("unknown.pdf", [1, 2, 3], 2000)),
    ).toBeUndefined();
  });

  it("shares the recorded verdict across re-wrapped Files", () => {
    const first = makeFile("wrapped.pdf", [1], 3000);
    noteFormVerdict(first, false);
    const second = makeFile("wrapped.pdf", [1], 3000);
    expect(getFormVerdict(second)).toBe(false);
  });

  it("latest verdict wins per file key", () => {
    const file = makeFile("flip.pdf", [1], 4000);
    noteFormVerdict(file, false);
    noteFormVerdict(file, true);
    expect(getFormVerdict(file)).toBe(true);
  });

  it("ignores bare Blobs without crashing", () => {
    const blob = new Blob([new Uint8Array([1])]);
    expect(() => noteFormVerdict(blob, false)).not.toThrow();
    expect(getFormVerdict(blob)).toBeUndefined();
  });
});

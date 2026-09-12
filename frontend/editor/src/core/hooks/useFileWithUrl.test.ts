import { describe, expect, it, vi, beforeEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { useFileWithUrl } from "@app/hooks/useFileWithUrl";

describe("useFileWithUrl bare-Blob keys", () => {
  beforeEach(() => {
    let next = 0;
    vi.mocked(URL.createObjectURL).mockImplementation(
      () => `blob:mock-${++next}`,
    );
  });

  it("gives distinct same-size Blobs distinct URLs", () => {
    const a = new Blob(["aaa"]);
    const b = new Blob(["bbb"]);
    expect(a.size).toBe(b.size);

    const { result: first } = renderHook(() => useFileWithUrl(a));
    const { result: second } = renderHook(() => useFileWithUrl(b));

    expect(first.current?.url).not.toBe(second.current?.url);
  });

  it("reuses the URL for the same Blob", () => {
    const a = new Blob(["aaa"]);

    const { result: first } = renderHook(() => useFileWithUrl(a));
    const { result: second } = renderHook(() => useFileWithUrl(a));

    expect(first.current?.url).toBe(second.current?.url);
  });
});

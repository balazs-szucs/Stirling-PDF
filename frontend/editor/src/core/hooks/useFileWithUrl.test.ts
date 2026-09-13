import { describe, expect, it, vi, beforeEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { useFileWithUrl, evictFileUrl } from "@app/hooks/useFileWithUrl";

describe("useFileWithUrl bare-Blob keys and eviction", () => {
  beforeEach(() => {
    let next = 0;
    vi.mocked(URL.createObjectURL).mockImplementation(
      () => `blob:mock-${++next}`,
    );
    vi.mocked(URL.revokeObjectURL).mockClear();
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

  it("evicts bare Blob URL directly via Blob reference", () => {
    const a = new Blob(["aaa"]);
    const { result: first } = renderHook(() => useFileWithUrl(a));
    const url1 = first.current?.url;
    expect(url1).toBeDefined();

    evictFileUrl(a);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith(url1);

    const { result: second } = renderHook(() => useFileWithUrl(a));
    expect(second.current?.url).not.toBe(url1);
  });

  it("evicts File URL directly via File reference", () => {
    const file = new File(["test-content"], "test.pdf", {
      type: "application/pdf",
      lastModified: 1000,
    });
    const { result: first } = renderHook(() => useFileWithUrl(file));
    const url1 = first.current?.url;
    expect(url1).toBeDefined();

    evictFileUrl(file);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith(url1);

    const { result: second } = renderHook(() => useFileWithUrl(file));
    expect(second.current?.url).not.toBe(url1);
  });

  it("evicts URL via string key", () => {
    const file = new File(["test-key"], "test-key.pdf", {
      type: "application/pdf",
    });
    const { result: first } = renderHook(() =>
      useFileWithUrl(file, "custom-key-123"),
    );
    const url1 = first.current?.url;
    expect(url1).toBeDefined();

    evictFileUrl("custom-key-123");
    expect(URL.revokeObjectURL).toHaveBeenCalledWith(url1);
  });
});

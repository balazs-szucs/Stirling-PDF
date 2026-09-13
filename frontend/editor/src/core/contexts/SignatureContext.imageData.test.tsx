/**
 * imageDataStore lifecycle: a deleted annotation's entry must leave the store
 * (with its blob URL revoked), not linger for the provider's lifetime.
 */
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { render, cleanup, act } from "@testing-library/react";
import { SignatureProvider, useSignature } from "@app/contexts/SignatureContext";

let probe: {
  store: (id: string, data: string) => void;
  get: (id: string) => string | undefined;
  del: (id: string) => void;
};

function Harness() {
  const { storeImageData, getImageData, deleteImageData } = useSignature();
  probe = { store: storeImageData, get: getImageData, del: deleteImageData };
  return null;
}

describe("SignatureContext imageDataStore", () => {
  let revoke: ReturnType<typeof vi.fn>;
  let createObjectUrl: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    createObjectUrl = vi.fn(() => "blob:mock-url");
    revoke = vi.fn();
    URL.createObjectURL = createObjectUrl as typeof URL.createObjectURL;
    URL.revokeObjectURL = revoke as unknown as typeof URL.revokeObjectURL;
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("deleteImageData removes the entry and revokes a blob URL", () => {
    render(
      <SignatureProvider>
        <Harness />
      </SignatureProvider>,
    );

    act(() => probe.store("ann-1", "blob:mock-url"));
    expect(probe.get("ann-1")).toBe("blob:mock-url");

    act(() => probe.del("ann-1"));
    expect(probe.get("ann-1")).toBeUndefined();
    expect(revoke).toHaveBeenCalledWith("blob:mock-url");
  });

  it("deleteImageData removes a data URL without revoking", () => {
    render(
      <SignatureProvider>
        <Harness />
      </SignatureProvider>,
    );

    act(() => probe.store("ann-2", "data:image/png;base64,AAAA"));
    act(() => probe.del("ann-2"));
    expect(probe.get("ann-2")).toBeUndefined();
    expect(revoke).not.toHaveBeenCalled();
  });
});

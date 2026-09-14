import { describe, it, expect, beforeEach, afterEach } from "vitest";
import {
  isPrefetchEnabled,
  computeDirectionalPrefetchTarget,
} from "@app/components/viewer/prefetch";

describe("prefetch contract", () => {
  const originalWindowLocation = window.location;

  beforeEach(() => {
    delete (window as unknown as { __PERF_PREFETCH?: boolean }).__PERF_PREFETCH;
  });

  afterEach(() => {
    delete (window as unknown as { __PERF_PREFETCH?: boolean }).__PERF_PREFETCH;
    Object.defineProperty(window, "location", {
      writable: true,
      value: originalWindowLocation,
    });
  });

  describe("isPrefetchEnabled", () => {
    it("defaults to true so scrolling prefetches without a flag", () => {
      expect(isPrefetchEnabled()).toBe(true);
    });

    it("disables via window.__PERF_PREFETCH = false", () => {
      (window as unknown as { __PERF_PREFETCH?: boolean }).__PERF_PREFETCH =
        false;
      expect(isPrefetchEnabled()).toBe(false);
    });

    it("stays enabled via window.__PERF_PREFETCH = true", () => {
      (window as unknown as { __PERF_PREFETCH?: boolean }).__PERF_PREFETCH =
        true;
      expect(isPrefetchEnabled()).toBe(true);
    });

    it("disables when search params contains prefetch=0", () => {
      Object.defineProperty(window, "location", {
        writable: true,
        value: { ...originalWindowLocation, search: "?prefetch=0" },
      });
      expect(isPrefetchEnabled()).toBe(false);
    });

    it("stays enabled when search params contains perf_prefetch=1", () => {
      Object.defineProperty(window, "location", {
        writable: true,
        value: { ...originalWindowLocation, search: "?perf_prefetch=1" },
      });
      expect(isPrefetchEnabled()).toBe(true);
    });
  });

  describe("computeDirectionalPrefetchTarget", () => {
    it("returns null when stationary (scrollDelta === 0)", () => {
      expect(computeDirectionalPrefetchTarget([0], 10, 0)).toBeNull();
    });

    it("prefetches next page when scrolling down", () => {
      expect(computeDirectionalPrefetchTarget([0], 10, 50)).toBe(1);
      expect(computeDirectionalPrefetchTarget([1, 2], 10, 20)).toBe(3);
    });

    it("returns null when scrolling down past document end", () => {
      expect(computeDirectionalPrefetchTarget([9], 10, 50)).toBeNull();
    });

    it("prefetches previous page when scrolling up", () => {
      expect(computeDirectionalPrefetchTarget([3], 10, -50)).toBe(2);
      expect(computeDirectionalPrefetchTarget([4, 5], 10, -20)).toBe(3);
    });

    it("returns null when scrolling up past page 0", () => {
      expect(computeDirectionalPrefetchTarget([0], 10, -50)).toBeNull();
    });

    it("handles boundary / invalid inputs gracefully", () => {
      expect(computeDirectionalPrefetchTarget([], 10, 50)).toBeNull();
      expect(computeDirectionalPrefetchTarget([0], 0, 50)).toBeNull();
    });
  });
});

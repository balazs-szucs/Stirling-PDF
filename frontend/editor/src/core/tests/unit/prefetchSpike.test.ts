import { describe, it, expect, beforeEach, afterEach } from "vitest";
import {
  isPrefetchSpikeEnabled,
  computeDirectionalPrefetchTarget,
} from "@app/components/viewer/prefetchSpike";

describe("prefetchSpike contract characterization (R5)", () => {
  const originalWindowLocation = window.location;

  beforeEach(() => {
    delete (window as unknown as { __PERF_PREFETCH?: boolean }).__PERF_PREFETCH;
  });

  afterEach(() => {
    delete (window as unknown as { __PERF_PREFETCH?: boolean }).__PERF_PREFETCH;
  });

  describe("isPrefetchSpikeEnabled", () => {
    it("defaults to false in standard environment", () => {
      expect(isPrefetchSpikeEnabled()).toBe(false);
    });

    it("activates when window.__PERF_PREFETCH is set", () => {
      (window as unknown as { __PERF_PREFETCH?: boolean }).__PERF_PREFETCH = true;
      expect(isPrefetchSpikeEnabled()).toBe(true);
    });

    it("activates when search params contains prefetch=1", () => {
      try {
        Object.defineProperty(window, "location", {
          writable: true,
          value: { ...originalWindowLocation, search: "?prefetch=1" },
        });
        expect(isPrefetchSpikeEnabled()).toBe(true);
      } finally {
        Object.defineProperty(window, "location", {
          writable: true,
          value: originalWindowLocation,
        });
      }
    });

    it("activates when search params contains perf_prefetch=1", () => {
      try {
        Object.defineProperty(window, "location", {
          writable: true,
          value: { ...originalWindowLocation, search: "?perf_prefetch=1" },
        });
        expect(isPrefetchSpikeEnabled()).toBe(true);
      } finally {
        Object.defineProperty(window, "location", {
          writable: true,
          value: originalWindowLocation,
        });
      }
    });
  });

  describe("computeDirectionalPrefetchTarget", () => {
    it("returns null when stationary (scrollDelta === 0) to avoid idle-first-page regressions", () => {
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

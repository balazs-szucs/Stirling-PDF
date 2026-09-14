import { describe, it, expect, beforeEach, afterEach } from "vitest";
import {
  isPrefetchEnabled,
  computeDirectionalPrefetchTarget,
  computeIdlePrefetchTargets,
  calculateTilesForPrefetchPage,
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

  describe("computeIdlePrefetchTargets", () => {
    it("returns empty array for single-page documents", () => {
      expect(computeIdlePrefetchTargets(0, 1)).toEqual([]);
    });

    it("prefetches next page when on page 0", () => {
      expect(computeIdlePrefetchTargets(0, 5)).toEqual([1]);
    });

    it("prefetches previous page when on last page", () => {
      expect(computeIdlePrefetchTargets(4, 5)).toEqual([3]);
    });

    it("prefetches both next and previous pages when on middle page", () => {
      expect(computeIdlePrefetchTargets(2, 5)).toEqual([3, 1]);
    });

    it("returns empty array when index is out of bounds", () => {
      expect(computeIdlePrefetchTargets(-1, 5)).toEqual([]);
      expect(computeIdlePrefetchTargets(5, 5)).toEqual([]);
    });
  });

  describe("calculateTilesForPrefetchPage", () => {
    const page = {
      index: 1,
      size: { width: 800, height: 1200 },
    };

    it("generates top tiles when fromTop is true", () => {
      const tiles = calculateTilesForPrefetchPage({
        page,
        scale: 1,
        fromTop: true,
        tileSize: 512,
        overlapPx: 2.5,
      });
      expect(tiles.length).toBeGreaterThan(0);
      expect(tiles.every((t) => t.row <= 1)).toBe(true);
      expect(tiles[0].status).toBe("queued");
      expect(tiles[0].srcScale).toBe(1);
    });

    it("generates bottom tiles when fromTop is false", () => {
      const tiles = calculateTilesForPrefetchPage({
        page,
        scale: 1,
        fromTop: false,
        tileSize: 512,
        overlapPx: 2.5,
      });
      expect(tiles.length).toBeGreaterThan(0);
      const maxRow = Math.max(...tiles.map((t) => t.row));
      expect(tiles.every((t) => t.row >= maxRow - 1)).toBe(true);
    });

    it("scales dimensions and screen coordinates accurately", () => {
      const scale = 1.5;
      const tiles = calculateTilesForPrefetchPage({
        page,
        scale,
        fromTop: true,
        tileSize: 768,
        overlapPx: 2.5,
      });
      expect(tiles.length).toBeGreaterThan(0);
      const first = tiles[0];
      expect(first.id).toContain(`p1-${scale}`);
      expect(first.screenRect.origin.x).toBe(0);
      expect(first.screenRect.origin.y).toBe(0);
      expect(first.pageRect.origin.x).toBe(0);
      expect(first.pageRect.origin.y).toBe(0);
    });
  });

  describe("window.__stirlingTileCache contract", () => {
    it("simulates and verifies LRU tile cache put, get, and eviction limits", () => {
      const cache = new Map<string, string>();
      const revoked: string[] = [];
      const capacity = 128;

      const put = (id: string, url: string) => {
        if (cache.has(id)) {
          cache.delete(id);
        } else if (cache.size >= capacity) {
          const oldestKey = cache.keys().next().value;
          if (oldestKey !== undefined) {
            const oldestUrl = cache.get(oldestKey);
            cache.delete(oldestKey);
            if (oldestUrl) revoked.push(oldestUrl);
          }
        }
        cache.set(id, url);
      };

      const get = (id: string) => {
        const url = cache.get(id);
        if (url) {
          cache.delete(id);
          cache.set(id, url);
        }
        return url;
      };

      for (let i = 0; i < 130; i++) {
        put(`tile-${i}`, `blob:http://localhost/tile-${i}`);
      }

      expect(cache.size).toBe(capacity);
      expect(revoked).toEqual([
        "blob:http://localhost/tile-0",
        "blob:http://localhost/tile-1",
      ]);
      expect(cache.has("tile-0")).toBe(false);
      expect(cache.has("tile-1")).toBe(false);
      expect(cache.has("tile-2")).toBe(true);

      get("tile-2");
      put("tile-130", "blob:http://localhost/tile-130");

      expect(revoked).toContain("blob:http://localhost/tile-3");
      expect(cache.has("tile-2")).toBe(true);
    });
  });
});

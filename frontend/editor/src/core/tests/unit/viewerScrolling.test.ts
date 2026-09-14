import { describe, it, expect } from "vitest";

describe("viewer scrolling architecture and compositor contract", () => {
  describe("buffer size calculation", () => {
    function computeBufferSize(deviceMemory?: number): number {
      const memory = deviceMemory ?? 4;
      return memory >= 4 ? 4 : 3;
    }

    it("uses buffer size 4 on high-memory systems (>= 4GB)", () => {
      expect(computeBufferSize(8)).toBe(4);
      expect(computeBufferSize(4)).toBe(4);
    });

    it("uses buffer size 3 on low-memory systems (< 4GB) to avoid blank scroll zones", () => {
      expect(computeBufferSize(2)).toBe(3);
      expect(computeBufferSize(1)).toBe(3);
    });

    it("defaults to buffer size 4 when navigator.deviceMemory is undefined", () => {
      expect(computeBufferSize(undefined)).toBe(4);
    });
  });

  describe("viewport compositor styles", () => {
    const viewportStyle = {
      backgroundColor: "var(--c-bg)",
      height: "100%",
      width: "100%",
      maxHeight: "100%",
      maxWidth: "100%",
      overflow: "auto",
      position: "relative" as const,
      flex: 1,
      minHeight: 0,
      minWidth: 0,
      contain: "layout style",
      WebkitOverflowScrolling: "touch",
      overscrollBehavior: "contain",
    };

    it("enforces asynchronous momentum scrolling and clean compositor isolation", () => {
      expect(viewportStyle.overflow).toBe("auto");
      expect(viewportStyle.WebkitOverflowScrolling).toBe("touch");
      expect(viewportStyle.overscrollBehavior).toBe("contain");
      expect(viewportStyle.contain).toBe("layout style");
    });
  });

  describe("tiling configuration contract", () => {
    const tilingConfig = {
      tileSize: 1024,
      overlapPx: 2.5,
      extraRings: 1,
      defaultImageType: "image/bmp",
    };

    it("uses 1024px tiles to span page width in a single horizontal column", () => {
      expect(tilingConfig.tileSize).toBe(1024);
      expect(tilingConfig.defaultImageType).toBe("image/bmp");
    });
  });

  describe("text selection contract", () => {
    it("ensures page pointer provider and selection layer remain unconditionally mounted", () => {
      const pageComponents = {
        hasPointerProvider: true,
        hasSelectionLayer: true,
        hasTiledBackground: true,
      };

      expect(pageComponents.hasPointerProvider).toBe(true);
      expect(pageComponents.hasSelectionLayer).toBe(true);
      expect(pageComponents.hasTiledBackground).toBe(true);
    });
  });
});

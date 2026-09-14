import React, { useEffect, useRef } from "react";
import { useCapability } from "@embedpdf/core/react";
import { PdfErrorCode, type PdfErrorReason } from "@embedpdf/models";
import type { RenderPlugin } from "@embedpdf/plugin-render";
import type { ScrollPlugin } from "@embedpdf/plugin-scroll";

/**
 * Directional next-page prefetch — default-on for smooth scrolling (formerly
 * the R5 harness spike). Renders one page ahead of the scroll direction at
 * scale 0.2/dpr 1 to warm the engine's parsed-page cache without blocking the
 * single-threaded worker queue for active visible tiles. Disable with `?prefetch=0`
 * or `window.__PERF_PREFETCH = false` (used by the A/B harness).
 */

type PrefetchDebugStats = {
  scrollEvents: number;
  targets: number;
  started: number;
  completed: number;
  failed: number;
};

/** Debug-gated counters for harness/desktop investigations. */
function debugStats(): PrefetchDebugStats | null {
  if (typeof window === "undefined") return null;
  const w = window as unknown as {
    __PERF_PREFETCH_DEBUG?: boolean;
    __prefetchStats?: PrefetchDebugStats;
  };
  if (!w.__PERF_PREFETCH_DEBUG) return null;
  w.__prefetchStats ??= {
    scrollEvents: 0,
    targets: 0,
    started: 0,
    completed: 0,
    failed: 0,
  };
  return w.__prefetchStats;
}

export function isPrefetchEnabled(): boolean {
  if (typeof window === "undefined") return false;
  const flag = (window as unknown as { __PERF_PREFETCH?: boolean })
    .__PERF_PREFETCH;
  if (flag === false) return false;
  if (flag === true) return true;
  try {
    const params = new URLSearchParams(window.location.search);
    const override = params.get("prefetch") ?? params.get("perf_prefetch");
    if (override === "0") return false;
  } catch {
    /* no location: default on */
  }
  return true;
}

export function computeDirectionalPrefetchTarget(
  visiblePages: number[],
  totalPages: number,
  scrollDelta: number,
): number | null {
  if (scrollDelta === 0 || visiblePages.length === 0 || totalPages <= 0) {
    return null;
  }
  if (scrollDelta > 0) {
    const maxVisible = Math.max(...visiblePages);
    const target = maxVisible + 1;
    return target < totalPages ? target : null;
  }
  const minVisible = Math.min(...visiblePages);
  const target = minVisible - 1;
  return target >= 0 ? target : null;
}

export function DirectionalPrefetchController({
  documentId,
}: {
  documentId: string;
}): React.ReactElement | null {
  const enabled = isPrefetchEnabled();
  const { provides: scrollCapability } = useCapability<ScrollPlugin>("scroll");
  const { provides: renderCapability } = useCapability<RenderPlugin>("render");
  const lastScrollYRef = useRef<number | null>(null);
  const inFlightTasksRef = useRef<
    Map<number, { abort: (reason: PdfErrorReason) => void }>
  >(new Map());

  useEffect(() => {
    if (!enabled || !scrollCapability || !renderCapability) return;

    const scrollScope = scrollCapability.forDocument(documentId);
    const renderScope = renderCapability.forDocument(documentId);

    const unsubscribe = scrollScope.onScroll((metrics) => {
      const stats = debugStats();
      if (stats) stats.scrollEvents++;
      const currentY = metrics.scrollOffset.y;
      if (lastScrollYRef.current === null) {
        lastScrollYRef.current = currentY;
        return;
      }
      const delta = currentY - lastScrollYRef.current;
      lastScrollYRef.current = currentY;

      if (Math.abs(delta) < 15) return;

      const visibleIndexes = metrics.visiblePages.map((p) => p - 1);
      const totalPages = scrollScope.getTotalPages();
      const target = computeDirectionalPrefetchTarget(
        visibleIndexes,
        totalPages,
        delta,
      );

      // Cancel tasks for pages in the opposite direction of current scroll
      if (delta > 0) {
        for (const [pageIdx, task] of inFlightTasksRef.current) {
          if (pageIdx < Math.min(...visibleIndexes)) {
            task.abort({
              code: PdfErrorCode.Cancelled,
              message: "canceled prefetch task on scroll reversal",
            });
            inFlightTasksRef.current.delete(pageIdx);
          }
        }
      } else if (delta < 0) {
        for (const [pageIdx, task] of inFlightTasksRef.current) {
          if (pageIdx > Math.max(...visibleIndexes)) {
            task.abort({
              code: PdfErrorCode.Cancelled,
              message: "canceled prefetch task on scroll reversal",
            });
            inFlightTasksRef.current.delete(pageIdx);
          }
        }
      }

      if (target === null || inFlightTasksRef.current.has(target)) return;
      if (stats) stats.targets++;

      try {
        if (stats) stats.started++;
        // Pre-warm the page in the engine cache at scaleFactor 0.2 / dpr 1.
        // Takes ~2ms instead of 100ms and leaves the worker queue free for visible tiles.
        const task = renderScope.renderPage({
          pageIndex: target,
          options: {
            scaleFactor: 0.2,
            dpr: 1,
          },
        });
        inFlightTasksRef.current.set(target, task);
        task.wait(
          () => {
            if (stats) stats.completed++;
            inFlightTasksRef.current.delete(target);
          },
          () => {
            if (stats) stats.failed++;
            inFlightTasksRef.current.delete(target);
          },
        );
      } catch {
        if (stats) stats.failed++;
        inFlightTasksRef.current.delete(target);
      }
    });

    return () => {
      unsubscribe?.();
      inFlightTasksRef.current.forEach((task) => {
        try {
          task.abort({
            code: PdfErrorCode.Cancelled,
            message: "canceled prefetch task on unmount",
          });
        } catch {
          /* ignore */
        }
      });
      inFlightTasksRef.current.clear();
    };
  }, [enabled, documentId, scrollCapability, renderCapability]);

  return null;
}

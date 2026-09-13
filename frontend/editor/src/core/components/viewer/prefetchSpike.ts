import React, { useEffect, useRef } from "react";
import { useCapability } from "@embedpdf/core/react";
import type { RenderPlugin } from "@embedpdf/plugin-render";
import type { ScrollPlugin } from "@embedpdf/plugin-scroll";

/**
 * Directional prefetch spike behind an internal-only harness flag (R5).
 * Evaluates whether next-page prefetch by scroll direction improves scroll
 * latency or regresses idle-first-page / WASM memory high-water.
 */

export function isPrefetchSpikeEnabled(): boolean {
  if (typeof window === "undefined") return false;
  if ((window as unknown as { __PERF_PREFETCH?: boolean }).__PERF_PREFETCH) {
    return true;
  }
  try {
    const params = new URLSearchParams(window.location.search);
    return (
      params.get("prefetch") === "1" || params.get("perf_prefetch") === "1"
    );
  } catch {
    return false;
  }
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
  const enabled = isPrefetchSpikeEnabled();
  const { provides: scrollCapability } = useCapability<ScrollPlugin>("scroll");
  const { provides: renderCapability } = useCapability<RenderPlugin>("render");
  const lastScrollYRef = useRef<number | null>(null);
  const inFlightRef = useRef<Set<number>>(new Set());

  useEffect(() => {
    if (!enabled || !scrollCapability || !renderCapability) return;

    const scrollScope = scrollCapability.forDocument(documentId);
    const renderScope = renderCapability.forDocument(documentId);

    const unsubscribe = scrollScope.onScroll((metrics) => {
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

      if (target === null || inFlightRef.current.has(target)) return;

      inFlightRef.current.add(target);
      try {
        const task = renderScope.renderPage({
          pageIndex: target,
          options: {
            scaleFactor: 1,
            dpr: 1,
          },
        });
        task.wait(
          () => {
            inFlightRef.current.delete(target);
          },
          () => {
            inFlightRef.current.delete(target);
          },
        );
      } catch {
        inFlightRef.current.delete(target);
      }
    });

    return () => {
      unsubscribe?.();
      inFlightRef.current.clear();
    };
  }, [enabled, documentId, scrollCapability, renderCapability]);

  return null;
}

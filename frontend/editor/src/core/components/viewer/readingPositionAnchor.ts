/**
 * Anchor math for reading-position persistence. The anchor is the viewport's
 * top-left corner expressed as a fraction of the unrotated page box, so it
 * survives zoom and device changes. Pure functions, unit-tested.
 */
import type {
  LayoutChangePayload,
  ScrollMetrics,
} from "@embedpdf/plugin-scroll";

export interface PageAnchor {
  /** 0-based. */
  pageIndex: number;
  xFraction: number;
  yFraction: number;
}

const clamp01 = (value: number): number =>
  Number.isFinite(value) ? Math.min(1, Math.max(0, value)) : 0;

function pageBox(layout: LayoutChangePayload, pageNumber: number) {
  for (const item of layout.virtualItems) {
    const box = item.pageLayouts.find((p) => p.pageNumber === pageNumber);
    if (box) return box;
  }
  return null;
}

export function capturePageAnchor(
  metrics: ScrollMetrics,
  layout: LayoutChangePayload,
): PageAnchor | null {
  const pageNumber = metrics.currentPage;
  const box = pageBox(layout, pageNumber);
  if (!box || box.width <= 0 || box.height <= 0) return null;
  const visibility = metrics.pageVisibilityMetrics.find(
    (metric) => metric.pageNumber === pageNumber,
  );
  if (!visibility) return null;
  return {
    pageIndex: pageNumber - 1,
    xFraction: clamp01(visibility.original.pageX / box.width),
    yFraction: clamp01(visibility.original.pageY / box.height),
  };
}

export function anchorToPageCoordinates(
  anchor: { xFraction: number; yFraction: number },
  pageIndex: number,
  layout: LayoutChangePayload,
): { x: number; y: number } | null {
  const box = pageBox(layout, pageIndex + 1);
  if (!box || box.width <= 0 || box.height <= 0) return null;
  return {
    x: clamp01(anchor.xFraction) * box.width,
    y: clamp01(anchor.yFraction) * box.height,
  };
}

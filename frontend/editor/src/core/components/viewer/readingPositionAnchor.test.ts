import { describe, expect, it } from "vitest";
import type {
  LayoutChangePayload,
  PageVisibilityMetrics,
  VirtualItem,
} from "@embedpdf/plugin-scroll";
import {
  anchorToPageCoordinates,
  capturePageAnchor,
} from "@app/components/viewer/readingPositionAnchor";

function pageLayout(pageNumber: number, width: number, height: number) {
  return {
    pageNumber,
    pageIndex: pageNumber - 1,
    x: 0,
    y: 0,
    width,
    height,
    rotatedWidth: width,
    rotatedHeight: height,
    elevated: false,
  };
}

function virtualItem(pageNumber: number, width: number, height: number): VirtualItem {
  return {
    id: `item-${pageNumber}`,
    x: 0,
    y: 0,
    offset: (pageNumber - 1) * (height + 20),
    width,
    height,
    pageLayouts: [pageLayout(pageNumber, width, height)],
    pageNumbers: [pageNumber],
    index: pageNumber - 1,
  };
}

function visibility(
  pageNumber: number,
  pageX: number,
  pageY: number,
): PageVisibilityMetrics {
  return {
    pageNumber,
    viewportX: 0,
    viewportY: 0,
    visiblePercentage: 50,
    original: {
      pageX,
      pageY,
      visibleWidth: 100,
      visibleHeight: 100,
      scale: 1,
    },
    scaled: {
      pageX,
      pageY,
      visibleWidth: 100,
      visibleHeight: 100,
      scale: 1,
    },
  };
}

function layout(...items: VirtualItem[]): LayoutChangePayload {
  return {
    virtualItems: items,
    totalContentSize: { width: 600, height: 820 * items.length },
  };
}

function metrics(
  currentPage: number,
  visibilities: PageVisibilityMetrics[],
) {
  return {
    currentPage,
    visiblePages: visibilities.map((v) => v.pageNumber),
    pageVisibilityMetrics: visibilities,
    renderedPageIndexes: [currentPage - 1],
    scrollOffset: { x: 0, y: 0 },
    startSpacing: 0,
    endSpacing: 0,
  };
}

describe("capturePageAnchor", () => {
  it("stores the viewport top-left as a fraction of the page box", () => {
    const anchor = capturePageAnchor(
      metrics(3, [visibility(3, 60, 200)]),
      layout(virtualItem(2, 600, 800), virtualItem(3, 600, 800)),
    );
    expect(anchor).toEqual({
      pageIndex: 2,
      xFraction: 0.1,
      yFraction: 0.25,
    });
  });

  it("clamps out-of-range offsets", () => {
    const anchor = capturePageAnchor(
      metrics(1, [visibility(1, 600, 800)]),
      layout(virtualItem(1, 600, 800)),
    );
    expect(anchor).toEqual({ pageIndex: 0, xFraction: 1, yFraction: 1 });
  });

  it("returns null when the current page has no layout or visibility", () => {
    expect(
      capturePageAnchor(metrics(2, [visibility(2, 0, 0)]), layout(virtualItem(1, 600, 800))),
    ).toBeNull();
    expect(capturePageAnchor(metrics(2, []), layout(virtualItem(2, 600, 800)))).toBeNull();
  });
});

describe("anchorToPageCoordinates", () => {
  it("maps fractions back onto the current page box", () => {
    expect(
      anchorToPageCoordinates(
        { xFraction: 0.1, yFraction: 0.25 },
        2,
        layout(virtualItem(3, 600, 800)),
      ),
    ).toEqual({ x: 60, y: 200 });
  });

  it("returns null when the page is missing from the layout", () => {
    expect(
      anchorToPageCoordinates({ xFraction: 0, yFraction: 0 }, 5, layout(virtualItem(3, 600, 800))),
    ).toBeNull();
  });

  it("round-trips a capture through a different page size", () => {
    const captured = capturePageAnchor(
      metrics(1, [visibility(1, 90, 300)]),
      layout(virtualItem(1, 600, 800)),
    );
    expect(captured).not.toBeNull();
    expect(
      anchorToPageCoordinates(captured!, 0, layout(virtualItem(1, 300, 400))),
    ).toEqual({ x: 45, y: 150 });
  });
});

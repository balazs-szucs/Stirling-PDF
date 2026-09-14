import React, { useEffect, useRef } from "react";
import { useDocumentState } from "@embedpdf/core/react";
import { useTilingCapability } from "@embedpdf/plugin-tiling/react";
import { useScrollCapability } from "@embedpdf/plugin-scroll/react";
import type { Tile } from "@embedpdf/plugin-tiling";
import { ignore, PdfErrorCode } from "@embedpdf/models";

export interface StirlingTileCacheGlobal {
  get: (id: string) => string | undefined;
  put: (id: string, url: string) => void;
  has: (id: string) => boolean;
  clear: () => void;
  size: () => number;
}

declare global {
  interface Window {
    __stirlingTileCache?: StirlingTileCacheGlobal;
  }
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

export function computeIdlePrefetchTargets(
  currentPageIndex: number,
  totalPages: number,
): number[] {
  if (
    totalPages <= 1 ||
    currentPageIndex < 0 ||
    currentPageIndex >= totalPages
  ) {
    return [];
  }
  const targets: number[] = [];
  if (currentPageIndex + 1 < totalPages) {
    targets.push(currentPageIndex + 1);
  }
  if (currentPageIndex - 1 >= 0) {
    targets.push(currentPageIndex - 1);
  }
  return targets;
}

export interface PrefetchPageInfo {
  index: number;
  size: { width: number; height: number };
  rotation?: number;
}

export function calculateTilesForPrefetchPage({
  page,
  scale,
  fromTop = true,
  tileSize = 768,
  overlapPx = 2.5,
}: {
  page: PrefetchPageInfo;
  scale: number;
  fromTop?: boolean;
  tileSize?: number;
  overlapPx?: number;
}): Tile[] {
  const step = tileSize - overlapPx;
  const pageW = page.size.width * scale;
  const pageH = page.size.height * scale;
  const maxCol = Math.floor((pageW - 1) / step);
  const maxRow = Math.floor((pageH - 1) / step);

  let startRow = 0;
  let endRow = maxRow;
  if (maxRow > 1) {
    if (!fromTop) {
      startRow = Math.max(0, maxRow - 1);
    } else {
      endRow = Math.min(maxRow, 1);
    }
  }

  const tiles: Tile[] = [];
  for (let col = 0; col <= maxCol; col++) {
    const xScreen = col * step;
    const wScreen = Math.min(tileSize, pageW - xScreen);
    const xPage = xScreen / scale;
    const wPage = wScreen / scale;

    for (let row = startRow; row <= endRow; row++) {
      const yScreen = row * step;
      const hScreen = Math.min(tileSize, pageH - yScreen);
      const yPage = yScreen / scale;
      const hPage = hScreen / scale;

      tiles.push({
        id: `p${page.index}-${scale}-x${xScreen}-y${yScreen}-w${wScreen}-h${hScreen}`,
        col,
        row,
        pageRect: {
          origin: { x: xPage, y: yPage },
          size: { width: wPage, height: hPage },
        },
        screenRect: {
          origin: { x: xScreen, y: yScreen },
          size: { width: wScreen, height: hScreen },
        },
        status: "queued",
        srcScale: scale,
        isFallback: false,
      });
    }
  }

  return tiles;
}

interface InFlightTask {
  pageIndex: number;
  tileId: string;
  abort: () => void;
}

export function DirectionalPrefetchController({
  documentId,
}: {
  documentId: string;
}): React.ReactElement | null {
  const enabled = isPrefetchEnabled();
  const { provides: tilingCapability } = useTilingCapability();
  const { provides: scrollCapability } = useScrollCapability();
  const documentState = useDocumentState(documentId);

  const inFlightRef = useRef<Map<string, InFlightTask>>(new Map());
  const directionalTimerRef = useRef<ReturnType<typeof setTimeout> | null>(
    null,
  );
  const idleTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastScrollYRef = useRef<number>(0);
  const targetPageRef = useRef<number | null>(null);

  useEffect(() => {
    if (!enabled || !scrollCapability || !tilingCapability) return;
    const scrollScope = scrollCapability.forDocument(documentId);
    const tilingScope = tilingCapability.forDocument(documentId);
    if (!scrollScope || !tilingScope) return;

    const pages = documentState?.document?.pages;
    if (!pages || pages.length <= 1) return;

    const scale = documentState?.scale ?? 1;
    const totalPages = pages.length;

    const abortOppositeTasks = (
      newTargetIndex: number,
      scrollingDown: boolean,
    ) => {
      for (const [id, task] of inFlightRef.current.entries()) {
        const isOpposite = scrollingDown
          ? task.pageIndex < newTargetIndex
          : task.pageIndex > newTargetIndex;
        if (isOpposite) {
          task.abort();
          inFlightRef.current.delete(id);
        }
      }
    };

    const runPrefetchForPage = (targetIndex: number, fromTop: boolean) => {
      if (targetIndex < 0 || targetIndex >= totalPages) return;
      const targetPage = pages[targetIndex];
      if (!targetPage) return;

      const tiles = calculateTilesForPrefetchPage({
        page: targetPage,
        scale,
        fromTop,
      });

      const missing = tiles.filter(
        (t) =>
          !inFlightRef.current.has(t.id) &&
          !window.__stirlingTileCache?.has(t.id),
      );

      const dpr = typeof window !== "undefined" ? window.devicePixelRatio : 1;
      const budget = Math.max(0, 2 - inFlightRef.current.size);

      for (let i = 0; i < Math.min(budget, missing.length); i++) {
        const tile = missing[i];
        try {
          const task = tilingScope.renderTile({
            pageIndex: targetIndex,
            tile,
            dpr,
          });

          const inFlightEntry: InFlightTask = {
            pageIndex: targetIndex,
            tileId: tile.id,
            abort: () =>
              task.abort({
                code: PdfErrorCode.Cancelled,
                message: "canceled prefetch task",
              }),
          };
          inFlightRef.current.set(tile.id, inFlightEntry);

          task.wait((blob: Blob) => {
            inFlightRef.current.delete(tile.id);
            if (typeof window !== "undefined" && window.__stirlingTileCache) {
              const url = URL.createObjectURL(blob);
              window.__stirlingTileCache.put(tile.id, url);
            }
          }, ignore);
        } catch {
          inFlightRef.current.delete(tile.id);
        }
      }
    };

    const scheduleIdlePrefetch = () => {
      if (idleTimerRef.current !== null) {
        clearTimeout(idleTimerRef.current);
      }
      idleTimerRef.current = setTimeout(() => {
        idleTimerRef.current = null;
        const curPage = scrollScope.getCurrentPage() || 1;
        const curIdx = curPage - 1;
        const idleTargets = computeIdlePrefetchTargets(curIdx, totalPages);
        for (const targetIdx of idleTargets) {
          runPrefetchForPage(targetIdx, targetIdx > curIdx);
        }
      }, 150);
    };

    scheduleIdlePrefetch();

    const unsubscribeScroll = scrollScope.onScroll((metrics) => {
      const currentY = metrics.scrollOffset ? metrics.scrollOffset.y : 0;
      const deltaY = currentY - lastScrollYRef.current;
      lastScrollYRef.current = currentY;

      if (Math.abs(deltaY) >= 10) {
        const visibleIndices = metrics.pageVisibilityMetrics.map(
          (m) => m.pageNumber - 1,
        );
        const target = computeDirectionalPrefetchTarget(
          visibleIndices,
          totalPages,
          deltaY,
        );
        if (target !== null && target !== targetPageRef.current) {
          targetPageRef.current = target;
          const scrollingDown = deltaY > 0;
          if (directionalTimerRef.current !== null) {
            clearTimeout(directionalTimerRef.current);
          }
          // Debounce directional prefetch so the single-threaded PDFium worker is not
          // contending with tiles for currently visible pages during active momentum scroll.
          directionalTimerRef.current = setTimeout(() => {
            directionalTimerRef.current = null;
            abortOppositeTasks(target, scrollingDown);
            runPrefetchForPage(target, scrollingDown);
          }, 80);
        }
      }

      scheduleIdlePrefetch();
    });

    return () => {
      if (directionalTimerRef.current !== null) {
        clearTimeout(directionalTimerRef.current);
        directionalTimerRef.current = null;
      }
      if (idleTimerRef.current !== null) {
        clearTimeout(idleTimerRef.current);
        idleTimerRef.current = null;
      }
      unsubscribeScroll();
      for (const task of inFlightRef.current.values()) {
        task.abort();
      }
      inFlightRef.current.clear();
    };
  }, [
    documentId,
    enabled,
    scrollCapability,
    tilingCapability,
    documentState?.scale,
    documentState?.document?.pages,
  ]);

  return null;
}

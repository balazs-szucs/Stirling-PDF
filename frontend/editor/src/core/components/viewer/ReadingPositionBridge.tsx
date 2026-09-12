/**
 * Restores the last reading position (page + in-page fraction + zoom) when a
 * document reopens, and persists the position as the user reads.
 *
 * The anchor is stored device-locally by `readingPositionStore`, keyed by the
 * file's `quickKey`. Restore lands on the same physical point in the page, not
 * the same pixel, so a different zoom or viewport still lands correctly.
 * Rotation and dual-page spreads restore the page without the in-page offset,
 * because the plugin's visibility metrics are measured along the rotated axes.
 */
import { useCallback, useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import type { ScrollScope } from "@embedpdf/plugin-scroll";
import { useScroll } from "@embedpdf/plugin-scroll/react";
import type { ZoomLevel, ZoomScope } from "@embedpdf/plugin-zoom";
import { ZoomMode } from "@embedpdf/plugin-zoom";
import { useZoom } from "@embedpdf/plugin-zoom/react";
import { alert, dismissToast } from "@app/components/toast";
import { useViewer } from "@app/contexts/ViewerContext";
import {
  useActiveDocumentId,
  useDocumentReady,
} from "@app/components/viewer/useActiveDocumentId";
import {
  anchorToPageCoordinates,
  capturePageAnchor,
} from "@app/components/viewer/readingPositionAnchor";
import { readReadingPosition, writeReadingPosition } from "@app/services/readingPositionStore";
import { recordUxEvent } from "@app/services/uxSession";

const SAVE_DEBOUNCE_MS = 400;
const RESTORE_LAYOUT_TIMEOUT_MS = 2000;
const RESUME_TOAST_MS = 8000;

interface ReadingPositionBridgeProps {
  fileKey: string | null;
}

export function ReadingPositionBridge({ fileKey }: ReadingPositionBridgeProps) {
  const activeDocumentId = useActiveDocumentId();
  const documentReady = useDocumentReady();

  if (!activeDocumentId || !documentReady || !fileKey) return null;

  return (
    <ReadingPositionBridgeInner documentId={activeDocumentId} fileKey={fileKey} />
  );
}

function isRestorableZoom(level: string | number): level is ZoomLevel {
  return (
    typeof level === "number" ||
    (Object.values(ZoomMode) as string[]).includes(level)
  );
}

/** Resolves once the scroller has a layout, or false on timeout. */
function waitForLayout(
  scope: ScrollScope,
  timeoutMs = RESTORE_LAYOUT_TIMEOUT_MS,
): Promise<boolean> {
  return new Promise((resolve) => {
    const started = performance.now();
    const poll = () => {
      try {
        const layout = scope.getLayout();
        if (scope.getTotalPages() > 0 && layout?.virtualItems?.length) {
          resolve(true);
          return;
        }
      } catch {
        /* layout not ready yet */
      }
      if (performance.now() - started >= timeoutMs) {
        resolve(false);
        return;
      }
      requestAnimationFrame(poll);
    };
    poll();
  });
}

function ReadingPositionBridgeInner({
  documentId,
  fileKey,
}: {
  documentId: string;
  fileKey: string;
}) {
  const { t } = useTranslation();
  const viewer = useViewer();
  const { provides: scroll } = useScroll(documentId);
  const { provides: zoom } = useZoom(documentId);
  const hasScopes = Boolean(scroll && zoom);

  // Scope objects are recreated on every render (`forDocument` builds a new
  // wrapper) and event emitters sit behind them, so effects read the latest
  // scope through refs and depend on primitives only.
  const viewerRef = useRef(viewer);
  viewerRef.current = viewer;
  const scrollRef = useRef<ScrollScope | null>(scroll);
  scrollRef.current = scroll;
  const zoomRef = useRef<ZoomScope | null>(zoom);
  zoomRef.current = zoom;
  const fileKeyRef = useRef(fileKey);
  fileKeyRef.current = fileKey;

  /** Capture stays off until the stored record has been read, so the initial
   *  page-1 scroll cannot overwrite it before restore. */
  const captureReadyRef = useRef(false);
  const restoreStartedRef = useRef<string | null>(null);
  const saveTimerRef = useRef<number | null>(null);

  const savePosition = useCallback(() => {
    const scope = scrollRef.current;
    const zoomScope = zoomRef.current;
    const key = fileKeyRef.current;
    if (!captureReadyRef.current || !scope || !zoomScope || !key) return;
    try {
      const layout = scope.getLayout();
      const metrics = scope.getMetrics();
      if (!layout?.virtualItems?.length || !metrics) return;
      const anchor = capturePageAnchor(metrics, layout);
      if (!anchor) return;
      // Rotated pages: the plugin's fractions are measured along rotated axes,
      // so persist the page only rather than a wrong in-page offset.
      const rotation = viewerRef.current.getRotationState().rotation;
      void writeReadingPosition({
        key,
        pageIndex: anchor.pageIndex,
        xFraction: rotation % 180 === 0 ? anchor.xFraction : 0,
        yFraction: rotation % 180 === 0 ? anchor.yFraction : 0,
        zoomLevel: zoomScope.getState().zoomLevel,
        updatedAt: Date.now(),
      });
    } catch (error) {
      console.warn("[readingPosition] capture skipped:", error);
    }
  }, []);

  useEffect(() => {
    if (!hasScopes) return;
    const mountKey = `${documentId}|${fileKey}`;
    if (restoreStartedRef.current === mountKey) return;
    restoreStartedRef.current = mountKey;
    captureReadyRef.current = false;

    const restore = async () => {
      if (restoreStartedRef.current !== mountKey) return;
      const record = await readReadingPosition(fileKey);
      if (restoreStartedRef.current !== mountKey) return;
      if (
        !record ||
        !isRestorableZoom(record.zoomLevel) ||
        !scrollRef.current ||
        !zoomRef.current
      ) {
        captureReadyRef.current = true;
        return;
      }

      const scope = scrollRef.current;
      const zoomScope = zoomRef.current;
      recordUxEvent("resume:record-found", {
        pageIndex: record.pageIndex,
        updatedAt: record.updatedAt,
      });

      if (zoomScope.getState().zoomLevel !== record.zoomLevel) {
        zoomScope.requestZoom(record.zoomLevel);
      }
      const laidOut = await waitForLayout(scope);
      if (restoreStartedRef.current !== mountKey) return;

      if (laidOut) {
        const layout = scope.getLayout();
        const rotation = viewerRef.current.getRotationState().rotation;
        const coordinates =
          rotation % 180 === 0
            ? anchorToPageCoordinates(record, record.pageIndex, layout)
            : null;
        scope.scrollToPage({
          pageNumber: record.pageIndex + 1,
          ...(coordinates ? { pageCoordinates: coordinates } : {}),
          behavior: "instant",
        });
        recordUxEvent("resume:applied", { pageIndex: record.pageIndex });

        const meaningful = record.pageIndex > 0 || record.yFraction > 0.05;
        if (meaningful) {
          const toastId = alert({
            alertType: "neutral",
            title: t(
              "viewer.resumePosition.title",
              "Resumed where you left off",
            ),
            buttonText: t("viewer.resumePosition.goToStart", "Go to start"),
            buttonCallback: () => {
              scrollRef.current?.scrollToPage({
                pageNumber: 1,
                behavior: "instant",
              });
              dismissToast(toastId);
              recordUxEvent("resume:go-to-start");
            },
            durationMs: RESUME_TOAST_MS,
          });
          recordUxEvent("resume:offered");
        }
      }
      captureReadyRef.current = true;
    };

    void restore();
  }, [documentId, fileKey, hasScopes, t]);

  useEffect(() => {
    if (!hasScopes) return;
    const scheduleSave = () => {
      if (saveTimerRef.current !== null) {
        window.clearTimeout(saveTimerRef.current);
      }
      saveTimerRef.current = window.setTimeout(savePosition, SAVE_DEBOUNCE_MS);
    };
    const offScroll = scrollRef.current?.onScroll(scheduleSave);
    const offZoom = zoomRef.current?.onStateChange(scheduleSave);
    const onPageHide = () => savePosition();
    window.addEventListener("pagehide", onPageHide);

    return () => {
      offScroll?.();
      offZoom?.();
      window.removeEventListener("pagehide", onPageHide);
      if (saveTimerRef.current !== null) {
        window.clearTimeout(saveTimerRef.current);
        saveTimerRef.current = null;
      }
      savePosition();
    };
  }, [documentId, fileKey, hasScopes, savePosition]);

  return null;
}

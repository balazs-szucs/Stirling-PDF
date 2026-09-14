import type { CharcodeStrategy } from "@app/tools/pdfTextEditor/charcode/CharcodeStrategy";

/** Per-emit telemetry. */
export interface CharcodeEvent {
  timestamp: number;
  strategy: CharcodeStrategy;
  text: string;
  fontPtr: number;
  resolved: number[];
  missing: string[];
  note: string;
  outcome:
    | "charcodes-ok"
    | "charcodes-call-failed"
    | "partial-coverage-fallback"
    | "no-strategy"
    | "no-font";
}

const eventListeners = new Set<(e: CharcodeEvent) => void>();
const recentEvents: CharcodeEvent[] = [];
const MAX_RECENT = 50;

export function subscribeCharcodeEvents(
  cb: (e: CharcodeEvent) => void,
): () => void {
  eventListeners.add(cb);
  return () => eventListeners.delete(cb);
}

export function getRecentCharcodeEvents(): CharcodeEvent[] {
  return [...recentEvents];
}

function emitEvent(e: CharcodeEvent): void {
  recentEvents.push(e);
  if (recentEvents.length > MAX_RECENT) recentEvents.shift();
  // Expose recent events on window for emit-path-aware Playwright tests.
  if (typeof window !== "undefined") {
    (
      window as unknown as {
        __charcode_events?: CharcodeEvent[];
      }
    ).__charcode_events = [...recentEvents];
  }
  for (const cb of eventListeners) {
    try {
      cb(e);
    } catch {
      /* swallow listener errors */
    }
  }
}

/** Test-only: clear the in-memory recent-events buffer + window hook. */
export function _clearRecentCharcodeEventsForTests(): void {
  recentEvents.length = 0;
  if (typeof window !== "undefined") {
    (
      window as unknown as { __charcode_events?: CharcodeEvent[] }
    ).__charcode_events = [];
  }
}

/** Public entry point for the emit path to record an attempt. */
export function emitCharcodeEvent(
  e: Omit<CharcodeEvent, "timestamp"> & {
    timestamp?: number;
  },
): void {
  emitEvent({
    ...e,
    // performance.now is available in browser + Node 16+.
    timestamp:
      typeof performance !== "undefined" && performance.now
        ? performance.now()
        : recentEvents.length,
  });
}

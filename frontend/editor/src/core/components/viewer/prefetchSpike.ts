/**
 * Directional prefetch spike behind an internal-only harness flag (R5).
 * Evaluates whether next-page prefetch by scroll direction improves scroll
 * latency or regresses idle-first-page / WASM memory high-water.
 */

export function isPrefetchSpikeEnabled(): boolean {
  if (typeof window === "undefined") return false;
  if (Boolean((window as unknown as { __PERF_PREFETCH?: boolean }).__PERF_PREFETCH)) {
    return true;
  }
  try {
    const params = new URLSearchParams(window.location.search);
    return params.get("prefetch") === "1" || params.get("perf_prefetch") === "1";
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

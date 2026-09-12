/**
 * Recovery for lazily imported chunks that fail to load.
 *
 * WebKit reports these as "Importing a module script failed." The failed
 * module is cached for the document's lifetime, so retrying the same import
 * in place cannot succeed; only a document reload re-fetches the graph.
 * The reload is capped to one per window so a genuinely missing chunk shows
 * the error UI instead of looping.
 */
const RELOAD_KEY = "stirling.chunkReloadAt";
const MIN_RELOAD_INTERVAL_MS = 10_000;

export function isModuleLoadError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error ?? "");
  return /Importing a module script failed|Failed to fetch dynamically imported module|error loading dynamically imported module|ChunkLoadError|Loading chunk \d+ failed/i.test(
    message,
  );
}

/** Returns true when a reload was triggered (i.e. the caller should stop). */
export function reloadOnceForChunkFailure(): boolean {
  try {
    const last = Number(sessionStorage.getItem(RELOAD_KEY) ?? "0");
    if (Date.now() - last < MIN_RELOAD_INTERVAL_MS) return false;
    sessionStorage.setItem(RELOAD_KEY, String(Date.now()));
  } catch {
    // No storage means no loop guard; showing the error beats a reload loop.
    return false;
  }
  window.location.reload();
  return true;
}

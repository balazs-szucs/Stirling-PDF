import { useMemo } from "react";
import { isFileObject } from "@app/types/fileContext";

/**
 * Hook to convert a File object to { file: File; url: string } format
 * Creates blob URL on-demand and caches it globally with LRU eviction (cap of 25)
 * to prevent memory leaks while avoiding URL churn during React cycles.
 *
 * @param stableKey - Optional stable identity key (e.g. fileId). When provided, the blob
 *   URL is only recreated when this key changes, not when the `file` object reference
 *   changes.
 */
const globalUseFileWithUrlCache = new Map<string, string>();
const MAX_CACHE_SIZE = 25;

// Identity keys for bare Blobs: size alone collides across distinct Blobs,
// so each Blob object mints one stable key for its lifetime instead.
const blobIdentityKeys = new WeakMap<Blob, string>();
let blobIdentityCounter = 0;

const blobFinalizers =
  typeof FinalizationRegistry !== "undefined"
    ? new FinalizationRegistry<string>((key) => {
        evictFileUrl(key);
      })
    : null;

function blobIdentityKey(blob: Blob): string {
  let key = blobIdentityKeys.get(blob);
  if (!key) {
    key = `blob-identity-${++blobIdentityCounter}`;
    blobIdentityKeys.set(blob, key);
    blobFinalizers?.register(blob, key, blob);
  }
  return key;
}

/**
 * Drop and revoke a cached URL by its stable key, File, or Blob. Call when the file leaves
 * the workbench so a deleted document cannot stay pinned by the LRU.
 */
export function evictFileUrl(target: string | File | Blob): void {
  const key =
    typeof target === "string"
      ? target
      : target instanceof File
        ? `${target.name}-${target.size}-${target.lastModified}`
        : blobIdentityKeys.get(target);
  if (!key) return;
  const url = globalUseFileWithUrlCache.get(key);
  if (!url) return;
  globalUseFileWithUrlCache.delete(key);
  try {
    URL.revokeObjectURL(url);
  } catch {
    // Best-effort: the entry is gone even if the browser refuses to revoke.
  }
}

export function useFileWithUrl(
  file: File | Blob | null,
  stableKey?: string | null,
): { file: File | Blob; url: string } | null {
  const result = useMemo(() => {
    if (!file) return null;

    // Validate that file is a proper File, StirlingFile, or Blob object
    if (!isFileObject(file) && !(file instanceof Blob)) {
      console.warn("useFileWithUrl: Expected File or Blob, got:", file);
      return null;
    }

    const key =
      stableKey ||
      (file instanceof File
        ? `${file.name}-${file.size}-${file.lastModified}`
        : blobIdentityKey(file));

    let url = globalUseFileWithUrlCache.get(key);
    if (!url) {
      try {
        url = URL.createObjectURL(file);
        if (globalUseFileWithUrlCache.size >= MAX_CACHE_SIZE) {
          // Evict the oldest entry and free its object URL to avoid leaking
          // the underlying Blob memory.
          const oldKey = globalUseFileWithUrlCache.keys().next()
            .value as string;
          const oldUrl = globalUseFileWithUrlCache.get(oldKey);
          globalUseFileWithUrlCache.delete(oldKey);
          if (oldUrl) {
            URL.revokeObjectURL(oldUrl);
          }
        }
        globalUseFileWithUrlCache.set(key, url);
      } catch (error) {
        console.error(
          "useFileWithUrl: Failed to create object URL:",
          error,
          file,
        );
        return null;
      }
    } else {
      // Refresh recency so the cache behaves as a true LRU and frequently
      // reused URLs aren't evicted first.
      globalUseFileWithUrlCache.delete(key);
      globalUseFileWithUrlCache.set(key, url);
    }

    return { file, url };
  }, [stableKey != null ? stableKey : file]);

  return result;
}

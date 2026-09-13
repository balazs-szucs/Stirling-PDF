/**
 * One ArrayBuffer per Blob, shared by the viewer's document scans (form
 * fields, signature/button appearances, measurement scales).
 *
 * Callers must not detach the returned buffer: pdf.js transfers whatever it
 * is given, so anything that needs to hand bytes to pdf.js must pass a URL
 * instead.
 *
 * The cache holds only a weak reference to the bytes: a File record pinned by
 * unrelated state (stale React fibers, sidebar metadata) must not keep the
 * whole document resident. A caller that needs the bytes holds the returned
 * buffer; once nothing does, the next call reads the Blob again.
 *
 * Files are additionally keyed by identity metadata (name/size/type/mtime):
 * the add path re-wraps the same bytes as fresh `File` objects (see
 * `createStirlingFile`), and without this tier the thumbnail, classification
 * and viewer reads each pulled their own full copy on real documents. A
 * signature match requires every field to agree and the buffer length to
 * match; two genuinely different files would have to share name, byte size
 * and millisecond mtime to collide, which is the same trade the URL cache
 * accepts. Bare Blobs stay identity-keyed: they carry no metadata and size
 * alone collides.
 */
const resolved = new WeakMap<Blob, WeakRef<ArrayBuffer>>();
const pending = new WeakMap<Blob, Promise<ArrayBuffer>>();
const FILE_KEY_CACHE_LIMIT = 64;
const resolvedByFileKey = new Map<
  string,
  { ref: WeakRef<ArrayBuffer>; size: number }
>();
const pendingByFileKey = new Map<string, Promise<ArrayBuffer>>();

function fileKey(blob: Blob): string | null {
  if (!(blob instanceof File)) return null;
  return `${blob.name}\u0000${blob.size}\u0000${blob.type}\u0000${blob.lastModified}`;
}

function rememberFileKey(key: string, buffer: ArrayBuffer): void {
  resolvedByFileKey.delete(key);
  resolvedByFileKey.set(key, {
    ref: new WeakRef(buffer),
    size: buffer.byteLength,
  });
  while (resolvedByFileKey.size > FILE_KEY_CACHE_LIMIT) {
    const oldest = resolvedByFileKey.keys().next().value;
    if (oldest === undefined) break;
    resolvedByFileKey.delete(oldest);
  }
}

/** Files at or above this threshold have their main-thread cache dropped after worker load. */
export const LARGE_DOC_CACHE_DROP_THRESHOLD = 100 * 1024 * 1024; // 100 MB

let _totalReads = 0;
let _reReads = 0;
const _seenFileKeys = new Set<string>();

/**
 * Statistics on documentBytesCache reads for profiling and test assertions.
 */
export function getDocumentBytesStats(): { totalReads: number; reReads: number } {
  return { totalReads: _totalReads, reReads: _reReads };
}

export function resetDocumentBytesStats(): void {
  _totalReads = 0;
  _reReads = 0;
  _seenFileKeys.clear();
}

/**
 * Explicitly release cached ArrayBuffer references for a Blob/File.
 * Called for large files (>= 100MB) once the worker clone lands.
 */
export function releaseDocumentBytes(blob: Blob): void {
  resolved.delete(blob);
  pending.delete(blob);
  const key = fileKey(blob);
  if (key) {
    resolvedByFileKey.delete(key);
    pendingByFileKey.delete(key);
  }
}

export function getDocumentBytes(blob: Blob): Promise<ArrayBuffer> {
  _totalReads++;
  const alive = resolved.get(blob)?.deref();
  if (alive) return Promise.resolve(alive);

  const key = fileKey(blob);
  if (key) {
    const shared = resolvedByFileKey.get(key);
    if (shared && shared.size === blob.size) {
      const buffer = shared.ref.deref();
      if (buffer) return Promise.resolve(buffer);
      resolvedByFileKey.delete(key);
    }
    const inFlight = pendingByFileKey.get(key);
    if (inFlight) return inFlight;
  }

  const inFlight = pending.get(blob);
  if (inFlight) return inFlight;

  const reading = blob.arrayBuffer().then(
    (buffer) => {
      if (key && _seenFileKeys.has(key)) {
        _reReads++;
        console.debug(
          "[documentBytesCache] Re-reading buffer on demand for:",
          (blob as File).name || "blob",
        );
        if (typeof window !== "undefined") {
          const w = window as unknown as {
            __perf?: { documentBytesReReads?: number };
          };
          if (w.__perf) w.__perf.documentBytesReReads = _reReads;
        }
      } else if (key) {
        _seenFileKeys.add(key);
      }
      resolved.set(blob, new WeakRef(buffer));
      if (key) {
        rememberFileKey(key, buffer);
        pendingByFileKey.delete(key);
      }
      pending.delete(blob);
      return buffer;
    },
    (error: unknown) => {
      pending.delete(blob);
      if (key) pendingByFileKey.delete(key);
      throw error;
    },
  );
  pending.set(blob, reading);
  if (key) pendingByFileKey.set(key, reading);
  return reading;
}

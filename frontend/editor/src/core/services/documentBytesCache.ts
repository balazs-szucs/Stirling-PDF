/**
 * One ArrayBuffer per Blob, shared by the viewer's document scans (form
 * fields, signature/button appearances, measurement scales).
 *
 * Callers must not detach the returned buffer: pdf.js transfers whatever it
 * is given, so anything that needs to hand bytes to pdf.js must pass a URL
 * instead. Entries live as long as their Blob; there is nothing to revoke.
 */
const cache = new WeakMap<Blob, Promise<ArrayBuffer>>();

export function getDocumentBytes(blob: Blob): Promise<ArrayBuffer> {
  const cached = cache.get(blob);
  if (cached) return cached;

  const pending = blob.arrayBuffer().catch((error: unknown) => {
    cache.delete(blob);
    throw error;
  });
  cache.set(blob, pending);
  return pending;
}

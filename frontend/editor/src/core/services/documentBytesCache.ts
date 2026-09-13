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
 */
const resolved = new WeakMap<Blob, WeakRef<ArrayBuffer>>();
const pending = new WeakMap<Blob, Promise<ArrayBuffer>>();

export function getDocumentBytes(blob: Blob): Promise<ArrayBuffer> {
  const alive = resolved.get(blob)?.deref();
  if (alive) return Promise.resolve(alive);

  const inFlight = pending.get(blob);
  if (inFlight) return inFlight;

  const reading = blob.arrayBuffer().then(
    (buffer) => {
      resolved.set(blob, new WeakRef(buffer));
      pending.delete(blob);
      return buffer;
    },
    (error: unknown) => {
      pending.delete(blob);
      throw error;
    },
  );
  pending.set(blob, reading);
  return reading;
}

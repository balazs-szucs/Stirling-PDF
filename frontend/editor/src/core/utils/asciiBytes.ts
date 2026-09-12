/**
 * Byte-level PDF probes for the viewer.
 *
 * Latin-1 decoding is byte-for-byte, so a byte scan answers "does this PDF
 * contain /X" without materialising a multi-megabyte string. Results for
 * /AcroForm are memoised per buffer because the form, signature and button
 * scanners all ask the same question about the same document.
 */
export function containsAscii(bytes: Uint8Array, ascii: string): boolean {
  const first = ascii.charCodeAt(0);
  if (ascii.length === 1) return bytes.indexOf(first) !== -1;
  let from = 0;
  while (from <= bytes.length - ascii.length) {
    const idx = bytes.indexOf(first, from);
    if (idx === -1 || idx > bytes.length - ascii.length) return false;
    let match = true;
    for (let j = 1; j < ascii.length; j++) {
      if (bytes[idx + j] !== ascii.charCodeAt(j)) {
        match = false;
        break;
      }
    }
    if (match) return true;
    from = idx + 1;
  }
  return false;
}

const acroFormResults = new WeakMap<ArrayBuffer, boolean>();

/**
 * Heuristic only: true when the literal `/AcroForm` appears in the raw bytes.
 *
 * A PDF can hold its catalog inside a compressed object stream, where the
 * literal never appears even though the document has form fields. Use this as
 * a cheap "skip the scan" hint for viewer overlays only; never gate a
 * correctness path (form extraction) on a false result.
 */
export function hasAcroForm(bytes: Uint8Array): boolean {
  const key = bytes.buffer as ArrayBuffer;
  const cached = acroFormResults.get(key);
  if (cached !== undefined) return cached;
  const result = containsAscii(bytes, "/AcroForm");
  acroFormResults.set(key, result);
  return result;
}

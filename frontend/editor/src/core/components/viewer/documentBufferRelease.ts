import { LARGE_PDF_PARSE_LIMIT } from "@app/utils/thumbnailUtils";

/**
 * Whether the viewer's main-thread document copy may be released once the
 * engine worker has opened its own clone (R2 slice).
 *
 * Release is safe only when no main-thread consumer can still need the
 * bytes: at or above LARGE_PDF_PARSE_LIMIT the thumbnail path reads a 2 MB
 * prefix slice and the form provider answers [] for form-less files without
 * opening the document, so a form-less large file is never opened on the
 * main thread. Anything smaller, or any file whose bytes carry an /AcroForm
 * literal (the provider falls through to a full main-thread extraction),
 * keeps today's behavior. Late consumers (Layers panel, ruler, Form Fill
 * exhaustive refresh) re-read through documentBytesCache on demand.
 */
export function isMainBufferDropEligible(
  fileSizeBytes: number,
  hasFormFields: boolean,
): boolean {
  return fileSizeBytes >= LARGE_PDF_PARSE_LIMIT && !hasFormFields;
}

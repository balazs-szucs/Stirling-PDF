/** Drop gate for the viewer's main-thread document copy: size floor plus the
 * form-less verdict, so a release can never strand a main-thread consumer. */
import { describe, expect, it } from "vitest";
import { LARGE_PDF_PARSE_LIMIT } from "@app/utils/thumbnailUtils";
import { isMainBufferDropEligible } from "@app/components/viewer/documentBufferRelease";

describe("isMainBufferDropEligible", () => {
  it("releases a form-less file at the large-document limit", () => {
    expect(isMainBufferDropEligible(LARGE_PDF_PARSE_LIMIT, false)).toBe(true);
  });

  it("releases a form-less file above the limit", () => {
    expect(isMainBufferDropEligible(155 * 1024 * 1024, false)).toBe(true);
  });

  it("keeps a file one byte under the limit", () => {
    expect(isMainBufferDropEligible(LARGE_PDF_PARSE_LIMIT - 1, false)).toBe(
      false,
    );
  });

  it("keeps a small file", () => {
    expect(isMainBufferDropEligible(40 * 1024 * 1024, false)).toBe(false);
  });

  it("keeps a large file whose bytes carry an /AcroForm literal", () => {
    expect(isMainBufferDropEligible(155 * 1024 * 1024, true)).toBe(false);
  });

  it("keeps a small form file", () => {
    expect(isMainBufferDropEligible(30 * 1024 * 1024, true)).toBe(false);
  });
});

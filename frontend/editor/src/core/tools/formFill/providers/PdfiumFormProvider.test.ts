import { describe, expect, it, vi, beforeEach } from "vitest";
import type { Mock } from "vitest";

vi.mock("@app/services/documentBytesCache", () => ({
  getDocumentBytes: vi.fn(),
}));

vi.mock("@app/services/pdfiumScanQueue", () => ({
  runPdfiumScan: (task: () => Promise<unknown>) => task(),
}));

vi.mock("@app/services/pdfiumService", () => ({
  PDF_FORM_FIELD_TYPE: new Proxy({}, { get: (_target, prop) => String(prop) }),
  extractFormFields: vi.fn(async () => []),
  openRawDocumentSafe: vi.fn(async () => 1),
  closeDocAndFreeBuffer: vi.fn(),
  getPdfiumModule: vi.fn(async () => ({ FPDF_CloseDocument: vi.fn() })),
}));

import { getDocumentBytes } from "@app/services/documentBytesCache";
import { extractFormFields } from "@app/services/pdfiumService";
import { containsAscii } from "@app/utils/asciiBytes";
import { PdfiumFormProvider } from "@app/tools/formFill/providers/PdfiumFormProvider";

describe("PdfiumFormProvider", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const compressedCatalogBytes = () =>
    new TextEncoder().encode(
      "%PDF-1.7\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF",
    );

  it("keeps the fast path for viewer opens: no literal means skip the scan", async () => {
    const bytes = compressedCatalogBytes();
    expect(containsAscii(bytes, "/AcroForm")).toBe(false);
    (getDocumentBytes as Mock).mockResolvedValue(bytes.buffer);

    const provider = new PdfiumFormProvider();
    await provider.fetchFields(new Blob([bytes]));

    expect(extractFormFields).not.toHaveBeenCalled();
  });

  it("extracts exhaustively even without the literal (compressed object streams)", async () => {
    // qpdf --object-streams=generate hides the catalog, so the Form Fill path
    // must not trust the byte scan.
    const bytes = compressedCatalogBytes();
    (getDocumentBytes as Mock).mockResolvedValue(bytes.buffer);

    const provider = new PdfiumFormProvider();
    await provider.fetchFields(new Blob([bytes]), { exhaustive: true });

    expect(extractFormFields).toHaveBeenCalledTimes(1);
  });
});

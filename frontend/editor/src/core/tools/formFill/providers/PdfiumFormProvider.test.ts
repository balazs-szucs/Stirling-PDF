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
  readRawFormType: vi.fn(async () => 0),
  getPdfiumModule: vi.fn(async () => ({ FPDF_CloseDocument: vi.fn() })),
}));

import { getDocumentBytes } from "@app/services/documentBytesCache";
import {
  extractFormFields,
  readRawFormType,
} from "@app/services/pdfiumService";
import { containsAscii } from "@app/utils/asciiBytes";
import { LARGE_PDF_PARSE_LIMIT } from "@app/utils/thumbnailUtils";
import { PdfiumFormProvider } from "@app/tools/formFill/providers/PdfiumFormProvider";

describe("PdfiumFormProvider", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (readRawFormType as Mock).mockResolvedValue(0);
  });

  const noLiteralBytes = () =>
    new TextEncoder().encode(
      "%PDF-1.7\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF",
    );

  it("keeps the fast path when the catalog probe reports no form", async () => {
    const bytes = noLiteralBytes();
    expect(containsAscii(bytes, "/AcroForm")).toBe(false);
    (getDocumentBytes as Mock).mockResolvedValue(bytes.buffer);
    (readRawFormType as Mock).mockResolvedValue(0);

    const provider = new PdfiumFormProvider();
    await provider.fetchFields(new Blob([bytes]));

    expect(readRawFormType).toHaveBeenCalledTimes(1);
    expect(extractFormFields).not.toHaveBeenCalled();
  });

  it("extracts when the catalog probe finds a form the literal scan missed", async () => {
    // qpdf --object-streams=generate hides the catalog, so the byte scan is
    // not a proof of absence; the form-type probe is.
    const bytes = noLiteralBytes();
    (getDocumentBytes as Mock).mockResolvedValue(bytes.buffer);
    (readRawFormType as Mock).mockResolvedValue(1);

    const provider = new PdfiumFormProvider();
    await provider.fetchFields(new Blob([bytes]));

    expect(extractFormFields).toHaveBeenCalledTimes(1);
  });

  it("extracts when the probe cannot answer (unknown is not a miss)", async () => {
    const bytes = noLiteralBytes();
    (getDocumentBytes as Mock).mockResolvedValue(bytes.buffer);
    (readRawFormType as Mock).mockResolvedValue(null);

    const provider = new PdfiumFormProvider();
    await provider.fetchFields(new Blob([bytes]));

    expect(extractFormFields).toHaveBeenCalledTimes(1);
  });

  it("skips the probe above the full-parse limit so large files stay gated", async () => {
    const bytes = noLiteralBytes();
    (getDocumentBytes as Mock).mockResolvedValue(bytes.buffer);
    const largeBlob = { size: LARGE_PDF_PARSE_LIMIT } as unknown as Blob;

    const provider = new PdfiumFormProvider();
    await provider.fetchFields(largeBlob);

    expect(readRawFormType).not.toHaveBeenCalled();
    expect(extractFormFields).not.toHaveBeenCalled();
  });

  it("extracts exhaustively even without the literal (compressed object streams)", async () => {
    const bytes = noLiteralBytes();
    (getDocumentBytes as Mock).mockResolvedValue(bytes.buffer);

    const provider = new PdfiumFormProvider();
    await provider.fetchFields(new Blob([bytes]), { exhaustive: true });

    expect(readRawFormType).not.toHaveBeenCalled();
    expect(extractFormFields).toHaveBeenCalledTimes(1);
  });
});

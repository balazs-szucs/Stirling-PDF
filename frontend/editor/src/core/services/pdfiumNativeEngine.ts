export interface NativePdfPageRender {
  width: number;
  height: number;
  data: Uint8ClampedArray;
}

export interface NativePdfDocument {
  handle: number;
  pageCount: number;
  getPageCount(): Promise<number>;
  renderPage(pageIndex: number, scale?: number): Promise<NativePdfPageRender>;
  extractText(pageIndex: number): Promise<string>;
  close(): Promise<void>;
}

export class NativePdfEngineAdapter {
  async init(): Promise<void> {
    throw new Error(
      "Native PDFium Engine is only supported in Tauri desktop builds.",
    );
  }

  async openDocument(
    _fileData: ArrayBuffer | Uint8Array,
    _password?: string,
  ): Promise<NativePdfDocument> {
    throw new Error(
      "Native PDFium Engine is only supported in Tauri desktop builds.",
    );
  }
}

export const nativePdfEngineAdapter = new NativePdfEngineAdapter();

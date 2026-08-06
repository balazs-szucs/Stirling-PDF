import { invoke } from "@tauri-apps/api/core";

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
  private isInitialized = false;

  async init(): Promise<void> {
    this.isInitialized = true;
  }

  async openDocument(
    fileData: ArrayBuffer | Uint8Array,
    password?: string,
  ): Promise<NativePdfDocument> {
    const bytes =
      fileData instanceof Uint8Array ? fileData : new Uint8Array(fileData);

    const handle = await invoke<number>("pdfium_native_open_document", {
      fileBytes: bytes,
      password: password ?? null,
    });

    const pageCount = await invoke<number>("pdfium_native_get_page_count", {
      docHandle: handle,
    });

    return {
      handle,
      pageCount,
      getPageCount: async () => pageCount,
      renderPage: async (pageIndex: number, scale: number = 1.0) => {
        const frameBytes = await invoke<number[]>("pdfium_native_render_page", {
          docHandle: handle,
          pageIndex,
          scale,
        });

        const uint8Array = new Uint8Array(frameBytes);
        const view = new DataView(uint8Array.buffer);
        const width = view.getUint32(0, false);
        const height = view.getUint32(4, false);
        const rawPixels = new Uint8ClampedArray(uint8Array.subarray(8));

        return {
          width,
          height,
          data: rawPixels,
        };
      },
      extractText: async (pageIndex: number) => {
        return await invoke<string>("pdfium_native_extract_text", {
          docHandle: handle,
          pageIndex,
        });
      },
      close: async () => {
        await invoke("pdfium_native_close_document", { docHandle: handle });
      },
    };
  }
}

export const nativePdfEngineAdapter = new NativePdfEngineAdapter();

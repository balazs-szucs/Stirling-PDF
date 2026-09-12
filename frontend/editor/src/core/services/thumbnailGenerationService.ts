/**
 * High-performance thumbnail generation service using main thread processing
 */

import { FileId } from "@app/types/file";
import {
  openRawDocumentSafe,
  closeRawDocument,
} from "@app/services/pdfiumService";
import { renderPdfiumPageDataUrl } from "@app/utils/pdfiumPageRender";

export interface ThumbnailResult {
  pageNumber: number;
  thumbnail: string;
  success: boolean;
  error?: string;
}

interface ThumbnailGenerationOptions {
  scale?: number;
  quality?: number;
  batchSize?: number;
  parallelBatches?: number;
}

interface CachedThumbnail {
  thumbnail: string;
  lastUsed: number;
  sizeBytes: number;
}

interface CachedPDFDocument {
  docPtr: number;
  lastUsed: number;
  refCount: number;
}

export class ThumbnailGenerationService {
  // Session-based thumbnail cache
  private thumbnailCache = new Map<
    FileId | string /* FIX ME: Page ID */,
    CachedThumbnail
  >();
  private maxCacheSizeBytes = 1024 * 1024 * 1024; // 1GB cache limit
  private currentCacheSize = 0;

  // PDF document cache to reuse PDF instances and avoid creating multiple workers
  private pdfDocumentCache = new Map<FileId, CachedPDFDocument>();
  private maxPdfCacheSize = 10; // Keep up to 10 PDF documents cached

  constructor(private maxWorkers: number = 10) {
    // PDF rendering requires DOM access, so we use optimized main thread processing
  }

  /**
   * Get or create a cached PDFium document pointer. When the cache is full
   * and every entry is still rendering, the document opens uncached instead
   * of wedging the queue: eviction cannot free a slot nobody released.
   */
  private async getCachedPDFDocument(
    fileId: FileId,
    pdfArrayBuffer: ArrayBuffer,
  ): Promise<{ docPtr: number; cached: boolean }> {
    const cached = this.pdfDocumentCache.get(fileId);
    if (cached) {
      cached.lastUsed = Date.now();
      cached.refCount++;
      return { docPtr: cached.docPtr, cached: true };
    }

    const docPtr = await openRawDocumentSafe(pdfArrayBuffer);
    while (
      this.pdfDocumentCache.size >= this.maxPdfCacheSize &&
      this.evictLeastRecentlyUsedPDF()
    ) {
      /* each success frees exactly one slot */
    }
    if (this.pdfDocumentCache.size < this.maxPdfCacheSize) {
      this.pdfDocumentCache.set(fileId, {
        docPtr,
        lastUsed: Date.now(),
        refCount: 1,
      });
      return { docPtr, cached: true };
    }
    return { docPtr, cached: false };
  }

  /**
   * Release a reference to a cached PDF document
   */
  private releasePDFDocument(fileId: FileId): void {
    const cached = this.pdfDocumentCache.get(fileId);
    if (cached) {
      cached.refCount--;
      // Don't destroy immediately - keep in cache for potential reuse
    }
  }

  /**
   * Evict the least recently used PDF document. Entries with live renderers
   * cannot be freed; reports whether a slot opened so callers never spin.
   */
  private evictLeastRecentlyUsedPDF(): boolean {
    let oldestEntry: [FileId, CachedPDFDocument] | null = null;
    let oldestTime = Number.POSITIVE_INFINITY;

    for (const [key, value] of this.pdfDocumentCache.entries()) {
      if (value.refCount === 0 && value.lastUsed < oldestTime) {
        oldestTime = value.lastUsed;
        oldestEntry = [key, value];
      }
    }

    if (!oldestEntry) return false;
    void closeRawDocument(oldestEntry[1].docPtr);
    this.pdfDocumentCache.delete(oldestEntry[0]);
    return true;
  }

  /**
   * Generate thumbnails for multiple pages using main thread processing
   */
  async generateThumbnails(
    fileId: FileId,
    pdfArrayBuffer: ArrayBuffer,
    pageNumbers: number[],
    options: ThumbnailGenerationOptions = {},
    onProgress?: (progress: {
      completed: number;
      total: number;
      thumbnails: ThumbnailResult[];
    }) => void,
  ): Promise<ThumbnailResult[]> {
    // Input validation
    if (!fileId || typeof fileId !== "string" || fileId.trim() === "") {
      throw new Error("generateThumbnails: fileId must be a non-empty string");
    }

    if (!pdfArrayBuffer || pdfArrayBuffer.byteLength === 0) {
      throw new Error("generateThumbnails: pdfArrayBuffer must not be empty");
    }

    if (!pageNumbers || pageNumbers.length === 0) {
      throw new Error("generateThumbnails: pageNumbers must not be empty");
    }

    const { scale = 0.2, quality = 0.8 } = options;

    return await this.generateThumbnailsMainThread(
      fileId,
      pdfArrayBuffer,
      pageNumbers,
      scale,
      quality,
      onProgress,
    );
  }

  /**
   * Main thread thumbnail generation with batching for UI responsiveness
   */
  private async generateThumbnailsMainThread(
    fileId: FileId,
    pdfArrayBuffer: ArrayBuffer,
    pageNumbers: number[],
    scale: number,
    quality: number,
    onProgress?: (progress: {
      completed: number;
      total: number;
      thumbnails: ThumbnailResult[];
    }) => void,
  ): Promise<ThumbnailResult[]> {
    const { docPtr, cached } = await this.getCachedPDFDocument(
      fileId,
      pdfArrayBuffer,
    );

    try {
      const allResults: ThumbnailResult[] = [];
      let completed = 0;
      const batchSize = 3; // Smaller batches for better UI responsiveness

      // Process pages in small batches
      for (let i = 0; i < pageNumbers.length; i += batchSize) {
        const batch = pageNumbers.slice(i, i + batchSize);

        // Process batch sequentially (to avoid canvas conflicts)
        for (const pageNumber of batch) {
          try {
            const thumbnail = await renderPdfiumPageDataUrl(
              docPtr,
              pageNumber - 1,
              scale,
              {
                applyRotation: false,
                format: "jpeg",
                quality,
                returnBlobUrl: true,
              },
            );
            if (!thumbnail) {
              throw new Error(`Could not render page ${pageNumber}`);
            }
            allResults.push({ pageNumber, thumbnail, success: true });
          } catch (error) {
            console.error(
              `Failed to generate thumbnail for page ${pageNumber}:`,
              error,
            );
            allResults.push({
              pageNumber,
              thumbnail: "",
              success: false,
              error: error instanceof Error ? error.message : "Unknown error",
            });
          }
        }

        completed += batch.length;

        // Report progress
        if (onProgress) {
          onProgress({
            completed,
            total: pageNumbers.length,
            thumbnails: allResults.slice(-batch.length).filter((r) => r.success),
          });
        }

        // Yield control to prevent UI blocking
        await new Promise((resolve) => setTimeout(resolve, 1));
      }

      return allResults;
    } finally {
      if (cached) {
        // Release reference to PDF document (don't destroy - keep in cache)
        this.releasePDFDocument(fileId);
        this.cleanupCompletedDocument(fileId);
      } else {
        void closeRawDocument(docPtr);
      }
    }
  }

  /**
   * Cache management
   */
  getThumbnailFromCache(pageId: string): string | null {
    const cached = this.thumbnailCache.get(pageId);
    if (cached) {
      cached.lastUsed = Date.now();
      return cached.thumbnail;
    }
    return null;
  }

  addThumbnailToCache(pageId: string, thumbnail: string): void {
    const isBlob = thumbnail.startsWith("blob:");
    const sizeBytes = isBlob ? 100 * 1024 : thumbnail.length * 2; // Estimate 100KB for blob vs actual Base64 string length

    // Revoke any existing object URL for the same pageId if it changes,
    // and release its size reservation: set() overwrites, so keeping the
    // old bytes counted would inflate the cache until it evicts everything.
    const existing = this.thumbnailCache.get(pageId);
    if (existing) {
      if (
        existing.thumbnail.startsWith("blob:") &&
        existing.thumbnail !== thumbnail
      ) {
        URL.revokeObjectURL(existing.thumbnail);
      }
      this.currentCacheSize -= existing.sizeBytes;
    }

    // Enforce cache size limits
    while (
      this.currentCacheSize + sizeBytes > this.maxCacheSizeBytes &&
      this.thumbnailCache.size > 0
    ) {
      this.evictLeastRecentlyUsed();
    }

    this.thumbnailCache.set(pageId, {
      thumbnail,
      lastUsed: Date.now(),
      sizeBytes,
    });

    this.currentCacheSize += sizeBytes;
  }

  private evictLeastRecentlyUsed(): void {
    let oldestEntry: [string, CachedThumbnail] | null = null;
    let oldestTime = Date.now();

    for (const [key, value] of this.thumbnailCache.entries()) {
      if (value.lastUsed < oldestTime) {
        oldestTime = value.lastUsed;
        oldestEntry = [key, value];
      }
    }

    if (oldestEntry) {
      this.thumbnailCache.delete(oldestEntry[0]);
      this.currentCacheSize -= oldestEntry[1].sizeBytes;
      if (oldestEntry[1].thumbnail.startsWith("blob:")) {
        URL.revokeObjectURL(oldestEntry[1].thumbnail);
      }
    }
  }

  getCacheStats() {
    return {
      size: this.thumbnailCache.size,
      sizeBytes: this.currentCacheSize,
      maxSizeBytes: this.maxCacheSizeBytes,
    };
  }

  stopGeneration(): void {
    // No-op since we removed workers
  }

  clearCache(): void {
    for (const cached of this.thumbnailCache.values()) {
      if (cached.thumbnail.startsWith("blob:")) {
        URL.revokeObjectURL(cached.thumbnail);
      }
    }
    this.thumbnailCache.clear();
    this.currentCacheSize = 0;
  }

  clearPDFCache(): void {
    // Destroy all cached PDF documents using worker manager
    for (const [, cached] of this.pdfDocumentCache) {
      void closeRawDocument(cached.docPtr);
    }
    this.pdfDocumentCache.clear();
  }

  clearPDFCacheForFile(fileId: FileId): void {
    const cached = this.pdfDocumentCache.get(fileId);
    if (cached) {
      void closeRawDocument(cached.docPtr);
      this.pdfDocumentCache.delete(fileId);
    }
  }

  /**
   * Clean up a PDF document from cache when thumbnail generation is complete
   * This frees up workers faster for better performance
   */
  cleanupCompletedDocument(fileId: FileId): void {
    const cached = this.pdfDocumentCache.get(fileId);
    if (cached && cached.refCount <= 0) {
      void closeRawDocument(cached.docPtr);
      this.pdfDocumentCache.delete(fileId);
    }
  }

  destroy(): void {
    this.clearCache();
    this.clearPDFCache();
  }
}

// Global singleton instance
export const thumbnailGenerationService = new ThumbnailGenerationService();

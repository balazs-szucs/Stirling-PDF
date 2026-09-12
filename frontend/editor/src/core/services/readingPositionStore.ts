/**
 * Device-local reading positions, keyed by document fingerprint
 * (`StirlingFile.quickKey`). Metadata only: never document bytes or content.
 * Best-effort — a failed read/write must never block the viewer.
 */
import {
  DATABASE_CONFIGS,
  indexedDBManager,
} from "@app/services/indexedDBManager";

export interface ReadingPositionRecord {
  /** Document fingerprint (`name|size|lastModified`). */
  key: string;
  /** 0-based page index. */
  pageIndex: number;
  /** Viewport top-left inside the page, as a fraction of the unrotated page box. */
  xFraction: number;
  yFraction: number;
  /** EmbedPDF `ZoomLevel`: a `ZoomMode` string or a numeric scale. */
  zoomLevel: string | number;
  updatedAt: number;
}

const STORE_NAME = "positions";

async function database(): Promise<IDBDatabase> {
  return indexedDBManager.openDatabase(DATABASE_CONFIGS.READING_POSITIONS);
}

export async function readReadingPosition(
  key: string,
): Promise<ReadingPositionRecord | null> {
  try {
    const db = await database();
    return await new Promise((resolve, reject) => {
      const request = db
        .transaction(STORE_NAME, "readonly")
        .objectStore(STORE_NAME)
        .get(key);
      request.onsuccess = () =>
        resolve((request.result as ReadingPositionRecord | undefined) ?? null);
      request.onerror = () => reject(request.error);
    });
  } catch (error) {
    console.warn("[readingPosition] read failed:", error);
    return null;
  }
}

export async function writeReadingPosition(
  record: ReadingPositionRecord,
): Promise<void> {
  try {
    const db = await database();
    await new Promise<void>((resolve, reject) => {
      const transaction = db.transaction(STORE_NAME, "readwrite");
      transaction.objectStore(STORE_NAME).put(record);
      transaction.oncomplete = () => resolve();
      transaction.onerror = () => reject(transaction.error);
      transaction.onabort = () => reject(transaction.error);
    });
  } catch (error) {
    console.warn("[readingPosition] write failed:", error);
  }
}

export async function clearReadingPosition(key: string): Promise<void> {
  try {
    const db = await database();
    await new Promise<void>((resolve, reject) => {
      const transaction = db.transaction(STORE_NAME, "readwrite");
      transaction.objectStore(STORE_NAME).delete(key);
      transaction.oncomplete = () => resolve();
      transaction.onerror = () => reject(transaction.error);
      transaction.onabort = () => reject(transaction.error);
    });
  } catch (error) {
    console.warn("[readingPosition] clear failed:", error);
  }
}

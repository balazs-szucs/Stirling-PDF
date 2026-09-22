/**
 * Image thumbnails off the main thread: decode at tooltip width, encode a
 * small JPEG. A large photo costs 45 MB of RGBA to decode and 15-60 ms of
 * main-thread time if done inline, which is felt as jank when a library of
 * images opens at once. Falls back to the inline path when this worker or
 * OffscreenCanvas is unavailable (see thumbnailUtils).
 */
interface ThumbnailRequest {
  id: number;
  file: Blob;
  width: number;
}

self.onmessage = async (event: MessageEvent<ThumbnailRequest>) => {
  const { id, file, width } = event.data;
  try {
    const bitmap = await createImageBitmap(file, {
      resizeWidth: width,
      resizeQuality: "high",
      imageOrientation: "from-image",
    });
    try {
      const canvas = new OffscreenCanvas(bitmap.width, bitmap.height);
      const context = canvas.getContext("2d");
      if (!context) throw new Error("2d context unavailable");
      // Flatten alpha onto white: the JPEG below has no alpha channel.
      context.fillStyle = "#ffffff";
      context.fillRect(0, 0, canvas.width, canvas.height);
      context.drawImage(bitmap, 0, 0);
      const blob = await canvas.convertToBlob({
        type: "image/jpeg",
        quality: 0.8,
      });
      self.postMessage({ id, blob });
    } finally {
      bitmap.close();
    }
  } catch (error) {
    self.postMessage({ id, error: String(error) });
  }
};

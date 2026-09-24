import { useEffect, useRef, useState } from "react";
import { renderNativePdfPageBlob } from "@app/services/nativePdfRender";

/** Wide enough for a full-width viewer on a 2x display, small enough to land
 *  in tens of milliseconds. */
const POSTER_WIDTH = 1200;
/** Past this, show nothing more: the engine is the authority anyway. */
const GIVE_UP_MS = 12_000;
const POLL_MS = 120;

// Shadow of the core stub; @app alias order gives desktop builds this one.

/**
 * Page 1, rasterized natively, from the moment a file lands until the engine's
 * own page layer paints. The engine's first paint waits on a WASM document
 * open (hundreds of milliseconds on large files); the OS engine draws this
 * poster in single-digit milliseconds, so the viewer is no longer blank for
 * that window. It sits below the page layers and never takes pointer events,
 * so it disappears the moment real content covers it.
 */
export function NativeFirstPagePoster({
  filePath,
}: {
  filePath?: string | null;
}) {
  const [url, setUrl] = useState<string | null>(null);
  const urlRef = useRef<string | null>(null);

  const dropPoster = () => {
    if (urlRef.current) {
      URL.revokeObjectURL(urlRef.current);
      urlRef.current = null;
    }
    setUrl(null);
  };

  useEffect(() => {
    if (!filePath) {
      dropPoster();
      return;
    }
    let cancelled = false;
    void renderNativePdfPageBlob(filePath, 1, POSTER_WIDTH).then((blob) => {
      if (cancelled || !blob) return;
      urlRef.current = URL.createObjectURL(blob);
      setUrl(urlRef.current);
    });
    return () => {
      cancelled = true;
      dropPoster();
    };
    // dropPoster is stable enough for this effect: it only touches refs and state.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filePath]);

  useEffect(() => {
    if (!url) return;
    const started = Date.now();
    const timer = window.setInterval(() => {
      const painted = Array.from(
        document.querySelectorAll("[data-page-index]"),
      ).some(
        (page) =>
          page.querySelector('canvas, img[src^="blob:"], img[src^="data:"]') !==
          null,
      );
      if (painted || Date.now() - started > GIVE_UP_MS) {
        window.clearInterval(timer);
        dropPoster();
      }
    }, POLL_MS);
    return () => window.clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url]);

  if (!url) return null;
  return (
    <div
      aria-hidden="true"
      style={{
        position: "absolute",
        inset: 0,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        pointerEvents: "none",
        overflow: "hidden",
        zIndex: 0,
      }}
    >
      <img
        src={url}
        alt=""
        style={{
          maxWidth: "100%",
          maxHeight: "100%",
          background: "#fff",
          boxShadow: "0 2px 8px rgba(0, 0, 0, 0.15)",
        }}
      />
    </div>
  );
}

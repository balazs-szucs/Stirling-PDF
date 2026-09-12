/**
 * Owns the viewer's PDFium worker engine lifecycle.
 *
 * Mirrors `usePdfiumEngine` from `@embedpdf/engines/react`, but hands the engine
 * the `WebAssembly.Module` the app already compiled in `wasmPrecompiler`, so the
 * worker does not fetch and recompile pdfium.wasm on startup. The extra option is
 * ignored by an unpatched `@embedpdf/engines`; the local patch in
 * `scripts/patch-embedpdf-engines.mjs` consumes it and falls back to fetching the
 * URL on WebKit, where `WebAssembly.Module` is not structured-cloneable.
 */
import { useEffect, useRef, useState } from "react";
import { ignore, type Logger, type PdfEngine } from "@embedpdf/models";
import {
  createPdfiumEngine,
  type CreatePdfiumEngineOptions,
  type FontFallbackConfig,
} from "@embedpdf/engines/pdfium-worker-engine";
import {
  pdfiumWasmModulePromise,
  startEagerWasmCompilation,
} from "@app/services/wasmPrecompiler";

interface LocalPdfiumEngineOptions {
  wasmUrl: string;
  logger?: Logger;
  encoderPoolSize?: number;
  fontFallback?: FontFallbackConfig | null;
}

export function useLocalPdfiumEngine({
  wasmUrl,
  logger,
  encoderPoolSize,
  fontFallback,
}: LocalPdfiumEngineOptions) {
  const [engine, setEngine] = useState<PdfEngine<Blob> | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const engineRef = useRef<PdfEngine<Blob> | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        startEagerWasmCompilation();
        // Never block engine creation on a compile that a test harness or an
        // offline deployment may leave pending; the worker fetches the URL itself
        // when no module is handed over. The fallback timer is cleared on
        // settle so a fast compile does not leave a 3s straggler behind.
        let precompileTimer: ReturnType<typeof setTimeout> | undefined;
        const precompiled = await Promise.race([
          pdfiumWasmModulePromise,
          new Promise<null>((resolve) => {
            precompileTimer = setTimeout(() => resolve(null), 3000);
          }),
        ]).finally(() => clearTimeout(precompileTimer));
        const options: CreatePdfiumEngineOptions & {
          wasmModule?: WebAssembly.Module;
        } = { logger, encoderPoolSize, fontFallback };
        if (precompiled?.module) {
          options.wasmModule = precompiled.module;
        }
        const pdfEngine = createPdfiumEngine(wasmUrl, options);
        if (typeof performance !== "undefined") {
          performance.mark("pdfium-engine-created");
        }
        if (cancelled) {
          pdfEngine.closeAllDocuments?.()?.wait(() => pdfEngine.destroy?.(), ignore);
          return;
        }
        engineRef.current = pdfEngine;
        setEngine(pdfEngine);
        setIsLoading(false);
      } catch (cause) {
        if (!cancelled) {
          setError(cause instanceof Error ? cause : new Error(String(cause)));
          setIsLoading(false);
        }
      }
    })();

    return () => {
      cancelled = true;
      const current = engineRef.current;
      engineRef.current = null;
      current?.closeAllDocuments?.()?.wait(() => current?.destroy?.(), ignore);
    };
  }, [wasmUrl, logger, encoderPoolSize, fontFallback]);

  return { engine, isLoading, error };
}

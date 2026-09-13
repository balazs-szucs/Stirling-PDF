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
import { useCallback, useEffect, useRef, useState } from "react";
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
  cache?: {
    pageTtl?: number;
    maxPagesPerDocument?: number;
  };
}

// Minimum time between respawns so close/open churn cannot cycle workers.
const ENGINE_RESPAWN_COOLDOWN_MS = 5000;

export function useLocalPdfiumEngine({
  wasmUrl,
  logger,
  encoderPoolSize,
  fontFallback,
  cache,
}: LocalPdfiumEngineOptions) {
  const [engine, setEngine] = useState<PdfEngine<Blob> | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const engineRef = useRef<PdfEngine<Blob> | null>(null);
  const optionsRef = useRef<
    | (CreatePdfiumEngineOptions & {
        wasmModule?: WebAssembly.Module;
        cache?: {
          pageTtl?: number;
          maxPagesPerDocument?: number;
        };
      })
    | null
  >(null);
  const respawnInFlightRef = useRef(false);
  const lastRespawnAtRef = useRef(0);

  const buildEngine = async (): Promise<PdfEngine<Blob>> => {
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
      cache?: {
        pageTtl?: number;
        maxPagesPerDocument?: number;
      };
    } = { logger, encoderPoolSize, fontFallback, cache };
    if (precompiled?.module) {
      options.wasmModule = precompiled.module;
    }
    optionsRef.current = options;
    const pdfEngine = createPdfiumEngine(wasmUrl, options);
    if (typeof performance !== "undefined") {
      performance.mark("pdfium-engine-created");
    }
    return pdfEngine;
  };

  /**
   * Rebuild the engine from the same precompiled WASM module. Only safe while
   * no document is loaded in the viewer: the old worker is terminated and
   * pending engine tasks are aborted. Callers must gate on workbench-empty.
   */
  const respawnEngine = useCallback(() => {
    if (respawnInFlightRef.current) return;
    // Hysteresis: a rapid close/open/close cycle must not churn workers.
    if (Date.now() - lastRespawnAtRef.current < ENGINE_RESPAWN_COOLDOWN_MS) {
      return;
    }
    const previous = engineRef.current;
    respawnInFlightRef.current = true;
    (async () => {
      try {
        const next = await buildEngine();
        // Swap first so consumers created after the swap never see null.
        engineRef.current = next;
        setEngine(next);
        lastRespawnAtRef.current = Date.now();
        if (typeof performance !== "undefined") {
          performance.mark("pdfium-engine-respawned");
        }
        previous
          ?.closeAllDocuments?.()
          ?.wait(() => previous?.destroy?.(), ignore);
      } catch (cause) {
        // Keep the previous engine usable: a respawn failure must not take
        // the whole viewer down; the floor stays until the next attempt.
        console.warn("[useLocalPdfiumEngine] engine respawn failed:", cause);
      } finally {
        respawnInFlightRef.current = false;
      }
    })();
  }, [logger, encoderPoolSize, fontFallback]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const pdfEngine = await buildEngine();
        if (cancelled) {
          pdfEngine
            .closeAllDocuments?.()
            ?.wait(() => pdfEngine.destroy?.(), ignore);
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

  return { engine, isLoading, error, respawnEngine };
}

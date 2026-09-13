import { ReactNode, useEffect, useMemo, useRef, useState } from "react";
if (import.meta.env.DEV) {
  void import("@app/debug/memoryTelemetry");
}
import { QueryClientProvider } from "@tanstack/react-query";
import { createAppQueryClient } from "@app/query/queryClient";
import { ThemeProvider } from "@app/components/shared/ThemeProvider";
import { FileContextProvider } from "@app/contexts/FileContext";
import { NavigationProvider } from "@app/contexts/NavigationContext";
import { ToolRegistryProvider } from "@app/contexts/ToolRegistryProvider";
import { FilesModalProvider } from "@app/contexts/FilesModalContext";
import { ToolWorkflowProvider } from "@app/contexts/ToolWorkflowContext";
import { HotkeyProvider } from "@app/contexts/HotkeyContext";
import { SidebarProvider } from "@app/contexts/SidebarContext";
import {
  PreferencesProvider,
  usePreferences,
} from "@app/contexts/PreferencesContext";
import {
  AppConfigProvider,
  AppConfigProviderProps,
  AppConfigRetryOptions,
  useAppConfig,
} from "@app/contexts/AppConfigContext";
import { WorkbenchBarProvider } from "@app/contexts/WorkbenchBarContext";
import { ViewerProvider } from "@app/contexts/ViewerContext";
import { SignatureProvider } from "@app/contexts/SignatureContext";
import { SigningOverlayProvider } from "@app/contexts/SigningOverlayContext";
import { AnnotationProvider } from "@app/contexts/AnnotationContext";
import { TourOrchestrationProvider } from "@app/contexts/TourOrchestrationContext";
import { AdminTourOrchestrationProvider } from "@app/contexts/AdminTourOrchestrationContext";
import { PageEditorProvider } from "@app/contexts/PageEditorContext";
import { BannerProvider } from "@app/contexts/BannerContext";
import ErrorBoundary from "@app/components/shared/ErrorBoundary";
import { usePosthogTracking } from "@app/hooks/usePosthogTracking";
import { useScarfTracking } from "@app/hooks/useScarfTracking";
import { useAppInitialization } from "@app/hooks/useAppInitialization";
import { useLogoAssets } from "@app/hooks/useLogoAssets";
import AppConfigLoader from "@app/components/shared/AppConfigLoader";
import { UpdateStartupPopup } from "@app/components/shared/UpdateStartupPopup";
import { RedactionProvider } from "@app/contexts/RedactionContext";
import { FormFillProvider } from "@app/tools/formFill/FormFillContext";
import { FolderFileContextProvider } from "@app/contexts/FolderFileContext";
import { FolderProvider } from "@app/contexts/FolderContext";
import { WorkbenchSessionPersistence } from "@app/components/session/WorkbenchSessionPersistence";

// Component to initialize scarf tracking (must be inside AppConfigProvider)
function ScarfTrackingInitializer() {
  useScarfTracking();
  return null;
}

import { PdfEngineProvider } from "@embedpdf/engines/react";
import { pdfiumWasmUrl } from "@app/services/wasmPrecompiler";
import { getLocalFontFallbackConfig } from "@app/services/pdfiumFontFallback";
import { useLocalPdfiumEngine } from "@app/hooks/useLocalPdfiumEngine";
import { useFileSelector } from "@app/contexts/file/fileHooks";
import {
  consumeRemovedDocumentBytes,
} from "@app/services/engineRespawnSignal";

// The engine worker's wasm floor is the document's full clone plus PDFium
// caches: 50.6 MB for the 40 MB fixture, 46.4 MB for the form fixture, 188 MB
// for the 155 MB fixture. Below ~10 MB the clone is small and the base module
// dominates, so respawning there buys little; at and above it the worker is
// rebuilt from the precompiled module (~1 ms) the next time the workbench
// empties. The 5 s cooldown in useLocalPdfiumEngine keeps close/open churn
// from cycling workers.
const ENGINE_RESPAWN_THRESHOLD_BYTES = 10 * 1024 * 1024;

function PosthogTrackingInitializer() {
  usePosthogTracking();
  return null;
}
// Component to run app-level initialization (must be inside AppProviders for context access)
function AppInitializer() {
  useAppInitialization();
  return null;
}

function BrandingAssetManager() {
  const { favicon, logo192, manifestHref } = useLogoAssets();

  useEffect(() => {
    if (typeof document === "undefined") {
      return;
    }

    const setLinkHref = (selector: string, href: string) => {
      const link = document.querySelector<HTMLLinkElement>(selector);
      if (link && link.getAttribute("href") !== href) {
        link.setAttribute("href", href);
      }
    };

    setLinkHref('link[rel="icon"]', favicon);
    setLinkHref('link[rel="shortcut icon"]', favicon);
    setLinkHref('link[rel="apple-touch-icon"]', logo192);
    setLinkHref('link[rel="manifest"]', manifestHref);
  }, [favicon, logo192, manifestHref]);

  return null;
}

// Avoid requirement to have props which are required in app providers anyway
type AppConfigProviderOverrides = Omit<
  AppConfigProviderProps,
  "children" | "retryOptions"
>;

export interface AppProvidersProps {
  children: ReactNode;
  appConfigRetryOptions?: AppConfigRetryOptions;
  appConfigProviderProps?: Partial<AppConfigProviderOverrides>;
}

// Component to sync server defaults to preferences when AppConfig loads
function ServerDefaultsSync() {
  const { config } = useAppConfig();
  const { updateServerDefaults } = usePreferences();

  useEffect(() => {
    if (config) {
      const serverDefaults = {
        hideUnavailableTools: config.defaultHideUnavailableTools ?? false,
        hideUnavailableConversions:
          config.defaultHideUnavailableConversions ?? false,
      };
      updateServerDefaults(serverDefaults);
    }
  }, [config, updateServerDefaults]);

  return null;
}

/**
 * Respawns the app-level PDFium worker when the workbench empties right after
 * a very large document. Wasm linear memory only grows, so the worker keeps
 * its post-open high-water (188 MB for a 155 MB file) for the whole session;
 * terminating and rebuilding the engine worker from the precompiled module is
 * the only reclaim. Runs with no document loaded, so no engine task can be
 * mid-flight; the hook's cooldown blocks close/open churn.
 */
function EngineRespawnWatcher({ respawn }: { respawn: () => void }) {
  const fileCount = useFileSelector((s) => s.files.ids.length);
  const respawnedEmptyRef = useRef(false);
  useEffect(() => {
    if (fileCount > 0) {
      respawnedEmptyRef.current = false;
      return;
    }
    if (respawnedEmptyRef.current) return;
    respawnedEmptyRef.current = true;
    const bytes = consumeRemovedDocumentBytes();
    if (bytes >= ENGINE_RESPAWN_THRESHOLD_BYTES) {
      respawn();
    }
  }, [fileCount, respawn]);
  return null;
}

/**
 * Core application providers
 * Contains all providers needed for the core
 */
export function AppProviders({
  children,
  appConfigRetryOptions,
  appConfigProviderProps,
}: AppProvidersProps) {
  const [queryClient] = useState(createAppQueryClient);
  // Stable identity: useLocalPdfiumEngine recreates the engine whenever this prop changes.
  const fontFallback = useMemo(() => getLocalFontFallbackConfig(), []);
  const { engine, isLoading, error, respawnEngine } = useLocalPdfiumEngine({
    wasmUrl: pdfiumWasmUrl,
    encoderPoolSize:
      typeof navigator !== "undefined" && navigator.hardwareConcurrency
        ? Math.min(2, navigator.hardwareConcurrency)
        : 1,
    fontFallback,
  });

  return (
    <QueryClientProvider client={queryClient}>
      <PreferencesProvider>
        <ThemeProvider>
          <ErrorBoundary>
            <BannerProvider>
              <AppConfigProvider
                retryOptions={appConfigRetryOptions}
                {...appConfigProviderProps}
              >
                <PosthogTrackingInitializer />
                <ScarfTrackingInitializer />
                <AppConfigLoader />
                <ServerDefaultsSync />
                {/* Auto-popup on startup when a newer Stirling-PDF release is available.
                  No-ops inside Tauri — the desktop popup handles that flow. */}
                <UpdateStartupPopup />
                <PdfEngineProvider
                  engine={engine}
                  isLoading={isLoading}
                  error={error}
                >
                  <FileContextProvider
                    enableUrlSync={true}
                    enablePersistence={true}
                  >
                    <EngineRespawnWatcher respawn={respawnEngine} />
                    <FolderProvider>
                      <AppInitializer />
                      <BrandingAssetManager />
                      <ToolRegistryProvider>
                        <NavigationProvider>
                          <FilesModalProvider>
                            <ToolWorkflowProvider>
                              <HotkeyProvider>
                                <SidebarProvider>
                                  <ViewerProvider>
                                    <PageEditorProvider>
                                      <SignatureProvider>
                                        <SigningOverlayProvider>
                                          <RedactionProvider>
                                            <FormFillProvider>
                                              <AnnotationProvider>
                                                <WorkbenchBarProvider>
                                                  <TourOrchestrationProvider>
                                                    <AdminTourOrchestrationProvider>
                                                      <FolderFileContextProvider>
                                                        <WorkbenchSessionPersistence />
                                                        {children}
                                                      </FolderFileContextProvider>
                                                    </AdminTourOrchestrationProvider>
                                                  </TourOrchestrationProvider>
                                                </WorkbenchBarProvider>
                                              </AnnotationProvider>
                                            </FormFillProvider>
                                          </RedactionProvider>
                                        </SigningOverlayProvider>
                                      </SignatureProvider>
                                    </PageEditorProvider>
                                  </ViewerProvider>
                                </SidebarProvider>
                              </HotkeyProvider>
                            </ToolWorkflowProvider>
                          </FilesModalProvider>
                        </NavigationProvider>
                      </ToolRegistryProvider>
                    </FolderProvider>
                  </FileContextProvider>
                </PdfEngineProvider>
              </AppConfigProvider>
            </BannerProvider>
          </ErrorBoundary>
        </ThemeProvider>
      </PreferencesProvider>
    </QueryClientProvider>
  );
}

import type { PostHog } from "posthog-js";

const DEV = process.env.NODE_ENV === "development";

// posthog-js is only ever useful after `posthog.init()` has run with consent;
// importing it statically put its 73 KB vendor chunk on the entry graph of
// every flavor, including self-hosted installs where analytics are off.
let posthogModule: Promise<typeof import("posthog-js")> | null = null;

// Analytics events must not pull the posthog chunk on installs where the
// admin disabled analytics; the PostHog tracking hook sets this from config.
let analyticsEnabled = false;

export function setAnalyticsEnabled(enabled: boolean): void {
  analyticsEnabled = enabled;
}

function loadPosthog(): Promise<PostHog | null> {
  posthogModule ??= import("posthog-js");
  return posthogModule.then((module) => module.default).catch(() => null);
}

function canCapture(posthog: PostHog): boolean {
  const ph = posthog as unknown as {
    __loaded?: boolean;
    has_opted_in_capturing?: () => boolean;
  };
  if (!ph.__loaded) return false;
  return (
    typeof ph.has_opted_in_capturing !== "function" ||
    ph.has_opted_in_capturing()
  );
}

export function trackPdfUploaded(files: File[]): void {
  if (!analyticsEnabled || !files) return;
  void loadPosthog().then((posthog) => {
    try {
      if (!posthog || !canCapture(posthog)) return;
      for (let i = 0; i < files.length; i++) {
        posthog.capture("editor_pdf_uploaded", { source: "editor" });
      }
    } catch (error) {
      if (DEV) console.warn("[analytics] trackPdfUploaded failed", error);
    }
  });
}

export function trackEditorOperation(toolId: string, fileCount: number): void {
  if (!analyticsEnabled) return;
  void loadPosthog().then((posthog) => {
    try {
      if (!posthog || !canCapture(posthog)) return;
      posthog.capture("editor_operation", {
        source: "editor",
        tool: toolId,
        file_count: fileCount,
      });
    } catch (error) {
      if (DEV) console.warn("[analytics] trackEditorOperation failed", error);
    }
  });
}

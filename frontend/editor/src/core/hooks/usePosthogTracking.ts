import { useEffect } from "react";
import type { PostHog } from "posthog-js";
import { useAppConfig } from "@app/contexts/AppConfigContext";
import { setAnalyticsEnabled } from "@app/services/analytics";

// posthog-js is only needed when analytics are enabled and a key is compiled
// in; the dynamic import keeps its vendor chunk off the startup graph for
// self-hosted installs with analytics off. The module promise is shared so
// repeated mounts do not re-request the chunk.
let posthogModule: Promise<typeof import("posthog-js")> | null = null;

function loadPosthog(): Promise<PostHog | null> {
  posthogModule ??= import("posthog-js");
  return posthogModule.then((module) => module.default).catch(() => null);
}

function applyPosthogConsent(posthog: PostHog): void {
  if (typeof window === "undefined" || !posthog.__loaded) {
    return;
  }

  const optedIn =
    window.CookieConsent?.acceptedService?.("posthog", "analytics") || false;

  if (optedIn) {
    posthog.set_config({ persistence: "localStorage+cookie" });
    posthog.opt_in_capturing();
    return;
  }

  posthog.opt_out_capturing();
  posthog.set_config({ persistence: "memory" });
}

function ensurePosthogInitialized(posthog: PostHog): boolean {
  if (typeof window === "undefined") {
    return false;
  }

  const posthogKey = import.meta.env.VITE_PUBLIC_POSTHOG_KEY;
  const posthogHost = import.meta.env.VITE_PUBLIC_POSTHOG_HOST;

  if (!posthogKey || !posthogHost) {
    return false;
  }

  if (!posthog.__loaded) {
    posthog.init(posthogKey, {
      api_host: posthogHost,
      defaults: "2025-05-24",
      capture_exceptions: true,
      debug: false,
      opt_out_capturing_by_default: true,
      persistence: "memory",
      cross_subdomain_cookie: false,
    });
  }

  return true;
}

export function usePosthogTracking(): void {
  const { config } = useAppConfig();

  useEffect(() => {
    const analyticsEnabled = config?.enableAnalytics === true;
    const posthogEnabled = analyticsEnabled && config?.enablePosthog !== false;
    setAnalyticsEnabled(posthogEnabled);
    let cancelled = false;
    let removeConsentListeners: (() => void) | undefined;

    void loadPosthog().then((posthog) => {
      if (!posthog || cancelled) return;

      if (!posthogEnabled) {
        if (posthog.__loaded) {
          posthog.opt_out_capturing();
          posthog.set_config({ persistence: "memory" });
        }
        return;
      }

      if (!ensurePosthogInitialized(posthog)) {
        return;
      }

      applyPosthogConsent(posthog);

      const handleConsentChange = () => {
        applyPosthogConsent(posthog);
      };

      window.addEventListener("cc:onConsent", handleConsentChange);
      window.addEventListener("cc:onChange", handleConsentChange);
      removeConsentListeners = () => {
        window.removeEventListener("cc:onConsent", handleConsentChange);
        window.removeEventListener("cc:onChange", handleConsentChange);
      };
    });

    return () => {
      cancelled = true;
      removeConsentListeners?.();
    };
  }, [config?.enableAnalytics, config?.enablePosthog]);
}

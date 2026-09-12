/**
 * Opt-in local UX session recorder for Phase 2 felt-moment measurements.
 * Disabled unless the URL carries `?uxstudy=1`; events stay in memory and are
 * inspectable via `window.__uxSession`.
 */

export interface UxEvent {
  name: string;
  /** `performance.now()` at record time. */
  at: number;
  data?: Record<string, unknown>;
}

const enabled =
  typeof window !== "undefined" &&
  new URLSearchParams(window.location.search).has("uxstudy");

export function isUxStudyEnabled(): boolean {
  return enabled;
}

export function recordUxEvent(
  name: string,
  data?: Record<string, unknown>,
): void {
  if (!enabled) return;
  const target = window as unknown as { __uxSession?: UxEvent[] };
  if (!target.__uxSession) target.__uxSession = [];
  target.__uxSession.push({ name, at: Math.round(performance.now()), data });
  console.debug("[ux]", name, data ?? {});
}

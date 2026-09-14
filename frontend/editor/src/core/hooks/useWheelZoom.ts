import { RefObject, useEffect } from "react";

interface UseWheelZoomOptions {
  /**
   * Element the wheel listener should be bound to.
   */
  ref: RefObject<Element | null>;
  /**
   * Callback executed when the hook decides to zoom in.
   */
  onZoomIn: () => void;
  /**
   * Callback executed when the hook decides to zoom out.
   */
  onZoomOut: () => void;
  /**
   * Whether the wheel listener should be active.
   */
  enabled?: boolean;
  /**
   * How much delta needs to accumulate before a zoom action is triggered.
   * Defaults to 10 which matches the previous implementations.
   */
  threshold?: number;
  /**
   * Whether a Ctrl/Cmd modifier is required for zooming. Defaults to true so
   * we only react to pinch gestures and intentional ctrl+wheel zooming.
   */
  requireModifierKey?: boolean;
}

/**
 * Shared hook for handling wheel-based zoom across components.
 * It normalises accumulated delta behaviour, prevents default scrolling when
 * zoom is triggered, and keeps the handler detached when disabled.
 */
export function useWheelZoom({
  ref,
  onZoomIn,
  onZoomOut,
  enabled = true,
  threshold = 10,
  requireModifierKey = true,
}: UseWheelZoomOptions) {
  useEffect(() => {
    if (!enabled) {
      return;
    }

    const element = ref.current;
    if (!element) {
      return;
    }

    let accumulator = 0;
    let isNonPassiveAttached = false;
    let modifierTimeout: ReturnType<typeof setTimeout> | null = null;
    let isModifierKeyDown = false;

    const applyZoomDelta = (delta: number) => {
      accumulator += delta;
      if (accumulator <= -threshold) {
        onZoomIn();
        accumulator = 0;
      } else if (accumulator >= threshold) {
        onZoomOut();
        accumulator = 0;
      }
    };

    const handleWheelActive = (event: Event) => {
      const wheelEvent = event as WheelEvent;
      const hasModifier = wheelEvent.ctrlKey || wheelEvent.metaKey;
      if (requireModifierKey && !hasModifier) {
        return;
      }

      wheelEvent.preventDefault();
      wheelEvent.stopPropagation();
      applyZoomDelta(wheelEvent.deltaY);

      if (requireModifierKey && !isModifierKeyDown) {
        if (modifierTimeout) clearTimeout(modifierTimeout);
        modifierTimeout = setTimeout(() => {
          detachNonPassive();
        }, 300);
      }
    };

    const attachNonPassive = () => {
      if (isNonPassiveAttached) return;
      element.addEventListener("wheel", handleWheelActive, { passive: false });
      isNonPassiveAttached = true;
    };

    const detachNonPassive = () => {
      if (
        !isNonPassiveAttached ||
        (!requireModifierKey ? false : isModifierKeyDown)
      )
        return;
      element.removeEventListener("wheel", handleWheelActive);
      isNonPassiveAttached = false;
    };

    // If modifier is not required, keep the non-passive handler attached permanently.
    if (!requireModifierKey) {
      attachNonPassive();
      return () => {
        element.removeEventListener("wheel", handleWheelActive);
      };
    }

    // Passive listener detects pinch/modifier wheel events without blocking WebKit's
    // asynchronous compositor scrolling thread during standard navigation.
    const handlePassiveWheel = (event: Event) => {
      const wheelEvent = event as WheelEvent;
      if (wheelEvent.ctrlKey || wheelEvent.metaKey) {
        attachNonPassive();
        applyZoomDelta(wheelEvent.deltaY);
        if (modifierTimeout) clearTimeout(modifierTimeout);
        modifierTimeout = setTimeout(() => {
          detachNonPassive();
        }, 300);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (
        event.ctrlKey ||
        event.metaKey ||
        event.key === "Control" ||
        event.key === "Meta"
      ) {
        isModifierKeyDown = true;
        attachNonPassive();
      }
    };

    const handleKeyUp = (event: KeyboardEvent) => {
      if (!event.ctrlKey && !event.metaKey) {
        isModifierKeyDown = false;
        detachNonPassive();
      }
    };

    const handleBlur = () => {
      isModifierKeyDown = false;
      detachNonPassive();
    };

    // WebKit-specific trackpad pinch gesture support (macOS WKWebView / Safari)
    let lastGestureScale = 1;
    const handleGestureStart = (event: Event) => {
      event.preventDefault();
      lastGestureScale = 1;
      attachNonPassive();
    };

    const handleGestureChange = (event: Event) => {
      event.preventDefault();
      const gestureEvent = event as Event & { scale?: number };
      if (typeof gestureEvent.scale === "number") {
        const delta = (1 - gestureEvent.scale / lastGestureScale) * 100;
        lastGestureScale = gestureEvent.scale;
        applyZoomDelta(delta);
      }
    };

    const handleGestureEnd = (event: Event) => {
      event.preventDefault();
      detachNonPassive();
    };

    element.addEventListener("wheel", handlePassiveWheel, { passive: true });
    window.addEventListener("keydown", handleKeyDown, true);
    window.addEventListener("keyup", handleKeyUp, true);
    window.addEventListener("blur", handleBlur);
    element.addEventListener("gesturestart", handleGestureStart);
    element.addEventListener("gesturechange", handleGestureChange);
    element.addEventListener("gestureend", handleGestureEnd);

    return () => {
      if (modifierTimeout) clearTimeout(modifierTimeout);
      element.removeEventListener("wheel", handlePassiveWheel);
      if (isNonPassiveAttached) {
        element.removeEventListener("wheel", handleWheelActive);
      }
      window.removeEventListener("keydown", handleKeyDown, true);
      window.removeEventListener("keyup", handleKeyUp, true);
      window.removeEventListener("blur", handleBlur);
      element.removeEventListener("gesturestart", handleGestureStart);
      element.removeEventListener("gesturechange", handleGestureChange);
      element.removeEventListener("gestureend", handleGestureEnd);
    };
  }, [ref, onZoomIn, onZoomOut, enabled, threshold, requireModifierKey]);
}

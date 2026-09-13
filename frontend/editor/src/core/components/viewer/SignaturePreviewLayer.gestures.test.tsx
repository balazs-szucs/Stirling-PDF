/**
 * Gesture listeners on the signature preview must be removed when the pointer
 * is cancelled: a cancelled pointer never fires pointerup, so without a
 * pointercancel handler the move/up listeners and the paused interaction
 * outlive the gesture for the element's lifetime.
 */
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { render, cleanup, fireEvent } from "@testing-library/react";
import { MantineProvider } from "@mantine/core";
import { SignaturePreviewLayer } from "@app/components/viewer/SignaturePreviewLayer";
import type { SignaturePreview } from "@app/components/viewer/viewerTypes";

const PREVIEW: SignaturePreview = {
  id: "sig-1",
  pageIndex: 0,
  x: 0.2,
  y: 0.6,
  width: 0.25,
  height: 0.1,
  signatureData: "data:image/png;base64,AAAA",
  signatureType: "image",
};

function renderLayer() {
  return render(
    <MantineProvider>
      <SignaturePreviewLayer
        pageIndex={0}
        pageWidth={600}
        pageHeight={800}
        previews={[PREVIEW]}
        readOnly={false}
        placementMode={false}
        onChange={() => {}}
      />
    </MantineProvider>,
  );
}

/** Mirror the element's listener registry while still really attaching them. */
function spyListeners(el: Element) {
  const attached = new Map<string, EventListener[]>();
  const realAdd = el.addEventListener.bind(el);
  const realRemove = el.removeEventListener.bind(el);
  vi.spyOn(el, "addEventListener").mockImplementation((
    (type: string, handler: EventListener, options?: unknown) => {
      const list = attached.get(type) ?? [];
      list.push(handler);
      attached.set(type, list);
      realAdd(type, handler, options);
    }) as typeof el.addEventListener,
  );
  vi.spyOn(el, "removeEventListener").mockImplementation((
    (type: string, handler: EventListener, options?: unknown) => {
      const list = attached.get(type) ?? [];
      const idx = list.indexOf(handler);
      if (idx >= 0) list.splice(idx, 1);
      realRemove(type, handler, options);
    }) as typeof el.removeEventListener,
  );
  return attached;
}

describe("SignaturePreviewLayer gesture cleanup", () => {
  beforeEach(() => {
    Object.defineProperty(Element.prototype, "hasPointerCapture", {
      configurable: true,
      value: function () {
        return true;
      },
    });
    Object.defineProperty(Element.prototype, "setPointerCapture", {
      configurable: true,
      value: function () {},
    });
    Object.defineProperty(Element.prototype, "releasePointerCapture", {
      configurable: true,
      value: function () {},
    });
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  const pointerEvent = (type: string) =>
    new PointerEvent(type, {
      bubbles: true,
      cancelable: true,
      pointerId: 1,
      clientX: 10,
      clientY: 10,
    });

  it("drag listeners are removed when the pointer is cancelled", () => {
    const { container } = renderLayer();
    const img = container.querySelector('img[alt="Signature preview"]');
    expect(img).toBeTruthy();
    const el = img!.parentElement!;
    const attached = spyListeners(el);

    fireEvent(el, pointerEvent("pointerdown"));
    expect(attached.get("pointermove")?.length).toBe(1);
    expect(attached.get("pointerup")?.length).toBe(1);
    expect(attached.get("pointercancel")?.length).toBe(1);

    fireEvent(el, pointerEvent("pointercancel"));

    expect(attached.get("pointermove")?.length).toBe(0);
    expect(attached.get("pointerup")?.length).toBe(0);
    expect(attached.get("pointercancel")?.length).toBe(0);
  });

  it("drag listeners are removed on normal pointerup", () => {
    const { container } = renderLayer();
    const img = container.querySelector('img[alt="Signature preview"]');
    const el = img!.parentElement!;
    const attached = spyListeners(el);

    fireEvent(el, pointerEvent("pointerdown"));
    fireEvent(el, pointerEvent("pointerup"));

    expect(attached.get("pointermove")?.length).toBe(0);
    expect(attached.get("pointerup")?.length).toBe(0);
    expect(attached.get("pointercancel")?.length).toBe(0);
  });

  it("resize listeners are removed when the pointer is cancelled", () => {
    const { container } = renderLayer();
    const handle = container.querySelector<HTMLElement>(
      '[data-resize-handle="true"]',
    );
    expect(handle).toBeTruthy();
    const attached = spyListeners(handle!);

    fireEvent(handle!, pointerEvent("pointerdown"));
    expect(attached.get("pointermove")?.length).toBe(1);
    expect(attached.get("pointercancel")?.length).toBe(1);

    fireEvent(handle!, pointerEvent("pointercancel"));

    expect(attached.get("pointermove")?.length).toBe(0);
    expect(attached.get("pointerup")?.length).toBe(0);
    expect(attached.get("pointercancel")?.length).toBe(0);
  });
});

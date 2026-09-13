import { describe, expect, it, vi } from "vitest";
import { setSelectionRange } from "@app/components/viewer/TextSelectionHandler";

describe("setSelectionRange", () => {
  it("emits the full begin/update/end sequence", () => {
    const calls: string[] = [];
    const plugin = {
      clearSelection: vi.fn(() => calls.push("clear")),
      beginSelection: vi.fn((_id: string, page: number, glyph: number) =>
        calls.push(`begin:${page}:${glyph}`),
      ),
      updateSelection: vi.fn((_id: string, page: number, glyph: number) =>
        calls.push(`update:${page}:${glyph}`),
      ),
      endSelection: vi.fn(() => calls.push("end")),
    };

    setSelectionRange(plugin, "doc", 0, 5, 12);

    // The redaction/annotation plugins only convert on endSelection$; a
    // setSelection() shortcut would stop after `update`.
    expect(calls).toEqual(["clear", "begin:0:5", "update:0:12", "end"]);
    expect(plugin.endSelection).toHaveBeenCalledWith("doc");
  });
});

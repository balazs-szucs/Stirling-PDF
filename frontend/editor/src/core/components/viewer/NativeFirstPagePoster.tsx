import type { ReactElement } from "react";

/**
 * Stub: web builds paint the first page through the engine worker, so there is
 * nothing to show ahead of it. The desktop build shadows this with a poster
 * rendered natively on drop.
 */
export function NativeFirstPagePoster(_props: {
  filePath?: string | null;
}): ReactElement | null {
  return null;
}

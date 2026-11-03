import { useEffect, useRef } from "react";
import { useDocumentEditSession } from "@editor/contexts/documentEdit/DocumentEditSessionContext";

/**
 * Watches the edit revision to surface the Annotate panel when an annotation is
 * edited on the canvas. Subscribing from a leaf prevents keystrokes or minor
 * state updates from re-rendering the viewer itself.
 */
export function AnnotationDirtyWatcher({ onDirty }: { onDirty: () => void }) {
  const { workingRevision } = useDocumentEditSession();
  const lastSeenRevisionRef = useRef(0);

  useEffect(() => {
    if (
      workingRevision > 0 &&
      workingRevision !== lastSeenRevisionRef.current
    ) {
      lastSeenRevisionRef.current = workingRevision;
      onDirty();
    } else if (workingRevision === 0) {
      lastSeenRevisionRef.current = 0;
    }
  }, [workingRevision, onDirty]);

  return null;
}

import { useEffect, useRef } from "react";
import { useNavigationState } from "@app/contexts/NavigationContext";
import { useViewer } from "@app/contexts/ViewerContext";

/**
 * Hook that automatically stops read-aloud when navigating away from the viewer.
 * Monitors: workbench changes and active file changes.
 */
export function useStopReadAloudOnNavigation(
  isReadingAloud: boolean,
  onStop: () => void,
) {
  const { workbench } = useNavigationState();
  const viewer = useViewer();

  const previousStateRef = useRef({
    workbench,
    activeFileId: viewer.activeFileId,
    activeFileIndex: viewer.activeFileIndex,
  });

  // Monitor workbench and file changes
  useEffect(() => {
    // Stop on workbench change
    if (isReadingAloud && previousStateRef.current.workbench !== workbench) {
      onStop();
      previousStateRef.current.workbench = workbench;
      return;
    }

    // Stop on active file change (by index or file identity)
    if (
      isReadingAloud &&
      (previousStateRef.current.activeFileIndex !== viewer.activeFileIndex ||
        previousStateRef.current.activeFileId !== viewer.activeFileId)
    ) {
      onStop();
      previousStateRef.current.activeFileIndex = viewer.activeFileIndex;
      previousStateRef.current.activeFileId = viewer.activeFileId;
      return;
    }

    previousStateRef.current.workbench = workbench;
    previousStateRef.current.activeFileId = viewer.activeFileId;
    previousStateRef.current.activeFileIndex = viewer.activeFileIndex;
  }, [
    workbench,
    viewer.activeFileIndex,
    viewer.activeFileId,
    isReadingAloud,
    onStop,
  ]);

  // Stop on page unload (F5, navigation, close)
  useEffect(() => {
    if (!isReadingAloud) return;

    const handleBeforeUnload = () => {
      onStop();
    };

    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [isReadingAloud, onStop]);
}

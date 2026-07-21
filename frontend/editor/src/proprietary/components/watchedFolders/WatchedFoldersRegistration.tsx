import { useEffect, useRef, lazy, Suspense } from "react";
import { useTranslation } from "react-i18next";
import { useToolWorkflow } from "@app/contexts/ToolWorkflowContext";
import { LoadingFallback } from "@app/components/shared/LoadingFallback";
import { seedDefaultFolders } from "@app/data/watchedFolderPresets";
import { useWatchedFolderUrlSync } from "@app/hooks/useWatchedFolderUrlSync";

export const WATCHED_FOLDER_VIEW_ID = "watchedFolder";
export const WATCHED_FOLDER_WORKBENCH_ID = "custom:watchedFolder" as const;

const LazyWatchedFolderWorkbenchView = lazy(() =>
  import("@app/components/watchedFolders/WatchedFolderWorkbenchView").then(
    (m) => ({ default: m.WatchedFolderWorkbenchView }),
  ),
);

function WatchedFolderWorkbenchViewWrapper(props: {
  data: {
    folderId: string | null;
    pendingFileId?: string;
    pendingFileIds?: string[];
  };
}) {
  return (
    <Suspense fallback={<LoadingFallback />}>
      <LazyWatchedFolderWorkbenchView {...props} />
    </Suspense>
  );
}

export default function WatchedFoldersRegistration() {
  const { t } = useTranslation();
  const {
    registerCustomWorkbenchView,
    unregisterCustomWorkbenchView,
    clearCustomWorkbenchViewData,
  } = useToolWorkflow();
  useWatchedFolderUrlSync();

  // Keep refs to latest cleanup callbacks so the registration effect doesn't
  // re-run (and tear down) when unregisterCustomWorkbenchView changes identity
  // due to NavigationContext re-renders.
  const unregisterRef = useRef(unregisterCustomWorkbenchView);
  const clearRef = useRef(clearCustomWorkbenchViewData);
  useEffect(() => {
    unregisterRef.current = unregisterCustomWorkbenchView;
  });
  useEffect(() => {
    clearRef.current = clearCustomWorkbenchViewData;
  });

  useEffect(() => {
    seedDefaultFolders();
  }, []);

  useEffect(() => {
    registerCustomWorkbenchView({
      id: WATCHED_FOLDER_VIEW_ID,
      workbenchId: WATCHED_FOLDER_WORKBENCH_ID,
      label: t("watchedFolders.sidebarTitle", "Watched Folders"),
      component: WatchedFolderWorkbenchViewWrapper,
      // Show the standard workbench bar (view switcher) so users can navigate
      // back to Viewer / Active Files instead of being stranded in this view.
      hideTopControls: false,
      hideToolPanel: true,
    });
    return () => {
      clearRef.current(WATCHED_FOLDER_VIEW_ID);
      unregisterRef.current(WATCHED_FOLDER_VIEW_ID);
    };
  }, [registerCustomWorkbenchView]);

  return null;
}

import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";
import { ActionIcon } from "@editor/ui/ActionIcon";
import { Tooltip } from "@editor/components/shared/Tooltip";
import { ViewerContext } from "@editor/contexts/ViewerContext";
import { useSignature } from "@editor/contexts/SignatureContext";
import {
  useNavigationState,
  useNavigationGuard,
  useNavigationActions,
} from "@editor/contexts/NavigationContext";
import { useToolWorkflow } from "@editor/contexts/ToolWorkflowContext";
import {
  useRedactionMode,
  useRedaction,
} from "@editor/contexts/RedactionContext";
import {
  defaultParameters,
  RedactParameters,
} from "@editor/hooks/tools/redact/useRedactParameters";
import { RedactionMode } from "@embedpdf/plugin-redaction";

import { Icon } from "@editor/ui/Icon";
interface ViewerAnnotationControlsProps {
  currentView: string;
  disabled?: boolean;
}

export default function ViewerAnnotationControls({
  currentView,
  disabled = false,
}: ViewerAnnotationControlsProps) {
  const { t } = useTranslation();
  const { setLeftPanelView } = useToolWorkflow();

  // Viewer context for PDF controls - safely handle when not available
  const viewerContext = React.useContext(ViewerContext);

  // Signature context for accessing drawing API
  const { isPlacementMode } = useSignature();

  // Check if we're in sign mode or redaction mode
  const { selectedTool, hasUnsavedChanges } = useNavigationState();
  const { actions: navActions } = useNavigationActions();
  const isSignMode = selectedTool === "sign";
  const isRedactMode = selectedTool === "redact";

  // Get redaction pending state and navigation guard
  const { pendingCount } = useRedactionMode();
  const { requestNavigation } = useNavigationGuard();
  const {
    setRedactionMode,
    activateRedact,
    deactivateRedact,
    setRedactionConfig,
    redactionsApplied,
    redactionApiRef,
    setActiveType,
  } = useRedaction();

  // Check if we're in any annotation tool that should disable the toggle
  const isInAnnotationTool =
    selectedTool === "annotate" ||
    selectedTool === "sign" ||
    selectedTool === "addImage" ||
    selectedTool === "addText";

  // Check if we're on annotate tool to highlight the button
  const isAnnotateActive = selectedTool === "annotate";
  const annotationsHidden = viewerContext
    ? !viewerContext.isAnnotationsVisible
    : false;

  const exitRedactionMode = useCallback(() => {
    navActions.setToolAndWorkbench(null, "viewer");
    setLeftPanelView("toolPicker");
    setRedactionMode(false);
    setActiveType(null);
    deactivateRedact();
  }, [
    navActions,
    setLeftPanelView,
    setRedactionMode,
    setActiveType,
    deactivateRedact,
  ]);

  // Handle redaction mode toggle
  const handleRedactionToggle = async () => {
    if (isRedactMode) {
      if (pendingCount > 0 || redactionsApplied) {
        requestNavigation(exitRedactionMode);
      } else {
        exitRedactionMode();
      }
    } else {
      const enterRedactionMode = () => {
        const manualConfig: RedactParameters = {
          ...defaultParameters,
          mode: "manual",
        };
        setRedactionConfig(manualConfig);

        navActions.setToolAndWorkbench("redact", "viewer");
        setLeftPanelView("toolContent");
        setRedactionMode(true);
        setTimeout(() => {
          const currentType = redactionApiRef.current?.getActiveType?.();
          if (currentType !== RedactionMode.Redact) {
            activateRedact();
          }
        }, 200);
      };

      if (hasUnsavedChanges) {
        requestNavigation(enterRedactionMode);
      } else {
        enterRedactionMode();
      }
    }
  };

  const handleToggleAnnotationsVisibility = useCallback(() => {
    viewerContext?.toggleAnnotationsVisibility();
  }, [viewerContext]);

  // NOTE: This early return is placed AFTER all hooks to satisfy React's rules of hooks
  if (isSignMode) {
    return null;
  }

  return (
    <>
      <Tooltip
        content={
          isRedactMode
            ? t("workbenchBar.exitRedaction", "Exit Redaction Mode")
            : t("workbenchBar.redact", "Redact")
        }
        position="bottom"
        offset={16}
        arrow
        portalTarget={document.body}
      >
        <ActionIcon
          variant={isRedactMode ? "primary" : "tertiary"}
          className="workbench-bar-action-icon"
          onClick={handleRedactionToggle}
          disabled={disabled || currentView !== "viewer"}
          aria-label={
            isRedactMode
              ? t("workbenchBar.exitRedaction", "Exit Redaction Mode")
              : t("workbenchBar.redact", "Redact")
          }
        >
          <Icon name="file-x" size="1.25rem" />
        </ActionIcon>
      </Tooltip>

      <Tooltip
        content={t(
          "workbenchBar.toggleAnnotations",
          "Toggle Annotations Visibility",
        )}
        position="bottom"
        offset={16}
        arrow
        portalTarget={document.body}
      >
        <ActionIcon
          variant={annotationsHidden ? "primary" : "tertiary"}
          className="workbench-bar-action-icon"
          onClick={handleToggleAnnotationsVisibility}
          disabled={
            disabled ||
            currentView !== "viewer" ||
            (isInAnnotationTool && !isAnnotateActive) ||
            isPlacementMode
          }
          data-active={annotationsHidden ? "true" : undefined}
          aria-pressed={annotationsHidden}
          aria-label={t(
            "workbenchBar.toggleAnnotations",
            "Toggle Annotations Visibility",
          )}
        >
          <Icon
            name={viewerContext?.isAnnotationsVisible ? "eye" : "eye-off"}
            size="1.25rem"
          />
        </ActionIcon>
      </Tooltip>
    </>
  );
}

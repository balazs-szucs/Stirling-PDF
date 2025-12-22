import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useEndpointEnabled } from "@app/hooks/useEndpointConfig";
import { useFileState, useFileSelection } from "@app/contexts/FileContext";

import { createToolFlow } from "@app/components/tools/shared/createToolFlow";

import VectorConvertSettings from "@app/components/tools/convert/VectorConvertSettings";

import { useVectorConvertParameters } from "@app/hooks/tools/convert/useVectorConvertParameters";
import { useVectorConvertOperation } from "@app/hooks/tools/convert/useVectorConvertOperation";
import { BaseToolProps, ToolComponent } from "@app/types/tool";

const VectorConvert = ({ onPreviewFile, onComplete, onError }: BaseToolProps) => {
  const { t } = useTranslation();
  const { selectors } = useFileState();
  const activeFiles = selectors.getFiles();
  const { selectedFiles } = useFileSelection();
  const scrollContainerRef = useRef<HTMLDivElement>(null);

  const convertParams = useVectorConvertParameters();
  const convertOperation = useVectorConvertOperation();

  const { enabled: endpointEnabled, loading: endpointLoading } = useEndpointEnabled(convertParams.getEndpointName());

  const skipNextSelectionResetRef = useRef(false);
  const previousSelectionRef = useRef<string>('');

  const scrollToBottom = () => {
    if (scrollContainerRef.current) {
      scrollContainerRef.current.scrollTo({
        top: scrollContainerRef.current.scrollHeight,
        behavior: "smooth",
      });
    }
  };

  const hasFiles = selectedFiles.length > 0;
  const hasResults = convertOperation.files.length > 0 || convertOperation.downloadUrl !== null;
  const settingsCollapsed = hasResults;

  useEffect(() => {
    if (hasResults) {
      skipNextSelectionResetRef.current = true;
    }
  }, [hasResults]);

  useEffect(() => {
    const currentSelection = selectedFiles.map(f => f.fileId).sort().join(',');

    if (currentSelection === previousSelectionRef.current) return; // No change

    if (skipNextSelectionResetRef.current) {
      skipNextSelectionResetRef.current = false;
      previousSelectionRef.current = currentSelection;
      return;
    }

    if (selectedFiles.length > 0) {
      previousSelectionRef.current = currentSelection;
      convertParams.analyzeFileTypes(selectedFiles);
      if (hasResults) {
        convertOperation.resetResults();
        onPreviewFile?.(null);
      }
    } else {
      previousSelectionRef.current = '';
      if (activeFiles.length === 0) {
        convertParams.resetParameters();
      }
    }
  }, [selectedFiles]);

  useEffect(() => {
    if (!convertOperation.isLoading && !skipNextSelectionResetRef.current) {
      convertOperation.resetResults();
      onPreviewFile?.(null);
    }
  }, [convertParams.parameters.fromExtension, convertParams.parameters.toExtension]);

  useEffect(() => {
    if (hasFiles) {
      setTimeout(scrollToBottom, 100);
    }
  }, [hasFiles]);

  useEffect(() => {
    if (hasResults) {
      setTimeout(scrollToBottom, 100);
    }
  }, [hasResults]);

  const handleConvert = async () => {
    try {
      await convertOperation.executeOperation(convertParams.parameters, selectedFiles);
      if (convertOperation.files && onComplete) {
        onComplete(convertOperation.files);
      }
    } catch (error) {
      if (onError) {
        onError(error instanceof Error ? error.message : "Convert operation failed");
      }
    }
  };

  const handleThumbnailClick = (file: File) => {
    onPreviewFile?.(file);
    sessionStorage.setItem("previousMode", "vector-convert");
  };

  const handleSettingsReset = () => {
    skipNextSelectionResetRef.current = false;
    convertOperation.resetResults();
    onPreviewFile?.(null);
  };

  const handleUndo = async () => {
    await convertOperation.undoOperation();
    onPreviewFile?.(null);
  };

  return createToolFlow({
    files: {
      selectedFiles,
      isCollapsed: hasResults,
    },
    steps: [
      {
        title: t("vectorConvert.settings", "Settings"),
        isCollapsed: settingsCollapsed,
        onCollapsedClick: settingsCollapsed ? handleSettingsReset : undefined,
        content: (
          <VectorConvertSettings
            parameters={convertParams.parameters}
            onParameterChange={convertParams.updateParameter}
            disabled={endpointLoading}
          />
        ),
      },
    ],
    executeButton: {
      text: t("vectorConvert.convertFiles", "Convert Files"),
      loadingText: t("vectorConvert.converting", "Converting..."),
      onClick: handleConvert,
      isVisible: !hasResults,
      disabled: !convertParams.validateParameters() || !hasFiles || !endpointEnabled,
      testId: "vector-convert-button",
    },
    review: {
      isVisible: hasResults,
      operation: convertOperation,
      title: t("vectorConvert.conversionResults", "Conversion Results"),
      onFileClick: handleThumbnailClick,
      onUndo: handleUndo,
      testId: "vector-conversion-results",
    },
  });
};

VectorConvert.tool = () => useVectorConvertOperation;

export default VectorConvert as ToolComponent;

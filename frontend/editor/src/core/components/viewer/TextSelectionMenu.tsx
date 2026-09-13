import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { Popover, Stack, TextInput, Tooltip } from "@mantine/core";
import { ActionIcon } from "@app/ui/ActionIcon";
import { Button } from "@app/ui/Button";
import { LocalIcon } from "@app/components/shared/LocalIcon";
import type { SelectionSelectionMenuProps } from "@embedpdf/plugin-selection/react";
import { useSelectionCapability } from "@embedpdf/plugin-selection/react";
import { useAnnotation } from "@embedpdf/plugin-annotation/react";
import { RedactionMode } from "@embedpdf/plugin-redaction";
import { useRedactionCapability } from "@embedpdf/plugin-redaction/react";
import {
  PdfActionType,
  PdfAnnotationSubtype,
  PdfBlendMode,
} from "@embedpdf/models";
import { useActiveDocumentId } from "@app/components/viewer/useActiveDocumentId";
import { useToolWorkflow } from "@app/contexts/ToolWorkflowContext";
import { useRedaction, useRedactionMode } from "@app/contexts/RedactionContext";
import { useNavigationActions } from "@app/contexts/NavigationContext";
import {
  defaultParameters,
  type RedactParameters,
} from "@app/hooks/tools/redact/useRedactParameters";
import { alert } from "@app/components/toast";

function SquigglyIcon() {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
      style={{ display: "block" }}
    >
      <path
        d="M4 15.5c1.2-1.2 2.3-1.2 3.5 0s2.3 1.2 3.5 0 2.3-1.2 3.5 0 2.3 1.2 3.5 0 2.3-1.2 2 0"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        fill="none"
      />
    </svg>
  );
}

export type TextSelectionMenuProps = SelectionSelectionMenuProps & {
  documentId?: string;
};

/**
 * Floating actions for a text selection in the viewer. Selecting text is the
 * entry point for markup and redaction, so the menu owns turning the current
 * selection into annotations (via the annotation plugin) or pending
 * redactions, rather than requiring the user to arm a tool first.
 */
export function TextSelectionMenu(props: TextSelectionMenuProps) {
  const contextDocId = useActiveDocumentId();
  const documentId = props.documentId ?? contextDocId;

  if (!documentId) {
    return null;
  }

  return <TextSelectionMenuInner {...props} documentId={documentId} />;
}

function TextSelectionMenuInner({
  selected,
  menuWrapperProps,
  placement,
  documentId,
}: TextSelectionMenuProps & { documentId: string }) {
  const { t } = useTranslation();
  const { provides: selection } = useSelectionCapability();
  const { provides: annotationProvides } = useAnnotation(documentId);
  const { provides: redactionProvides } = useRedactionCapability();

  const { handleToolSelectForced, setSidebarsVisible, setLeftPanelView } =
    useToolWorkflow();
  const {
    setRedactionMode,
    activateRedact,
    setRedactionConfig,
    redactionApiRef,
    isBridgeReady,
  } = useRedaction();
  const { isRedacting } = useRedactionMode();
  const { actions: navActions } = useNavigationActions();

  const wrapperRef = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState<{
    top: number;
    left: number;
  } | null>(null);

  const [linkPopoverOpen, setLinkPopoverOpen] = useState(false);
  const [linkUrl, setLinkUrl] = useState("");
  const [pendingRedactActivation, setPendingRedactActivation] = useState(false);

  // The redact tool bridge mounts after its panel opens, so activation waits
  // for bridge readiness instead of racing a fixed timeout.
  useEffect(() => {
    if (!pendingRedactActivation || !isBridgeReady) return;
    setPendingRedactActivation(false);
    const currentType = redactionApiRef?.current?.getActiveType?.();
    if (currentType !== RedactionMode.Redact) {
      activateRedact?.();
    }
  }, [pendingRedactActivation, isBridgeReady, redactionApiRef, activateRedact]);

  const setRef = useCallback(
    (node: HTMLDivElement | null) => {
      wrapperRef.current = node;
      menuWrapperProps?.ref?.(node);
    },
    [menuWrapperProps],
  );

  const showAbove = placement?.suggestTop ?? true;

  useEffect(() => {
    if (!selected || !wrapperRef.current) {
      setPosition(null);
      return;
    }
    const update = () => {
      const wrapper = wrapperRef.current;
      if (!wrapper) return;
      const r = wrapper.getBoundingClientRect();
      setPosition({
        top: showAbove ? r.top - 8 : r.bottom + 8,
        left: r.left + r.width / 2,
      });
    };
    update();
    window.addEventListener("scroll", update, true);
    window.addEventListener("resize", update);
    return () => {
      window.removeEventListener("scroll", update, true);
      window.removeEventListener("resize", update);
    };
  }, [selected, showAbove]);

  const handleCopy = useCallback(() => {
    selection?.copyToClipboard(documentId);
    selection?.clear(documentId);
    alert({
      alertType: "neutral",
      title: t("common.copied", "Copied to clipboard"),
      durationMs: 2000,
    });
  }, [selection, documentId, t]);

  const openAnnotateUi = useCallback(() => {
    handleToolSelectForced?.("annotate");
    setSidebarsVisible?.(true);
    setLeftPanelView?.("toolContent");
  }, [handleToolSelectForced, setSidebarsVisible, setLeftPanelView]);

  const createMarkupAnnotation = useCallback(
    (
      subtype:
        | PdfAnnotationSubtype.HIGHLIGHT
        | PdfAnnotationSubtype.STRIKEOUT
        | PdfAnnotationSubtype.UNDERLINE
        | PdfAnnotationSubtype.SQUIGGLY,
      color: string,
      blendMode?: PdfBlendMode,
    ) => {
      const selections = selection?.getFormattedSelection(documentId) ?? [];
      if (!selections.length) return;

      const apply = (text?: string) => {
        for (const sel of selections) {
          annotationProvides?.createAnnotation(sel.pageIndex, {
            type: subtype,
            strokeColor: color,
            color,
            opacity: 1,
            ...(blendMode !== undefined ? { blendMode } : {}),
            rect: sel.rect,
            segmentRects: sel.segmentRects,
            pageIndex: sel.pageIndex,
            created: new Date(),
            id: crypto.randomUUID(),
            ...(text ? { custom: { text } } : {}),
          });
        }
        selection?.clear(documentId);
        navActions.setHasUnsavedChanges(true);
        openAnnotateUi();
      };

      const selTask = selection?.getSelectedText(documentId);
      if (selTask) {
        selTask.wait(
          (texts) => apply(texts.join("\n")),
          () => apply(),
        );
      } else {
        apply();
      }
    },
    [documentId, selection, annotationProvides, navActions, openAnnotateUi],
  );

  const handleHighlight = useCallback(() => {
    createMarkupAnnotation(
      PdfAnnotationSubtype.HIGHLIGHT,
      "#FFCD45",
      PdfBlendMode.Multiply,
    );
  }, [createMarkupAnnotation]);

  const handleStrikeout = useCallback(() => {
    createMarkupAnnotation(PdfAnnotationSubtype.STRIKEOUT, "#E44234");
  }, [createMarkupAnnotation]);

  const handleUnderline = useCallback(() => {
    createMarkupAnnotation(PdfAnnotationSubtype.UNDERLINE, "#E44234");
  }, [createMarkupAnnotation]);

  const handleSquiggly = useCallback(() => {
    createMarkupAnnotation(PdfAnnotationSubtype.SQUIGGLY, "#E44234");
  }, [createMarkupAnnotation]);

  const handleAddLink = useCallback(
    (url: string) => {
      const uri = url.trim();
      if (!uri) return;
      const selections = selection?.getFormattedSelection(documentId) ?? [];
      if (!selections.length) return;

      for (const sel of selections) {
        annotationProvides?.createAnnotation(sel.pageIndex, {
          type: PdfAnnotationSubtype.LINK,
          id: crypto.randomUUID(),
          pageIndex: sel.pageIndex,
          rect: sel.rect,
          target: { type: "action", action: { type: PdfActionType.URI, uri } },
          created: new Date(),
        });
      }
      selection?.clear(documentId);
      setLinkPopoverOpen(false);
      setLinkUrl("");
      navActions.setHasUnsavedChanges(true);
      openAnnotateUi();
    },
    [documentId, selection, annotationProvides, navActions, openAnnotateUi],
  );

  const handleRedact = useCallback(() => {
    const selections = selection?.getFormattedSelection(documentId) ?? [];
    if (!selections.length) return;

    // Let the redaction plugin turn the current selection into pending marks:
    // it owns the annotation-mode conversion, text capture and selection
    // clearing, so building REDACT annotations by hand here would race it.
    redactionProvides?.forDocument(documentId).queueCurrentSelectionAsPending();

    const manualConfig: RedactParameters = {
      ...defaultParameters,
      mode: "manual",
    };
    setRedactionConfig?.(manualConfig);
    setRedactionMode?.(true);
    navActions.setHasUnsavedChanges(true);
    navActions.setToolAndWorkbench("redact", "viewer");
    setSidebarsVisible?.(true);
    setLeftPanelView?.("toolContent");
    setPendingRedactActivation(true);
  }, [
    documentId,
    selection,
    redactionProvides,
    setRedactionConfig,
    setRedactionMode,
    navActions,
    setSidebarsVisible,
    setLeftPanelView,
  ]);

  const portalContent =
    position &&
    // In redact mode the redaction plugin converts selections into pending
    // marks itself; showing this menu would duplicate actions and its portal
    // can swallow the second click of a double-click gesture.
    !isRedacting &&
    createPortal(
      <div
        data-text-selection-menu
        style={{
          position: "fixed",
          top: position.top,
          left: position.left,
          transform: `translate(-50%, ${showAbove ? "-100%" : "0"})`,
          zIndex: 10000,
          pointerEvents: "auto",
          display: "flex",
          alignItems: "center",
          gap: 4,
          padding: "4px 6px",
          borderRadius: 8,
          backgroundColor: "var(--mantine-color-body)",
          border: "1px solid var(--mantine-color-default-border)",
          boxShadow: "0 2px 12px rgba(0, 0, 0, 0.25)",
        }}
        onMouseDown={(e) => e.preventDefault()}
      >
        <Tooltip label={t("viewer.copyText", "Copy")} withArrow>
          <ActionIcon
            variant="secondary"
            accent="neutral"
            size="md"
            onClick={handleCopy}
            aria-label={t("viewer.copyText", "Copy")}
          >
            <LocalIcon icon="content-copy" width="1.1rem" height="1.1rem" />
          </ActionIcon>
        </Tooltip>

        <Tooltip label={t("annotation.highlight", "Highlight")} withArrow>
          <ActionIcon
            variant="secondary"
            accent="neutral"
            size="md"
            onClick={handleHighlight}
            aria-label={t("annotation.highlight", "Highlight")}
          >
            <LocalIcon icon="highlight" width="1.2rem" height="1.2rem" />
          </ActionIcon>
        </Tooltip>

        <Tooltip label={t("annotation.strikeout", "Strikeout")} withArrow>
          <ActionIcon
            variant="secondary"
            accent="neutral"
            size="md"
            onClick={handleStrikeout}
            aria-label={t("annotation.strikeout", "Strikeout")}
          >
            <LocalIcon icon="strikethrough-s" width="1.2rem" height="1.2rem" />
          </ActionIcon>
        </Tooltip>

        <Tooltip label={t("annotation.underline", "Underline")} withArrow>
          <ActionIcon
            variant="secondary"
            accent="neutral"
            size="md"
            onClick={handleUnderline}
            aria-label={t("annotation.underline", "Underline")}
          >
            <LocalIcon
              icon="format-underlined"
              width="1.2rem"
              height="1.2rem"
            />
          </ActionIcon>
        </Tooltip>

        <Tooltip label={t("annotation.squiggly", "Squiggly")} withArrow>
          <ActionIcon
            variant="secondary"
            accent="neutral"
            size="md"
            onClick={handleSquiggly}
            aria-label={t("annotation.squiggly", "Squiggly")}
          >
            <SquigglyIcon />
          </ActionIcon>
        </Tooltip>

        <Popover
          opened={linkPopoverOpen}
          onChange={setLinkPopoverOpen}
          position={showAbove ? "top" : "bottom"}
          withArrow
          shadow="md"
          transitionProps={{ duration: 0 }}
        >
          <Popover.Target>
            <ActionIcon
              variant="secondary"
              accent="neutral"
              size="md"
              onClick={() => setLinkPopoverOpen((open) => !open)}
              aria-label={t("viewer.comments.addLink", "Add link")}
            >
              <LocalIcon icon="link" width="1.1rem" height="1.1rem" />
            </ActionIcon>
          </Popover.Target>
          <Popover.Dropdown
            onMouseDown={(e) => e.stopPropagation()}
            style={{
              backgroundColor: "var(--mantine-color-body)",
              borderColor: "var(--mantine-color-default-border)",
            }}
          >
            <Stack gap="xs" style={{ minWidth: 220 }}>
              <TextInput
                placeholder="https://..."
                value={linkUrl}
                onChange={(e) => setLinkUrl(e.currentTarget.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && linkUrl.trim()) {
                    handleAddLink(linkUrl);
                  }
                }}
                size="xs"
                autoFocus
              />
              <Button
                size="sm"
                disabled={!linkUrl.trim()}
                onClick={() => handleAddLink(linkUrl)}
              >
                {t("viewer.comments.addLink", "Add link")}
              </Button>
            </Stack>
          </Popover.Dropdown>
        </Popover>

        <Tooltip label={t("workbenchBar.redact", "Redact")} withArrow>
          <ActionIcon
            variant="secondary"
            accent="neutral"
            size="md"
            onClick={handleRedact}
            aria-label={t("workbenchBar.redact", "Redact")}
          >
            <LocalIcon
              icon="scan-delete-rounded"
              width="1.2rem"
              height="1.2rem"
            />
          </ActionIcon>
        </Tooltip>
      </div>,
      document.body,
    );

  return (
    <>
      <div ref={setRef} style={menuWrapperProps?.style} />
      {portalContent}
    </>
  );
}

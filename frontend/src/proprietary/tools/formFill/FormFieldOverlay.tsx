/**
 * FormFieldOverlay — Renders interactive HTML form widgets on top of a PDF page.
 *
 * This layer is placed inside the renderPage callback of the EmbedPDF Scroller,
 * similar to how AnnotationLayer, RedactionLayer, and LinkLayer work.
 *
 * It reads the form field coordinates (in un-rotated CSS space, top-left origin)
 * and scales them using the document scale from EmbedPDF.
 *
 * Each widget renders an appropriate HTML input (text, checkbox, dropdown, etc.)
 * that synchronises bidirectionally with FormFillContext values.
 *
 * Coordinate handling:
 * Both providers (PdfLibFormProvider and PdfBoxFormProvider) output widget
 * coordinates in un-rotated PDF space (y-flipped to CSS upper-left origin).
 * The <Rotate> component (which wraps this overlay along with page tiles)
 * handles visual rotation via CSS transforms — same as TilingLayer,
 * AnnotationLayer, and LinkLayer.
 */
import React, { useCallback, useMemo, memo, useRef, useState } from 'react';
import { useDocumentState } from '@embedpdf/core/react';
import { useFormFill, useFieldValue } from '@proprietary/tools/formFill/FormFillContext';
import { useFormMaker } from '@proprietary/tools/formFill/FormMakerContext';
import type { FormField, FormMakerField, WidgetCoordinates } from '@proprietary/tools/formFill/types';

// ---------------------------------------------------------------------------
// Field-type colour mapping for the form maker overlay
// ---------------------------------------------------------------------------

const MAKER_FIELD_COLORS: Record<string, string> = {
  text: '#2196F3',
  checkbox: '#4CAF50',
  radio: '#FF9800',
  combobox: '#9C27B0',
  listbox: '#00BCD4',
  signature: '#F44336',
  button: '#607D8B',
};

function makerFieldColor(type: string): string {
  return MAKER_FIELD_COLORS[type] ?? '#2196F3';
}

// ---------------------------------------------------------------------------
// FormMakerDrawLayer — renders per-page draw canvas + placed maker fields
// ---------------------------------------------------------------------------

interface FormMakerDrawLayerProps {
  pageIndex: number;
  scaleX: number;
  scaleY: number;
}

interface DragState {
  fieldId: string;
  startMouseX: number;
  startMouseY: number;
  origX: number;
  origY: number;
  scaleX: number;
  scaleY: number;
}

function FormMakerDrawLayer({ pageIndex, scaleX, scaleY }: FormMakerDrawLayerProps) {
  const formMaker = useFormMaker();
  const isDrawingRef = useRef(false);
  const dragRef = useRef<DragState | null>(null);
  const [draggingFieldId, setDraggingFieldId] = useState<string | null>(null);

  if (!formMaker) return null;

  const { state, startDrawing, updateDrawing, finishDrawing, cancelDrawing, selectField, updateField } = formMaker;
  const { isDrawingMode, activeDrawing, fields, selectedFieldId } = state;

  const makerFieldsOnPage: FormMakerField[] = fields.filter((f) => f.pageIndex === pageIndex);

  // ---- Draw mode: blank-area mouse handlers ----

  const handleMouseDown = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!isDrawingMode) return;
    e.preventDefault();
    e.stopPropagation();
    const rect = e.currentTarget.getBoundingClientRect();
    const x = (e.clientX - rect.left) / scaleX;
    const y = (e.clientY - rect.top) / scaleY;
    isDrawingRef.current = true;
    startDrawing(pageIndex, x, y);
  };

  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!isDrawingMode || !isDrawingRef.current) return;
    e.preventDefault();
    const rect = e.currentTarget.getBoundingClientRect();
    const x = (e.clientX - rect.left) / scaleX;
    const y = (e.clientY - rect.top) / scaleY;
    updateDrawing(x, y);
  };

  const handleMouseUp = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!isDrawingMode || !isDrawingRef.current) return;
    e.preventDefault();
    e.stopPropagation();
    isDrawingRef.current = false;
    finishDrawing();
  };

  const handleMouseLeave = () => {
    if (isDrawingRef.current) {
      isDrawingRef.current = false;
      cancelDrawing();
    }
  };

  // ---- Drag-to-move: field box mouse handlers ----

  const handleFieldMouseDown = (e: React.MouseEvent, field: FormMakerField) => {
    e.stopPropagation();
    selectField(field.id);

    // In draw mode clicks just select; dragging is disabled
    if (isDrawingMode) return;

    e.preventDefault();
    const drag: DragState = {
      fieldId: field.id,
      startMouseX: e.clientX,
      startMouseY: e.clientY,
      origX: field.x,
      origY: field.y,
      scaleX,
      scaleY,
    };
    dragRef.current = drag;
    setDraggingFieldId(field.id);

    const onMouseMove = (mv: MouseEvent) => {
      const d = dragRef.current;
      if (!d) return;
      const dx = (mv.clientX - d.startMouseX) / d.scaleX;
      const dy = (mv.clientY - d.startMouseY) / d.scaleY;
      updateField(d.fieldId, {
        x: Math.max(0, d.origX + dx),
        y: Math.max(0, d.origY + dy),
      });
    };

    const onMouseUp = () => {
      dragRef.current = null;
      setDraggingFieldId(null);
      window.removeEventListener('mousemove', onMouseMove);
      window.removeEventListener('mouseup', onMouseUp);
    };

    window.addEventListener('mousemove', onMouseMove);
    window.addEventListener('mouseup', onMouseUp);
  };

  // Draw-mode ghost rectangle
  const ghostRect =
    activeDrawing && activeDrawing.pageIndex === pageIndex
      ? {
          left: Math.min(activeDrawing.startX, activeDrawing.currentX) * scaleX,
          top: Math.min(activeDrawing.startY, activeDrawing.currentY) * scaleY,
          width: Math.abs(activeDrawing.currentX - activeDrawing.startX) * scaleX,
          height: Math.abs(activeDrawing.currentY - activeDrawing.startY) * scaleY,
        }
      : null;

  return (
    <div
      style={{
        position: 'absolute',
        inset: 0,
        cursor: isDrawingMode ? 'crosshair' : 'default',
        // Capture events only when drawing; field boxes handle their own events otherwise
        pointerEvents: isDrawingMode ? 'all' : 'none',
        zIndex: 15,
      }}
      onMouseDown={handleMouseDown}
      onMouseMove={handleMouseMove}
      onMouseUp={handleMouseUp}
      onMouseLeave={handleMouseLeave}
    >
      {/* In-progress drawing rectangle */}
      {ghostRect && (
        <div
          style={{
            position: 'absolute',
            left: ghostRect.left,
            top: ghostRect.top,
            width: ghostRect.width,
            height: ghostRect.height,
            border: '2px dashed #2196F3',
            background: 'rgba(33, 150, 243, 0.08)',
            pointerEvents: 'none',
            boxSizing: 'border-box',
          }}
        />
      )}

      {/* Already-placed maker fields on this page */}
      {makerFieldsOnPage.map((field) => {
        const isSelected = field.id === selectedFieldId;
        const isBeingDragged = field.id === draggingFieldId;
        const color = makerFieldColor(field.type);
        return (
          <div
            key={field.id}
            style={{
              position: 'absolute',
              left: field.x * scaleX,
              top: field.y * scaleY,
              width: field.width * scaleX,
              height: field.height * scaleY,
              border: `2px solid ${color}`,
              background: isSelected ? `${color}22` : `${color}0F`,
              boxSizing: 'border-box',
              cursor: isDrawingMode ? 'pointer' : (isBeingDragged ? 'grabbing' : 'grab'),
              pointerEvents: 'all',
              zIndex: 16,
              display: 'flex',
              alignItems: 'flex-start',
              justifyContent: 'flex-start',
              overflow: 'hidden',
              boxShadow: isSelected
                ? `0 0 0 2px ${color}66`
                : isBeingDragged
                ? `0 4px 12px rgba(0,0,0,0.2)`
                : 'none',
              userSelect: 'none',
              opacity: isBeingDragged ? 0.85 : 1,
            }}
            onMouseDown={(e) => handleFieldMouseDown(e, field)}
            title={`${field.type}: ${field.name}${isDrawingMode ? '' : ' — drag to move'}`}
          >
            <span
              style={{
                fontSize: Math.max(8, Math.min(field.height * scaleY * 0.45, 11)),
                fontWeight: 700,
                color,
                background: `${color}33`,
                padding: '0 2px',
                lineHeight: 1.2,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                maxWidth: '100%',
                textTransform: 'uppercase',
                letterSpacing: '0.02em',
                pointerEvents: 'none',
              }}
            >
              {field.name}
            </span>
          </div>
        );
      })}
    </div>
  );
}

interface WidgetInputProps {
  field: FormField;
  widget: WidgetCoordinates;
  isActive: boolean;
  error?: string;
  scaleX: number;
  scaleY: number;
  onFocus: (fieldName: string) => void;
  onChange: (fieldName: string, value: string) => void;
}

/**
 * WidgetInput subscribes to its own field value via useSyncExternalStore,
 * so it only re-renders when its specific value changes — not when ANY
 * form value in the entire document changes.
 */
function WidgetInputInner({
  field,
  widget,
  isActive,
  error,
  scaleX,
  scaleY,
  onFocus,
  onChange,
}: WidgetInputProps) {
  // Per-field value subscription — only this widget re-renders when its value changes
  const value = useFieldValue(field.name);

  // Coordinates are in visual CSS space (top-left origin).
  // Multiply by per-axis scale to get rendered pixel coordinates.
  const left = widget.x * scaleX;
  const top = widget.y * scaleY;
  const width = widget.width * scaleX;
  const height = widget.height * scaleY;

  const borderColor = error ? '#f44336' : (isActive ? '#2196F3' : 'rgba(33, 150, 243, 0.4)');
  const bgColor = error
    ? '#FFEBEE' // Red 50 (Opaque)
    : (isActive ? '#E3F2FD' : '#FFFFFF'); // Blue 50 (Opaque) : White (Opaque)

  const commonStyle: React.CSSProperties = {
    position: 'absolute',
    left,
    top,
    width,
    height,
    zIndex: 10,
    boxSizing: 'border-box',
    border: `1px solid ${borderColor}`,
    borderRadius: 1,
    background: isActive ? bgColor : 'transparent',
    transition: 'border-color 0.15s, background 0.15s, box-shadow 0.15s',
    boxShadow:
      isActive && field.type !== 'radio' && field.type !== 'checkbox'
        ? `0 0 0 2px ${error ? 'rgba(244, 67, 54, 0.25)' : 'rgba(33, 150, 243, 0.25)'}`
        : 'none',
    cursor: field.readOnly ? 'default' : 'text',
    pointerEvents: 'auto',
    display: 'flex',
    alignItems: field.multiline ? 'stretch' : 'center',
  };

  const stopPropagation = (e: React.SyntheticEvent) => {
    e.stopPropagation();
    // Also stop immediate propagation to native listeners to block non-React subscribers
    if (e.nativeEvent) {
      e.nativeEvent.stopImmediatePropagation?.();
    }
  };

  const commonProps = {
    style: commonStyle,
    onPointerDown: stopPropagation,
    onPointerUp: stopPropagation,
    onMouseDown: stopPropagation,
    onMouseUp: stopPropagation,
    onClick: stopPropagation,
    onDoubleClick: stopPropagation,
    onKeyDown: stopPropagation,
    onKeyUp: stopPropagation,
    onKeyPress: stopPropagation,
    onDragStart: stopPropagation,
    onSelect: stopPropagation,
    onContextMenu: stopPropagation,
  };

  const captureStopProps = {
    onPointerDownCapture: stopPropagation,
    onPointerUpCapture: stopPropagation,
    onMouseDownCapture: stopPropagation,
    onMouseUpCapture: stopPropagation,
    onClickCapture: stopPropagation,
    onKeyDownCapture: stopPropagation,
    onKeyUpCapture: stopPropagation,
    onKeyPressCapture: stopPropagation,
  };

  const fontSize = widget.fontSize
    ? widget.fontSize * scaleY
    : field.multiline
      ? Math.max(6, Math.min(height * 0.60, 14))
      : Math.max(6, height * 0.65);

  const inputBaseStyle: React.CSSProperties = {
    width: '100%',
    height: '100%',
    border: 'none',
    outline: 'none',
    background: 'transparent',
    padding: 0,
    paddingLeft: `${Math.max(2, 4 * scaleX)}px`,
    paddingRight: `${Math.max(2, 4 * scaleX)}px`,
    fontSize: `${fontSize}px`,
    fontFamily: 'Helvetica, Arial, sans-serif',
    color: '#000',
    boxSizing: 'border-box',
    lineHeight: 'normal',
  };

  const handleFocus = () => onFocus(field.name);

  switch (field.type) {
    case 'text':
      return (
        <div {...commonProps} title={error || field.tooltip || field.label}>
          {field.multiline ? (
            <textarea
              value={value}
              onChange={(e) => onChange(field.name, e.target.value)}
              onFocus={handleFocus}
              disabled={field.readOnly}
              placeholder={field.label}
              style={{
                ...inputBaseStyle,
                resize: 'none',
                overflow: 'auto',
                paddingTop: `${Math.max(1, 2 * scaleY)}px`,
              }}
              {...captureStopProps}
            />
          ) : (
            <input
              type="text"
              id={`${field.name}_${widget.pageIndex}_${widget.x}_${widget.y}`}
              value={value}
              onChange={(e) => onChange(field.name, e.target.value)}
              onFocus={handleFocus}
              disabled={field.readOnly}
              placeholder={field.label}
              style={inputBaseStyle}
              aria-label={field.label || field.name}
              aria-required={field.required}
              aria-invalid={!!error}
              aria-describedby={error ? `${field.name}-error` : undefined}
              {...captureStopProps}
            />
          )}
        </div>
      );

    case 'checkbox': {
      // Checkbox is checked when value is anything other than 'Off' or empty
      const isChecked = !!value && value !== 'Off';
      // When toggling on, use the widget's exportValue (e.g. 'Red', 'Blue') or fall back to 'Yes'
      const onValue = widget.exportValue || 'Yes';
      return (
        <div
          {...commonProps}
          style={{
            ...commonStyle,
            border: isActive ? commonStyle.border : '1px solid rgba(0,0,0,0.15)',
            background: isActive ? bgColor : 'transparent',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center', // Keep center for checkboxes as they are usually square hitboxes
            cursor: field.readOnly ? 'default' : 'pointer',
          }}
          title={error || field.tooltip || field.label}
          onClick={(e) => {
            if (field.readOnly) return;
            handleFocus();
            onChange(field.name, isChecked ? 'Off' : onValue);
            stopPropagation(e);
          }}
        >
          <span
            style={{
              width: '85%',
              height: '85%',
              maxWidth: height * 0.9, // Prevent it from getting too wide in rectangular boxes
              maxHeight: width * 0.9,
              fontSize: `${Math.max(10, height * 0.75)}px`,
              lineHeight: 1,
              color: isChecked ? '#2196F3' : 'transparent',
              background: '#FFF',
              border: isChecked || isActive ? '1px solid #2196F3' : '1.5px solid #666',
              borderRadius: 2,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 700,
              userSelect: 'none',
              boxShadow: isActive ? '0 0 0 2px rgba(33, 150, 243, 0.2)' : 'none',
            }}
          >
            ✓
          </span>
        </div>
      );
    }

    case 'combobox':
    case 'listbox': {
      const inputId = `${field.name}_${widget.pageIndex}_${widget.x}_${widget.y}`;

      // For multi-select, value should be an array
      // We store as comma-separated string, so parse it
      const selectValue = field.multiSelect
        ? (value ? value.split(',').map(v => v.trim()) : [])
        : value;

      const handleSelectChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
        if (field.multiSelect) {
          // For multi-select, join selected options with comma
          const selected = Array.from(e.target.selectedOptions, opt => opt.value);
          onChange(field.name, selected.join(','));
        } else {
          onChange(field.name, e.target.value);
        }
      };

      return (
        <div {...commonProps} title={error || field.tooltip || field.label}>
          <select
            id={inputId}
            value={selectValue}
            onChange={handleSelectChange}
            onFocus={handleFocus}
            disabled={field.readOnly}
            multiple={field.multiSelect}
            style={{
              ...inputBaseStyle,
              padding: 0,
              paddingLeft: 2,
              appearance: 'auto',
              WebkitAppearance: 'auto' as React.CSSProperties['WebkitAppearance'],
            }}
            aria-label={field.label || field.name}
            aria-required={field.required}
            aria-invalid={!!error}
            {...captureStopProps}
          >
            {!field.multiSelect && <option value="">— select —</option>}
            {(field.options || []).map((opt, idx) => (
              <option key={opt} value={opt}>
                {(field.displayOptions && field.displayOptions[idx]) || opt}
              </option>
            ))}
          </select>
        </div>
      );
    }

    case 'radio': {
      // Each radio widget has an exportValue set by the backend
      const optionValue = widget.exportValue || '';
      if (!optionValue) return null; // no export value, skip
      const isSelected = value === optionValue;
      return (
        <div
          {...commonProps}
          style={{
            ...commonStyle,
            border: isActive ? commonStyle.border : 'none',
            background: 'transparent',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-start', // Align to start (left) instead of center for radio buttons
            paddingLeft: Math.max(1, (height - Math.min(width, height) * 0.8) / 2), // Slight offset
            cursor: field.readOnly ? 'default' : 'pointer',
          }}
          title={error || field.tooltip || `${field.label}: ${optionValue}`}
          onClick={(e) => {
            if (field.readOnly || value === optionValue) return; // Don't deselect radio buttons
            handleFocus();
            onChange(field.name, optionValue);
            stopPropagation(e);
          }}
        >
          <span
            style={{
              width: Math.min(width, height) * 0.8,
              height: Math.min(width, height) * 0.8,
              borderRadius: '50%',
              border: `1.5px solid ${isSelected || isActive ? '#2196F3' : '#666'}`,
              background: isSelected ? '#2196F3' : '#FFF',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: isSelected ? 'inset 0 0 0 2px white' : 'none',
              transition: 'background 0.15s, border-color 0.15s',
            }}
          />
        </div>
      );
    }

    case 'signature':
    case 'button':
      // Just render a highlighted area — not editable
      return (
        <div
          {...commonProps}
          style={{
            ...commonStyle,
            background: 'rgba(200,200,200,0.3)',
            border: '1px dashed #999',
            cursor: 'default',
          }}
          title={field.tooltip || `${field.type}: ${field.label}`}
          onClick={handleFocus}
        />
      );

    default:
      return (
        <div {...commonProps} title={field.tooltip || field.label}>
          <input
            type="text"
            value={value}
            onChange={(e) => onChange(field.name, e.target.value)}
            onFocus={handleFocus}
            disabled={field.readOnly}
            style={inputBaseStyle}
            {...captureStopProps}
          />
        </div>
      );
  }
}

const WidgetInput = memo(WidgetInputInner);

interface FormFieldOverlayProps {
  documentId: string;
  pageIndex: number;
  pageWidth: number;  // rendered CSS pixel width (from renderPage callback)
  pageHeight: number; // rendered CSS pixel height
  /** File identity — if provided, overlay only renders when context fields match this file */
  fileId?: string | null;
}

export function FormFieldOverlay({
  documentId,
  pageIndex,
  pageWidth,
  pageHeight,
  fileId,
}: FormFieldOverlayProps) {
  const { setValue, setActiveField, fieldsByPage, state, forFileId } = useFormFill();
  const { activeFieldName, validationErrors } = state;
  const formMaker = useFormMaker();

  // Get scale from EmbedPDF document state — same pattern as LinkLayer
  // NOTE: All hooks must be called unconditionally (before any early returns)
  const documentState = useDocumentState(documentId);

  const { scaleX, scaleY } = useMemo(() => {
    const pdfPage = documentState?.document?.pages?.[pageIndex];
    if (!pdfPage || !pdfPage.size || !pageWidth || !pageHeight) {
      const s = documentState?.scale ?? 1;
      return { scaleX: s, scaleY: s };
    }

    // pdfPage.size contains un-rotated (MediaBox) dimensions;
    // pageWidth/pageHeight from Scroller also use these un-rotated dims * scale
    return {
      scaleX: pageWidth / pdfPage.size.width,
      scaleY: pageHeight / pdfPage.size.height,
    };
  }, [documentState, pageIndex, pageWidth, pageHeight]);

  const pageFields = useMemo(
    () => fieldsByPage.get(pageIndex) || [],
    [fieldsByPage, pageIndex]
  );

  const handleFocus = useCallback(
    (fieldName: string) => setActiveField(fieldName),
    [setActiveField]
  );

  const handleChange = useCallback(
    (fieldName: string, value: string) => setValue(fieldName, value),
    [setValue]
  );

  // Whether the form maker has any content to show for this page
  const hasMakerContent = formMaker != null && (
    formMaker.state.isDrawingMode ||
    formMaker.state.fields.some((f) => f.pageIndex === pageIndex)
  );

  // Guard: don't render fields from a previous document.
  // If fileId is provided and doesn't match what the context fetched for, render nothing.
  if (fileId != null && forFileId != null && fileId !== forFileId) {
    // Still show maker layer even if fill fields are stale
    if (!hasMakerContent) return null;
    return (
      <div
        style={{ position: 'absolute', inset: 0, pointerEvents: 'none', zIndex: 5 }}
        data-form-overlay-page={pageIndex}
      >
        <FormMakerDrawLayer pageIndex={pageIndex} scaleX={scaleX} scaleY={scaleY} />
      </div>
    );
  }
  // Also guard: if fields exist but no forFileId is set (reset happened), don't render stale fields
  if (fileId != null && forFileId == null && state.fields.length > 0) {
    if (!hasMakerContent) return null;
    return (
      <div
        style={{ position: 'absolute', inset: 0, pointerEvents: 'none', zIndex: 5 }}
        data-form-overlay-page={pageIndex}
      >
        <FormMakerDrawLayer pageIndex={pageIndex} scaleX={scaleX} scaleY={scaleY} />
      </div>
    );
  }

  if (pageFields.length === 0 && !hasMakerContent) return null;

  return (
    <div
      style={{
        position: 'absolute',
        top: 0,
        left: 0,
        width: '100%',
        height: '100%',
        pointerEvents: 'none', // allow click-through except on widgets
        zIndex: 5, // above TilingLayer, below LinkLayer
      }}
      data-form-overlay-page={pageIndex}
    >
      {pageFields.map((field: FormField) =>
        (field.widgets || [])
          .filter((w: WidgetCoordinates) => w.pageIndex === pageIndex)
          .map((widget: WidgetCoordinates, widgetIdx: number) => {
            // Coordinates are in un-rotated PDF space (y-flipped to CSS TL origin).
            // The <Rotate> CSS wrapper handles visual rotation for us,
            // just like it does for TilingLayer, LinkLayer, etc.
            return (
              <WidgetInput
                key={`${field.name}-${widgetIdx}`}
                field={field}
                widget={widget}
                isActive={activeFieldName === field.name}
                error={validationErrors[field.name]}
                scaleX={scaleX}
                scaleY={scaleY}
                onFocus={handleFocus}
                onChange={handleChange}
              />
            );
          })
      )}

      {/* Form maker draw layer — only renders when form maker is active */}
      {hasMakerContent && (
        <FormMakerDrawLayer pageIndex={pageIndex} scaleX={scaleX} scaleY={scaleY} />
      )}
    </div>
  );
}

export default FormFieldOverlay;

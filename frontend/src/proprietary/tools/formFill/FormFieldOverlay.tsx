/**
 * FormFieldOverlay — Renders interactive HTML form widgets on top of a PDF page.
 *
 * This layer is placed inside the renderPage callback of the EmbedPDF Scroller,
 * similar to how AnnotationLayer, RedactionLayer, and LinkLayer work.
 *
 * It reads the form field coordinates (in PDF space, lower-left origin) and converts
 * them to CSS coordinates using the document scale from EmbedPDF, exactly like
 * LinkLayer does for link annotations.
 *
 * Each widget renders an appropriate HTML input (text, checkbox, dropdown, etc.)
 * that synchronises bidirectionally with FormFillContext values.
 */
import React, { useCallback, useMemo, memo } from 'react';
import { useDocumentState } from '@embedpdf/core/react';
import { useFormFill } from '@proprietary/tools/formFill/FormFillContext';
import type { FormField, WidgetCoordinates } from '@proprietary/tools/formFill/types';

// ─── Per-widget input component ─────────────────────────────────────────

interface WidgetInputProps {
  field: FormField;
  widget: WidgetCoordinates;
  value: string;
  isActive: boolean;
  error?: string;
  scaleX: number;
  scaleY: number;
  onFocus: (fieldName: string) => void;
  onChange: (fieldName: string, value: string) => void;
}

function WidgetInputInner({
  field,
  widget,
  value,
  isActive,
  error,
  scaleX,
  scaleY,
  onFocus,
  onChange,
}: WidgetInputProps) {
  // Coordinates are in PDF space (top-left origin relative to CropBox) from the backend.
  // Multiply by per-axis scale to get CSS coordinates.
  const left = widget.x * scaleX;
  const top = widget.y * scaleY;
  const width = widget.width * scaleX;
  const height = widget.height * scaleY;

  const borderColor = error ? '#f44336' : (isActive ? '#2196F3' : 'rgba(33, 150, 243, 0.4)');
  const bgColor = error
    ? 'rgba(244, 67, 54, 0.08)'
    : (isActive ? 'rgba(33, 150, 243, 0.08)' : 'rgba(255, 255, 255, 0.85)');

  const commonStyle: React.CSSProperties = {
    position: 'absolute',
    left,
    top,
    width,
    height,
    zIndex: 10,
    boxSizing: 'border-box',
    border: `2px solid ${borderColor}`,
    borderRadius: 2,
    background: bgColor,
    transition: 'border-color 0.15s, background 0.15s, box-shadow 0.15s',
    boxShadow: isActive
      ? `0 0 0 2px ${error ? 'rgba(244, 67, 54, 0.25)' : 'rgba(33, 150, 243, 0.25)'}`
      : 'none',
    cursor: field.readOnly ? 'default' : 'text',
    pointerEvents: 'auto',
    display: 'flex',
    alignItems: field.multiline ? 'stretch' : 'center',
  };

  // Scale font size with the widget height (using Y scale as a proxy for uniform font scaling)
  const fontSize = widget.fontSize
    ? widget.fontSize * scaleY
    : Math.max(8, Math.min(height * 0.65, 14));

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
        <div style={commonStyle} title={error || field.tooltip || field.label}>
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
          style={{
            ...commonStyle,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: field.readOnly ? 'default' : 'pointer',
          }}
          title={error || field.tooltip || field.label}
          onClick={() => {
            if (field.readOnly) return;
            handleFocus();
            onChange(field.name, isChecked ? 'Off' : onValue);
          }}
        >
          <span
            style={{
              fontSize: `${Math.max(12, height * 0.7)}px`,
              lineHeight: 1,
              color: isChecked ? '#2196F3' : 'transparent',
              fontWeight: 700,
              userSelect: 'none',
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
      return (
        <div style={commonStyle} title={error || field.tooltip || field.label}>
          <select
            id={inputId}
            value={value}
            onChange={(e) => onChange(field.name, e.target.value)}
            onFocus={handleFocus}
            disabled={field.readOnly}
            multiple={field.multiSelect}
            style={{
              ...inputBaseStyle,
              padding: 0,
              paddingLeft: 2,
              appearance: 'auto',
              WebkitAppearance: 'auto' as any,
            }}
            aria-label={field.label || field.name}
            aria-required={field.required}
            aria-invalid={!!error}
          >
            <option value="">— select —</option>
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
          style={{
            ...commonStyle,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: field.readOnly ? 'default' : 'pointer',
          }}
          title={error || field.tooltip || `${field.label}: ${optionValue}`}
          onClick={() => {
            if (field.readOnly || value === optionValue) return; // Don't deselect radio buttons
            handleFocus();
            onChange(field.name, optionValue);
          }}
        >
          <span
            style={{
              width: Math.max(8, height * 0.5),
              height: Math.max(8, height * 0.5),
              borderRadius: '50%',
              border: '2px solid #666',
              background: isSelected ? '#2196F3' : 'transparent',
              display: 'block',
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
        <div style={commonStyle} title={field.tooltip || field.label}>
          <input
            type="text"
            value={value}
            onChange={(e) => onChange(field.name, e.target.value)}
            onFocus={handleFocus}
            disabled={field.readOnly}
            style={inputBaseStyle}
          />
        </div>
      );
  }
}

// ─── Main overlay component ─────────────────────────────────────────────

const WidgetInput = memo(WidgetInputInner);

interface FormFieldOverlayProps {
  documentId: string;
  pageIndex: number;
  pageWidth: number;  // rendered CSS pixel width (from renderPage callback)
  pageHeight: number; // rendered CSS pixel height
}

export function FormFieldOverlay({
  documentId,
  pageIndex,
  pageWidth,
  pageHeight,
}: FormFieldOverlayProps) {
  const { state, setValue, setActiveField, fieldsByPage } = useFormFill();
  const { values, activeFieldName, validationErrors } = state;

  // Get scale from EmbedPDF document state — same pattern as LinkLayer
  const documentState = useDocumentState(documentId);

  // Calculate per-page scale for better alignment accuracy across different page sizes.
  // We compare the rendered page dimensions (from EmbedPDF) against the original PDF page size.
  const { scaleX, scaleY } = useMemo(() => {
    const pdfPage = documentState?.document?.pages?.[pageIndex];
    if (!pdfPage || !pdfPage.size || !pageWidth || !pageHeight) {
      const s = documentState?.scale ?? 1;
      return { scaleX: s, scaleY: s };
    }
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

  if (pageFields.length === 0) return null;

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
          .map((widget: WidgetCoordinates, widgetIdx: number) => (
            <WidgetInput
              key={`${field.name}-${widgetIdx}`}
              field={field}
              widget={widget}
              value={values[field.name] ?? ''}
              isActive={activeFieldName === field.name}
              error={validationErrors[field.name]}
              scaleX={scaleX}
              scaleY={scaleY}
              onFocus={handleFocus}
              onChange={handleChange}
            />
          ))
      )}
    </div>
  );
}

export default FormFieldOverlay;

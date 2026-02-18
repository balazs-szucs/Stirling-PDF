/**
 * Types for the Form Fill PDF Viewer feature.
 * These mirror the backend FormFieldWithCoordinates model.
 */

export interface WidgetCoordinates {
  pageIndex: number;
  x: number;      // PDF points, un-rotated, CSS upper-left origin
  y: number;      // PDF points, un-rotated, CSS upper-left origin
  width: number;  // PDF points
  height: number; // PDF points
  /** Export value for this specific widget (radio/checkbox only) */
  exportValue?: string;
  /** Font size in PDF points */
  fontSize?: number;
}

export interface FormField {
  name: string;
  label: string;
  type: FormFieldType;
  value: string;
  /** Export values used for data binding (sent to backend) */
  options: string[] | null;
  /** Human-readable display labels parallel to options. Null when same as options. */
  displayOptions: string[] | null;
  required: boolean;
  readOnly: boolean;
  multiSelect: boolean;
  multiline: boolean;
  tooltip: string | null;
  widgets: WidgetCoordinates[] | null;
}

export type FormFieldType =
  | 'text'
  | 'checkbox'
  | 'combobox'
  | 'listbox'
  | 'radio'
  | 'button'
  | 'signature';

// ---------------------------------------------------------------------------
// Form Maker types — for creating new fields from scratch
// ---------------------------------------------------------------------------

/**
 * Definition for a new form field to be created via POST /api/v1/form/create-fields.
 * Coordinates (x, y, width, height) are in PDF points, CSS upper-left origin
 * (same system as WidgetCoordinates returned by /fields-with-coordinates).
 */
export interface NewFormFieldDefinition {
  /** Internal field name (unique in AcroForm) */
  name: string;
  /** Displayed label / alternate field name */
  label?: string;
  /** Field type */
  type: FormFieldType;
  /** 0-based page index */
  pageIndex: number;
  /** Distance from left edge of CropBox, in PDF points */
  x: number;
  /** Distance from top edge of CropBox, in PDF points */
  y: number;
  /** Width in PDF points */
  width: number;
  /** Height in PDF points */
  height: number;
  /** Whether the field is required */
  required?: boolean;
  /** Whether a listbox allows multi-selection */
  multiSelect?: boolean;
  /** Options for combobox, listbox, radio fields */
  options?: string[];
  /** Default / pre-filled value */
  defaultValue?: string;
  /** Tooltip / user description */
  tooltip?: string;
}

/**
 * A field being designed in the form maker UI.
 * Extends NewFormFieldDefinition with a client-side id for React keys / selection.
 */
export interface FormMakerField extends NewFormFieldDefinition {
  /** Client-side unique id (not sent to backend) */
  id: string;
}

export interface FormFillState {
  /** Fields fetched from backend with coordinates */
  fields: FormField[];
  /** Current user-entered values keyed by field name */
  values: Record<string, string>;
  /** Whether a backend fetch is in progress */
  loading: boolean;
  /** Error message from fetch */
  error: string | null;
  /** Currently focused/selected field name */
  activeFieldName: string | null;
  /** Whether the form has been modified */
  isDirty: boolean;
  /** Current validation errors keyed by field name */
  validationErrors: Record<string, string>;
}

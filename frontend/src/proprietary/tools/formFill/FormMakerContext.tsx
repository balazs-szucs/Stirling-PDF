/**
 * FormMakerContext — React context for form maker state management.
 *
 * Manages the list of FormMakerField definitions being built by the user,
 * drawing mode (click-drag to place fields on the PDF), field selection,
 * and submission to the backend.
 *
 * Provided inside FormFillProvider so it is accessible from both the
 * FormFill tool panel (FormMakerPanel) and the FormFieldOverlay (draw layer).
 */
import React, {
  createContext,
  useCallback,
  useContext,
  useReducer,
} from 'react';
import type { FormMakerField, NewFormFieldDefinition } from '@proprietary/tools/formFill/types';
import { createFormFields } from '@proprietary/tools/formFill/formApi';

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

interface DrawingState {
  /** 0-based page index where drawing started */
  pageIndex: number;
  /** Start point in PDF points, CSS upper-left origin */
  startX: number;
  startY: number;
  /** Current pointer position during drag, PDF points */
  currentX: number;
  currentY: number;
}

interface FormMakerState {
  fields: FormMakerField[];
  selectedFieldId: string | null;
  /** Whether the draw-tool is active (crosshair cursor shown on PDF) */
  isDrawingMode: boolean;
  /** In-progress rectangle being drawn */
  activeDrawing: DrawingState | null;
  isSubmitting: boolean;
  error: string | null;
}

// ---------------------------------------------------------------------------
// Actions
// ---------------------------------------------------------------------------

type FormMakerAction =
  | { type: 'ADD_FIELD'; field: FormMakerField }
  | { type: 'UPDATE_FIELD'; id: string; updates: Partial<NewFormFieldDefinition> }
  | { type: 'REMOVE_FIELD'; id: string }
  | { type: 'SELECT_FIELD'; id: string | null }
  | { type: 'SET_DRAWING_MODE'; enabled: boolean }
  | { type: 'START_DRAWING'; pageIndex: number; x: number; y: number }
  | { type: 'UPDATE_DRAWING'; x: number; y: number }
  | { type: 'FINISH_DRAWING' }
  | { type: 'CANCEL_DRAWING' }
  | { type: 'SET_SUBMITTING'; value: boolean }
  | { type: 'SET_ERROR'; error: string | null }
  | { type: 'CLEAR_FIELDS' };

// ---------------------------------------------------------------------------
// Reducer
// ---------------------------------------------------------------------------

const initialState: FormMakerState = {
  fields: [],
  selectedFieldId: null,
  isDrawingMode: false,
  activeDrawing: null,
  isSubmitting: false,
  error: null,
};

let _nextFieldIndex = 1;

function reducer(state: FormMakerState, action: FormMakerAction): FormMakerState {
  switch (action.type) {
    case 'ADD_FIELD':
      return {
        ...state,
        fields: [...state.fields, action.field],
        selectedFieldId: action.field.id,
      };

    case 'UPDATE_FIELD':
      return {
        ...state,
        fields: state.fields.map((f) =>
          f.id === action.id ? { ...f, ...action.updates } : f
        ),
      };

    case 'REMOVE_FIELD': {
      const newFields = state.fields.filter((f) => f.id !== action.id);
      const newSelected =
        state.selectedFieldId === action.id
          ? (newFields.length > 0 ? newFields[newFields.length - 1].id : null)
          : state.selectedFieldId;
      return { ...state, fields: newFields, selectedFieldId: newSelected };
    }

    case 'SELECT_FIELD':
      return { ...state, selectedFieldId: action.id };

    case 'SET_DRAWING_MODE':
      return {
        ...state,
        isDrawingMode: action.enabled,
        activeDrawing: action.enabled ? state.activeDrawing : null,
      };

    case 'START_DRAWING':
      return {
        ...state,
        activeDrawing: {
          pageIndex: action.pageIndex,
          startX: action.x,
          startY: action.y,
          currentX: action.x,
          currentY: action.y,
        },
      };

    case 'UPDATE_DRAWING':
      if (!state.activeDrawing) return state;
      return {
        ...state,
        activeDrawing: { ...state.activeDrawing, currentX: action.x, currentY: action.y },
      };

    case 'FINISH_DRAWING': {
      if (!state.activeDrawing) return state;
      const { pageIndex, startX, startY, currentX, currentY } = state.activeDrawing;
      const x = Math.min(startX, currentX);
      const y = Math.min(startY, currentY);
      const width = Math.abs(currentX - startX);
      const height = Math.abs(currentY - startY);

      // Ignore tiny accidental clicks (< 5 PDF points in any dimension)
      if (width < 5 || height < 5) {
        return { ...state, activeDrawing: null };
      }

      const id = `maker-field-${Date.now()}-${_nextFieldIndex++}`;
      const name = `field_${_nextFieldIndex}`;
      const newField: FormMakerField = {
        id,
        name,
        type: 'text',
        pageIndex,
        x,
        y,
        width,
        height,
        required: false,
      };

      return {
        ...state,
        activeDrawing: null,
        fields: [...state.fields, newField],
        selectedFieldId: id,
      };
    }

    case 'CANCEL_DRAWING':
      return { ...state, activeDrawing: null };

    case 'SET_SUBMITTING':
      return { ...state, isSubmitting: action.value };

    case 'SET_ERROR':
      return { ...state, error: action.error };

    case 'CLEAR_FIELDS':
      return { ...state, fields: [], selectedFieldId: null, activeDrawing: null };

    default:
      return state;
  }
}

// ---------------------------------------------------------------------------
// Context value
// ---------------------------------------------------------------------------

interface FormMakerContextValue {
  state: FormMakerState;
  addField: (field: Omit<FormMakerField, 'id'>) => string;
  updateField: (id: string, updates: Partial<NewFormFieldDefinition>) => void;
  removeField: (id: string) => void;
  selectField: (id: string | null) => void;
  setDrawingMode: (enabled: boolean) => void;
  startDrawing: (pageIndex: number, x: number, y: number) => void;
  updateDrawing: (x: number, y: number) => void;
  finishDrawing: () => void;
  cancelDrawing: () => void;
  submitFields: (file: File | Blob) => Promise<Blob>;
  clearFields: () => void;
}

// ---------------------------------------------------------------------------
// Context + hook
// ---------------------------------------------------------------------------

const FormMakerContext = createContext<FormMakerContextValue | null>(null);

/**
 * Returns the FormMakerContext value, or null if not inside a FormMakerProvider.
 * Components should guard against null to degrade gracefully in core builds.
 */
export function useFormMaker(): FormMakerContextValue | null {
  return useContext(FormMakerContext);
}

// ---------------------------------------------------------------------------
// Provider
// ---------------------------------------------------------------------------

export function FormMakerProvider({ children }: { children: React.ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState);

  const addField = useCallback((field: Omit<FormMakerField, 'id'>): string => {
    const id = `maker-field-${Date.now()}-${_nextFieldIndex++}`;
    dispatch({ type: 'ADD_FIELD', field: { ...field, id } });
    return id;
  }, []);

  const updateField = useCallback(
    (id: string, updates: Partial<NewFormFieldDefinition>) => {
      dispatch({ type: 'UPDATE_FIELD', id, updates });
    },
    []
  );

  const removeField = useCallback((id: string) => {
    dispatch({ type: 'REMOVE_FIELD', id });
  }, []);

  const selectField = useCallback((id: string | null) => {
    dispatch({ type: 'SELECT_FIELD', id });
  }, []);

  const setDrawingMode = useCallback((enabled: boolean) => {
    dispatch({ type: 'SET_DRAWING_MODE', enabled });
  }, []);

  const startDrawing = useCallback((pageIndex: number, x: number, y: number) => {
    dispatch({ type: 'START_DRAWING', pageIndex, x, y });
  }, []);

  const updateDrawing = useCallback((x: number, y: number) => {
    dispatch({ type: 'UPDATE_DRAWING', x, y });
  }, []);

  const finishDrawing = useCallback(() => {
    dispatch({ type: 'FINISH_DRAWING' });
  }, []);

  const cancelDrawing = useCallback(() => {
    dispatch({ type: 'CANCEL_DRAWING' });
  }, []);

  const submitFields = useCallback(
    async (file: File | Blob): Promise<Blob> => {
      if (state.fields.length === 0) {
        throw new Error('No fields to create');
      }
      dispatch({ type: 'SET_SUBMITTING', value: true });
      dispatch({ type: 'SET_ERROR', error: null });
      try {
        // Strip client-side `id` before sending to backend
        const definitions: NewFormFieldDefinition[] = state.fields.map(
          ({ id: _id, ...def }) => def
        );
        const result = await createFormFields(file, definitions);
        return result;
      } catch (err: any) {
        const message =
          err?.response?.data instanceof Blob
            ? 'Failed to create form fields'
            : err?.message || 'Failed to create form fields';
        dispatch({ type: 'SET_ERROR', error: message });
        throw err;
      } finally {
        dispatch({ type: 'SET_SUBMITTING', value: false });
      }
    },
    [state.fields]
  );

  const clearFields = useCallback(() => {
    dispatch({ type: 'CLEAR_FIELDS' });
  }, []);

  const value: FormMakerContextValue = {
    state,
    addField,
    updateField,
    removeField,
    selectField,
    setDrawingMode,
    startDrawing,
    updateDrawing,
    finishDrawing,
    cancelDrawing,
    submitFields,
    clearFields,
  };

  return (
    <FormMakerContext.Provider value={value}>
      {children}
    </FormMakerContext.Provider>
  );
}

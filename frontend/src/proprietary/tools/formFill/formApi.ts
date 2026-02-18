/**
 * API service for form-related backend calls.
 */
import apiClient from '@app/services/apiClient';
import type { FormField, NewFormFieldDefinition } from '@proprietary/tools/formFill/types';

/**
 * Fetch form fields with coordinates from the backend.
 * Calls POST /api/v1/form/fields-with-coordinates
 */
export async function fetchFormFieldsWithCoordinates(
  file: File | Blob
): Promise<FormField[]> {
  const formData = new FormData();
  formData.append('file', file);

  const response = await apiClient.post<FormField[]>(
    '/api/v1/form/fields-with-coordinates',
    formData
  );
  return response.data;
}

/**
 * Create new form fields in a PDF and return the updated PDF blob.
 * Calls POST /api/v1/form/create-fields
 *
 * Coordinates in each field definition must be in CSS upper-left origin (same system
 * as returned by fetchFormFieldsWithCoordinates).
 */
export async function createFormFields(
  file: File | Blob,
  fields: NewFormFieldDefinition[]
): Promise<Blob> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append(
    'fields',
    new Blob([JSON.stringify(fields)], { type: 'application/json' })
  );

  const response = await apiClient.post('/api/v1/form/create-fields', formData, {
    responseType: 'blob',
  });
  return response.data;
}

/**
 * Fill form fields and get back a filled PDF blob.
 * Calls POST /api/v1/form/fill
 */
export async function fillFormFields(
  file: File | Blob,
  values: Record<string, string>,
  flatten: boolean = false
): Promise<Blob> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append(
    'data',
    new Blob([JSON.stringify(values)], { type: 'application/json' })
  );
  formData.append('flatten', String(flatten));

  const response = await apiClient.post('/api/v1/form/fill', formData, {
    responseType: 'blob',
  });
  return response.data;
}


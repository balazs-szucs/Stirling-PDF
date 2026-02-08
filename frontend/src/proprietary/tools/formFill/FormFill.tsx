/**
 * FormFill — The tool component that renders in the left ToolPanel
 * when the "Fill Form" tool is selected.
 *
 * Contains the controls (save, flatten, re-scan) at the top and
 * the full form field list below, so users can fill everything
 * from the left sidebar without a separate right-side panel.
 */
import React, { useEffect, useCallback, useState, useRef, useMemo, memo } from 'react';
import {
  Button,
  Stack,
  Text,
  Alert,
  Group,
  Switch,
  Loader,
  Box,
  ScrollArea,
  TextInput,
  Textarea,
  Checkbox,
  Select,
  MultiSelect,
  Divider,
  Badge,
  Paper,
  Progress,
} from '@mantine/core';
import { useFormFill } from './FormFillContext';
import { useNavigation } from '@app/contexts/NavigationContext';
import { useViewer } from '@app/contexts/ViewerContext';
import { useFileState, useFileActions } from '@app/contexts/FileContext';
import { isStirlingFile } from '@app/types/fileContext';
import { createStirlingFilesAndStubs } from '@app/services/fileStubHelpers';
import type { BaseToolProps } from '@app/types/tool';
import type { FormField, FormFieldType } from './types';
import SaveIcon from '@mui/icons-material/Save';
import RefreshIcon from '@mui/icons-material/Refresh';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import TextFieldsIcon from '@mui/icons-material/TextFields';
import CheckBoxIcon from '@mui/icons-material/CheckBox';
import ArrowDropDownCircleIcon from '@mui/icons-material/ArrowDropDownCircle';
import RadioButtonCheckedIcon from '@mui/icons-material/RadioButtonChecked';
import ListIcon from '@mui/icons-material/List';
import DrawIcon from '@mui/icons-material/Draw';

// ─── Field type visual config ───────────────────────────────────────────

const FIELD_TYPE_ICON: Record<FormFieldType, React.ReactNode> = {
  text: <TextFieldsIcon sx={{ fontSize: 14 }} />,
  checkbox: <CheckBoxIcon sx={{ fontSize: 14 }} />,
  combobox: <ArrowDropDownCircleIcon sx={{ fontSize: 14 }} />,
  listbox: <ListIcon sx={{ fontSize: 14 }} />,
  radio: <RadioButtonCheckedIcon sx={{ fontSize: 14 }} />,
  button: <DrawIcon sx={{ fontSize: 14 }} />,
  signature: <DrawIcon sx={{ fontSize: 14 }} />,
};

const FIELD_TYPE_COLOR: Record<FormFieldType, string> = {
  text: 'blue',
  checkbox: 'green',
  combobox: 'violet',
  listbox: 'cyan',
  radio: 'orange',
  button: 'gray',
  signature: 'pink',
};

// ─── Field input widget ────────────────────────────────────────────────

function FieldInputInner({
  field,
  value,
  onValueChange,
}: {
  field: FormField;
  value: string;
  onValueChange: (fieldName: string, value: string) => void;
}) {
  const onChange = useCallback(
    (v: string) => onValueChange(field.name, v),
    [onValueChange, field.name]
  );

  switch (field.type) {
    case 'text':
      if (field.multiline) {
        return (
          <Textarea
            size="xs"
            value={value}
            onChange={(e) => onChange(e.currentTarget.value)}
            placeholder={field.tooltip || `Enter ${field.label}`}
            disabled={field.readOnly}
            autosize
            minRows={2}
            maxRows={5}
          />
        );
      }
      return (
        <TextInput
          size="xs"
          value={value}
          onChange={(e) => onChange(e.currentTarget.value)}
          placeholder={field.tooltip || `Enter ${field.label}`}
          disabled={field.readOnly}
        />
      );

    case 'checkbox': {
      const isChecked = !!value && value !== 'Off';
      const onValue = (field.widgets && field.widgets[0]?.exportValue) || 'Yes';
      return (
        <Checkbox
          size="xs"
          checked={isChecked}
          onChange={(e) => onChange(e.currentTarget.checked ? onValue : 'Off')}
          label={field.label}
          disabled={field.readOnly}
        />
      );
    }

    case 'combobox': {
      const comboData = (field.options || []).map((opt, idx) => ({
        value: opt,
        label: (field.displayOptions && field.displayOptions[idx]) || opt,
      }));
      return (
        <Select
          size="xs"
          data={comboData}
          value={value || null}
          onChange={(v) => onChange(v || '')}
          placeholder={`Select ${field.label}`}
          clearable
          searchable
          disabled={field.readOnly}
        />
      );
    }

    case 'listbox': {
      const listData = (field.options || []).map((opt, idx) => ({
        value: opt,
        label: (field.displayOptions && field.displayOptions[idx]) || opt,
      }));
      if (field.multiSelect) {
        const selectedValues = value ? value.split(',').filter(Boolean) : [];
        return (
          <MultiSelect
            size="xs"
            data={listData}
            value={selectedValues}
            onChange={(vals) => onChange(vals.join(','))}
            placeholder={`Select ${field.label}`}
            searchable
            disabled={field.readOnly}
          />
        );
      }
      return (
        <Select
          size="xs"
          data={listData}
          value={value || null}
          onChange={(v) => onChange(v || '')}
          placeholder={`Select ${field.label}`}
          clearable
          searchable
          disabled={field.readOnly}
        />
      );
    }

    case 'radio': {
      const radioOptions: string[] = [];
      if (field.widgets && field.widgets.length > 0) {
        for (const w of field.widgets) {
          if (w.exportValue && !radioOptions.includes(w.exportValue)) {
            radioOptions.push(w.exportValue);
          }
        }
      }
      if (radioOptions.length === 0 && field.options) {
        radioOptions.push(...field.options);
      }
      return (
        <Stack gap={4}>
          {radioOptions.map((opt) => (
            <Checkbox
              key={opt}
              size="xs"
              label={opt}
              checked={value === opt}
              onChange={() => {
                // Radio buttons should not deselect — only select a different option
                if (value !== opt) onChange(opt);
              }}
              disabled={field.readOnly}
              styles={{ input: { borderRadius: '50%' } }}
            />
          ))}
        </Stack>
      );
    }

    default:
      return (
        <TextInput
          size="xs"
          value={value}
          onChange={(e) => onChange(e.currentTarget.value)}
          disabled={field.readOnly}
        />
      );
  }
}

const FieldInput = memo(FieldInputInner);

// ─── Main component ─────────────────────────────────────────────────────

const FormFill = (_props: BaseToolProps) => {
  const { selectedTool, workbench } = useNavigation();
  const { selectors, state: fileState } = useFileState();
  const { actions } = useFileActions();

  const {
    state: formState,
    fetchFields,
    submitForm,
    setValue,
    setActiveField,
  } = useFormFill();

  const { scrollActions } = useViewer();

  const [flatten, setFlatten] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [validationErrors, setValidationErrors] = useState<Record<string, string>>({});
  const hasFetched = useRef(false);
  const activeFieldRef = useRef<HTMLDivElement>(null);

  // Get the current file
  const activeFiles = selectors.getFiles();
  const selectedFileIds = fileState.ui.selectedFileIds;
  const currentFile = React.useMemo(() => {
    if (activeFiles.length === 0) return null;
    if (selectedFileIds.length > 0) {
      const sel = activeFiles.find(
        (f) => isStirlingFile(f) && selectedFileIds.includes(f.fileId)
      );
      if (sel) return sel;
    }
    return activeFiles[0];
  }, [activeFiles, selectedFileIds]);

  const isActive = selectedTool === 'formFill';

  // Fetch form fields when tool activates or file changes
  useEffect(() => {
    if (
      selectedTool === 'formFill' &&
      workbench === 'viewer' &&
      currentFile &&
      !hasFetched.current
    ) {
      hasFetched.current = true;
      fetchFields(currentFile);
    }
  }, [selectedTool, workbench, currentFile, fetchFields]);

  // Reset fetch flag when file changes
  useEffect(() => {
    hasFetched.current = false;
  }, [currentFile]);

  // Scroll active field into view in sidebar
  useEffect(() => {
    if (formState.activeFieldName && activeFieldRef.current) {
      activeFieldRef.current.scrollIntoView({
        behavior: 'smooth',
        block: 'center',
      });
    }
  }, [formState.activeFieldName]);

  // Keyboard shortcut: Ctrl+S to save
  useEffect(() => {
    if (!isActive) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault();
        if (formState.isDirty) handleSave();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isActive, formState.isDirty]);

  // Handle refresh
  const handleRefresh = useCallback(() => {
    if (currentFile) {
      hasFetched.current = false;
      fetchFields(currentFile);
    }
  }, [currentFile, fetchFields]);

  // Validate required fields
  const validateForm = useCallback((): boolean => {
    const errors: Record<string, string> = {};
    for (const field of formState.fields) {
      const value = formState.values[field.name] || '';
      if (field.required && !value.trim()) {
        errors[field.name] = `${field.label} is required`;
      }
    }
    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  }, [formState.fields, formState.values]);

  // Handle save/apply
  const handleSave = useCallback(async () => {
    if (!currentFile || !isStirlingFile(currentFile)) return;

    if (!validateForm()) {
      setSaveError('Please fill in all required fields');
      return;
    }

    setSaving(true);
    setSaveError(null);

    try {
      const filledBlob = await submitForm(currentFile, flatten);
      const filledFile = new File(
        [filledBlob],
        currentFile.name || 'filled.pdf',
        { type: 'application/pdf' }
      );

      const currentFileId = currentFile.fileId;
      const parentStub = selectors.getStirlingFileStub(currentFileId);
      if (!parentStub) throw new Error('Parent stub not found');

      const { stirlingFiles, stubs } = await createStirlingFilesAndStubs(
        [filledFile], parentStub, 'formFill' as any
      );
      await actions.consumeFiles([currentFileId], stirlingFiles, stubs);

      hasFetched.current = false;
      setValidationErrors({});
    } catch (err: any) {
      setSaveError(err?.message || 'Failed to save filled form');
    } finally {
      setSaving(false);
    }
  }, [currentFile, submitForm, flatten, actions, selectors, validateForm]);

  const handleValueChange = useCallback(
    (fieldName: string, value: string) => {
      setValue(fieldName, value);
      // Clear validation error for this field when user types
      setValidationErrors((prev) => {
        if (!prev[fieldName]) return prev;
        const next = { ...prev };
        delete next[fieldName];
        return next;
      });
    },
    [setValue]
  );

  const handleFieldClick = useCallback(
    (fieldName: string, pageIndex?: number) => {
      setActiveField(fieldName);
      // Scroll to the field's page in the viewer
      if (pageIndex !== undefined) {
        scrollActions.scrollToPage(pageIndex + 1);
      }
    },
    [setActiveField, scrollActions]
  );

  // Memoize fields grouped by page
  const { sortedPages, fieldsByPage } = useMemo(() => {
    const byPage = new Map<number, FormField[]>();
    for (const field of formState.fields) {
      const pageIndex =
        field.widgets && field.widgets.length > 0 ? field.widgets[0].pageIndex : 0;
      if (!byPage.has(pageIndex)) {
        byPage.set(pageIndex, []);
      }
      byPage.get(pageIndex)!.push(field);
    }
    const pages = Array.from(byPage.keys()).sort((a, b) => a - b);
    return { sortedPages: pages, fieldsByPage: byPage };
  }, [formState.fields]);

  // Progress tracking
  const filledCount = useMemo(() => {
    return Object.values(formState.values).filter((v) => v && v !== 'Off' && v.trim() !== '').length;
  }, [formState.values]);

  const requiredCount = useMemo(() => {
    return formState.fields.filter((f) => f.required).length;
  }, [formState.fields]);

  const filledRequiredCount = useMemo(() => {
    return formState.fields.filter(
      (f) => f.required && formState.values[f.name] && formState.values[f.name] !== 'Off' && formState.values[f.name].trim() !== ''
    ).length;
  }, [formState.fields, formState.values]);

  if (!isActive) return null;

  return (
    <Box style={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      {/* Controls section */}
      <Box p="sm" style={{ flexShrink: 0, borderBottom: '1px solid var(--mantine-color-default-border)' }}>
        <Stack gap="sm">
          {/* Status */}
          {formState.loading && (
            <Group gap="xs">
              <Loader size="xs" />
              <Text size="sm" c="dimmed">
                Analysing form fields…
              </Text>
            </Group>
          )}

          {formState.error && (
            <Alert
              icon={<WarningAmberIcon sx={{ fontSize: 18 }} />}
              color="red"
              variant="light"
              p="xs"
            >
              <Text size="xs">{formState.error}</Text>
            </Alert>
          )}

          {!formState.loading && formState.fields.length > 0 && (
            <>
              {/* Progress */}
              <Box>
                <Group justify="space-between" mb={4}>
                  <Text size="xs" c="dimmed">
                    {filledCount}/{formState.fields.length} fields filled
                    {requiredCount > 0 && ` · ${filledRequiredCount}/${requiredCount} required`}
                  </Text>
                </Group>
                <Progress
                  value={formState.fields.length > 0 ? (filledCount / formState.fields.length) * 100 : 0}
                  size="xs"
                  radius="xl"
                  color={filledRequiredCount === requiredCount ? 'teal' : 'blue'}
                />
              </Box>

              {/* Options */}
              <Switch
                label="Flatten after filling"
                checked={flatten}
                onChange={(e) => setFlatten(e.currentTarget.checked)}
                size="xs"
              />

              {/* Actions */}
              <Group gap="xs">
                <Button
                  leftSection={<SaveIcon sx={{ fontSize: 14 }} />}
                  size="xs"
                  onClick={handleSave}
                  loading={saving}
                  disabled={!formState.isDirty}
                  fullWidth
                >
                  Apply & Save
                </Button>
                <Button
                  leftSection={<RefreshIcon sx={{ fontSize: 14 }} />}
                  size="xs"
                  variant="light"
                  onClick={handleRefresh}
                >
                  Re-scan
                </Button>
              </Group>

              {saveError && (
                <Alert color="red" variant="light" p="xs">
                  <Text size="xs">{saveError}</Text>
                </Alert>
              )}

              {formState.isDirty && (
                <Text size="xs" c="yellow.7">
                  Unsaved changes
                </Text>
              )}
            </>
          )}

          {!formState.loading && formState.fields.length === 0 && !formState.error && (
            <Text size="xs" c="dimmed">
              No fillable form fields found.
            </Text>
          )}
        </Stack>
      </Box>

      {/* Form fields list */}
      {!formState.loading && formState.fields.length > 0 && (
        <ScrollArea style={{ flex: 1 }} px="sm" py="sm">
          <Stack gap="sm">
            {sortedPages.map((pageIdx) => (
              <React.Fragment key={pageIdx}>
                <Divider
                  label={
                    <Text size="xs" fw={600} c="dimmed" tt="uppercase" lts={0.5}>
                      Page {pageIdx + 1}
                    </Text>
                  }
                  labelPosition="left"
                  mt={pageIdx === sortedPages[0] ? 0 : 'xs'}
                />
                {fieldsByPage.get(pageIdx)!.map((field) => {
                  const isFieldActive = formState.activeFieldName === field.name;
                  const hasError = !!validationErrors[field.name];
                  const pageIndex =
                    field.widgets && field.widgets.length > 0 ? field.widgets[0].pageIndex : undefined;

                  return (
                    <Paper
                      key={field.name}
                      ref={isFieldActive ? activeFieldRef : undefined}
                      p="sm"
                      radius="md"
                      withBorder
                      shadow={isFieldActive ? 'sm' : undefined}
                      style={{
                        cursor: 'pointer',
                        borderColor: hasError
                          ? 'var(--mantine-color-red-5)'
                          : isFieldActive
                            ? 'var(--mantine-color-blue-5)'
                            : undefined,
                        borderWidth: isFieldActive || hasError ? 2 : 1,
                        background: isFieldActive
                          ? 'var(--mantine-color-blue-light)'
                          : undefined,
                        transition: 'all 0.15s ease',
                      }}
                      onClick={() => handleFieldClick(field.name, pageIndex)}
                    >
                      {/* Field header */}
                      <Group gap={6} mb={6} wrap="nowrap">
                        <Box
                          style={{
                            color: `var(--mantine-color-${FIELD_TYPE_COLOR[field.type]}-6)`,
                            display: 'flex',
                            flexShrink: 0,
                          }}
                        >
                          {FIELD_TYPE_ICON[field.type]}
                        </Box>
                        <Text size="xs" fw={500} truncate style={{ flex: 1 }}>
                          {field.label || field.name}
                        </Text>
                        {field.required && (
                          <Badge size="xs" color="red" variant="dot" style={{ flexShrink: 0 }}>
                            required
                          </Badge>
                        )}
                      </Group>

                      {/* Field input */}
                      {field.type !== 'button' && field.type !== 'signature' && (
                        <Box onClick={(e: React.MouseEvent) => e.stopPropagation()}>
                          <FieldInput
                            field={field}
                            value={formState.values[field.name] ?? ''}
                            onValueChange={handleValueChange}
                          />
                        </Box>
                      )}

                      {/* Validation error */}
                      {hasError && (
                        <Text size="xs" c="red" mt={4}>
                          {validationErrors[field.name]}
                        </Text>
                      )}

                      {/* Tooltip hint */}
                      {field.tooltip && (
                        <Text size="xs" c="dimmed" mt={4} lineClamp={2}>
                          {field.tooltip}
                        </Text>
                      )}
                    </Paper>
                  );
                })}
              </React.Fragment>
            ))}
          </Stack>
        </ScrollArea>
      )}
    </Box>
  );
};

export default FormFill;

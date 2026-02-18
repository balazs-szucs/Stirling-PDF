/**
 * FormMakerPanel — left panel UI for the "Create" (form maker) mode.
 *
 * Lets users:
 * 1. Activate draw mode to click-drag new fields on the PDF
 * 2. Configure each field's properties (name, type, label, etc.)
 * 3. Submit all fields to the backend to produce a new PDF with the form
 */
import React, { useCallback, useMemo } from 'react';
import {
  Button,
  TextInput,
  Select,
  Checkbox,
  Textarea,
  NumberInput,
  Text,
  ScrollArea,
  Alert,
  Tooltip,
  ActionIcon,
  Divider,
  Stack,
  Group,
  Badge,
} from '@mantine/core';
import { useFormMaker } from '@proprietary/tools/formFill/FormMakerContext';
import { FIELD_TYPE_ICON, FIELD_TYPE_COLOR } from '@proprietary/tools/formFill/fieldMeta';
import type { FormMakerField, NewFormFieldDefinition } from '@proprietary/tools/formFill/types';
import { useFileState } from '@app/contexts/FileContext';
import { isStirlingFile } from '@app/types/fileContext';
import BrushIcon from '@mui/icons-material/Brush';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import AddCircleOutlineIcon from '@mui/icons-material/AddCircleOutline';
import PostAddIcon from '@mui/icons-material/PostAdd';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import styles from '@proprietary/tools/formFill/FormFill.module.css';

// ---------------------------------------------------------------------------
// Field type options for the type selector
// ---------------------------------------------------------------------------

const FIELD_TYPE_OPTIONS = [
  { value: 'text', label: 'Text field' },
  { value: 'checkbox', label: 'Checkbox' },
  { value: 'radio', label: 'Radio button' },
  { value: 'combobox', label: 'Dropdown (combobox)' },
  { value: 'listbox', label: 'List box' },
];

// ---------------------------------------------------------------------------
// FieldCard — compact card in the field list
// ---------------------------------------------------------------------------

interface FieldCardProps {
  field: FormMakerField;
  isSelected: boolean;
  onSelect: (id: string) => void;
  onRemove: (id: string) => void;
}

function FieldCard({ field, isSelected, onSelect, onRemove }: FieldCardProps) {
  const color = FIELD_TYPE_COLOR[field.type] ?? 'blue';
  const icon = FIELD_TYPE_ICON[field.type];

  return (
    <div
      className={`${styles.fieldCard} ${isSelected ? styles.fieldCardActive : ''}`}
      onClick={() => onSelect(field.id)}
      style={{ cursor: 'pointer' }}
    >
      <div className={styles.fieldHeader}>
        <span
          className={styles.fieldTypeIcon}
          style={{ color: `var(--mantine-color-${color}-6)` }}
        >
          {icon}
        </span>
        <span className={styles.fieldName}>{field.name}</span>
        {field.required && <span className={styles.fieldRequired}>req</span>}
        <Tooltip label="Remove field" withArrow position="left">
          <ActionIcon
            size="xs"
            variant="subtle"
            color="red"
            onClick={(e) => {
              e.stopPropagation();
              onRemove(field.id);
            }}
            aria-label="Remove field"
          >
            <DeleteOutlineIcon sx={{ fontSize: 14 }} />
          </ActionIcon>
        </Tooltip>
      </div>
      <Text size="xs" c="dimmed" style={{ lineHeight: 1.3 }}>
        {field.type} · page {field.pageIndex + 1} ·{' '}
        {Math.round(field.width)}×{Math.round(field.height)} pt
      </Text>
    </div>
  );
}

// ---------------------------------------------------------------------------
// FieldEditor — property editor for the selected field
// ---------------------------------------------------------------------------

interface FieldEditorProps {
  field: FormMakerField;
  onChange: (id: string, updates: Partial<NewFormFieldDefinition>) => void;
}

function FieldEditor({ field, onChange }: FieldEditorProps) {
  const needsOptions = ['combobox', 'listbox', 'radio'].includes(field.type);
  const isListbox = field.type === 'listbox';

  const optionsText = useMemo(
    () => (field.options ?? []).join('\n'),
    [field.options]
  );

  const handleOptionsChange = useCallback(
    (raw: string) => {
      const options = raw
        .split('\n')
        .map((s) => s.trim())
        .filter(Boolean);
      onChange(field.id, { options });
    },
    [field.id, onChange]
  );

  return (
    <Stack gap="xs" p="md" style={{ borderTop: '1px solid var(--mantine-color-default-border)' }}>
      <Text size="xs" fw={700} tt="uppercase" c="dimmed" style={{ letterSpacing: '0.05em' }}>
        Field properties
      </Text>

      <TextInput
        label="Name"
        description="Unique field name (internal)"
        size="xs"
        value={field.name}
        onChange={(e) => onChange(field.id, { name: e.currentTarget.value })}
        required
      />

      <Select
        label="Type"
        size="xs"
        data={FIELD_TYPE_OPTIONS}
        value={field.type}
        onChange={(v) => v && onChange(field.id, { type: v as any })}
      />

      <TextInput
        label="Label"
        description="Displayed label / alternate name"
        size="xs"
        value={field.label ?? ''}
        onChange={(e) => onChange(field.id, { label: e.currentTarget.value || undefined })}
      />

      <TextInput
        label="Tooltip"
        description="User-facing description"
        size="xs"
        value={field.tooltip ?? ''}
        onChange={(e) => onChange(field.id, { tooltip: e.currentTarget.value || undefined })}
      />

      <TextInput
        label="Default value"
        size="xs"
        value={field.defaultValue ?? ''}
        onChange={(e) =>
          onChange(field.id, { defaultValue: e.currentTarget.value || undefined })
        }
      />

      {needsOptions && (
        <Textarea
          label="Options"
          description="One option per line"
          size="xs"
          minRows={3}
          maxRows={6}
          value={optionsText}
          onChange={(e) => handleOptionsChange(e.currentTarget.value)}
        />
      )}

      {isListbox && (
        <Checkbox
          label="Allow multi-selection"
          size="xs"
          checked={!!field.multiSelect}
          onChange={(e) => onChange(field.id, { multiSelect: e.currentTarget.checked })}
        />
      )}

      <Checkbox
        label="Required"
        size="xs"
        checked={!!field.required}
        onChange={(e) => onChange(field.id, { required: e.currentTarget.checked })}
      />

      <Divider label="Position" labelPosition="left" my={4} />

      <Group gap="xs" grow>
        <NumberInput
          label="Page"
          size="xs"
          min={1}
          value={field.pageIndex + 1}
          onChange={(v) =>
            typeof v === 'number' && onChange(field.id, { pageIndex: Math.max(0, v - 1) })
          }
        />
        <NumberInput
          label="X (pt)"
          size="xs"
          min={0}
          value={Math.round(field.x)}
          onChange={(v) => typeof v === 'number' && onChange(field.id, { x: v })}
        />
        <NumberInput
          label="Y (pt)"
          size="xs"
          min={0}
          value={Math.round(field.y)}
          onChange={(v) => typeof v === 'number' && onChange(field.id, { y: v })}
        />
      </Group>

      <Group gap="xs" grow>
        <NumberInput
          label="Width (pt)"
          size="xs"
          min={1}
          value={Math.round(field.width)}
          onChange={(v) => typeof v === 'number' && onChange(field.id, { width: v })}
        />
        <NumberInput
          label="Height (pt)"
          size="xs"
          min={1}
          value={Math.round(field.height)}
          onChange={(v) => typeof v === 'number' && onChange(field.id, { height: v })}
        />
      </Group>
    </Stack>
  );
}

// ---------------------------------------------------------------------------
// FormMakerPanel — main export
// ---------------------------------------------------------------------------

export function FormMakerPanel() {
  const formMaker = useFormMaker();
  const { selectors, state: fileState } = useFileState();

  const activeFiles = selectors.getFiles();
  const selectedFileIds = fileState.ui.selectedFileIds;
  const currentFile = useMemo(() => {
    if (activeFiles.length === 0) return null;
    if (selectedFileIds.length > 0) {
      const sel = activeFiles.find(
        (f) => isStirlingFile(f) && selectedFileIds.includes(f.fileId)
      );
      if (sel) return sel;
    }
    return activeFiles[0];
  }, [activeFiles, selectedFileIds]);

  if (!formMaker) return null;

  const { state, setDrawingMode, selectField, updateField, removeField, submitFields, clearFields } =
    formMaker;
  const { fields, selectedFieldId, isDrawingMode, isSubmitting, error } = state;
  const selectedField = fields.find((f) => f.id === selectedFieldId) ?? null;

  const handleSubmit = async () => {
    if (!currentFile) return;
    try {
      let fileBlob: File | Blob;
      if (isStirlingFile(currentFile) && 'file' in currentFile) {
        fileBlob = (currentFile as any).file as File;
      } else if ('arrayBuffer' in currentFile) {
        fileBlob = currentFile as unknown as File;
      } else {
        return;
      }
      const resultBlob = await submitFields(fileBlob);
      const event = new CustomEvent('formfill:apply', { detail: { blob: resultBlob } });
      window.dispatchEvent(event);
      clearFields();
    } catch {
      // error already set in context
    }
  };

  return (
    <div className={styles.root}>
      {/* Header */}
      <div className={styles.header}>
        {/* Draw mode toggle */}
        <Button
          leftSection={<BrushIcon sx={{ fontSize: 14 }} />}
          size="xs"
          variant={isDrawingMode ? 'filled' : 'light'}
          color={isDrawingMode ? 'blue' : 'gray'}
          fullWidth
          onClick={() => setDrawingMode(!isDrawingMode)}
        >
          {isDrawingMode ? 'Drawing mode ON — drag to place' : 'Enable draw mode'}
        </Button>

        {isDrawingMode && (
          <Text size="xs" c="dimmed" ta="center" style={{ lineHeight: 1.5 }}>
            Click and drag on any PDF page to draw a new field rectangle.
            Configure it in the panel below.
          </Text>
        )}

        {/* Add field manually button */}
        <Button
          leftSection={<AddCircleOutlineIcon sx={{ fontSize: 14 }} />}
          size="xs"
          variant="subtle"
          fullWidth
          onClick={() => {
            formMaker.addField({
              name: `field_${fields.length + 1}`,
              type: 'text',
              pageIndex: 0,
              x: 72,
              y: 72,
              width: 200,
              height: 24,
              required: false,
            });
          }}
        >
          Add field manually
        </Button>

        {/* Error */}
        {error && (
          <Alert icon={<WarningAmberIcon sx={{ fontSize: 16 }} />} color="red" variant="light" p="xs">
            <Text size="xs">{error}</Text>
          </Alert>
        )}
      </div>

      {/* Field list */}
      {fields.length === 0 ? (
        <div className={styles.emptyState}>
          <PostAddIcon className={styles.emptyStateIcon} />
          <span className={styles.emptyStateText}>
            No fields yet. Enable draw mode and drag on the PDF to place fields,
            or click "Add field manually".
          </span>
        </div>
      ) : (
        <>
          <ScrollArea style={{ flex: 1, minHeight: 0 }}>
            <div className={styles.fieldListInner}>
              <Group justify="space-between" align="center" mb={4}>
                <Badge size="sm" variant="light" color="blue">
                  {fields.length} {fields.length === 1 ? 'field' : 'fields'}
                </Badge>
                <Button
                  size="xs"
                  variant="subtle"
                  color="red"
                  onClick={clearFields}
                  leftSection={<DeleteOutlineIcon sx={{ fontSize: 12 }} />}
                >
                  Clear all
                </Button>
              </Group>

              {fields.map((field) => (
                <FieldCard
                  key={field.id}
                  field={field}
                  isSelected={field.id === selectedFieldId}
                  onSelect={selectField}
                  onRemove={removeField}
                />
              ))}
            </div>
          </ScrollArea>

          {/* Property editor for selected field */}
          {selectedField && (
            <ScrollArea style={{ maxHeight: '45%', flexShrink: 0 }}>
              <FieldEditor field={selectedField} onChange={updateField} />
            </ScrollArea>
          )}
        </>
      )}

      {/* Submit bar */}
      {fields.length > 0 && (
        <div className={styles.statusBar} style={{ padding: '0.75rem 1rem' }}>
          <Button
            leftSection={<CheckCircleOutlineIcon sx={{ fontSize: 14 }} />}
            size="xs"
            fullWidth
            loading={isSubmitting}
            disabled={!currentFile || fields.length === 0}
            color="teal"
            onClick={handleSubmit}
          >
            Create {fields.length} {fields.length === 1 ? 'field' : 'fields'}
          </Button>
        </div>
      )}
    </div>
  );
}

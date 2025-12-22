import { Group, Stack, Text, Switch, Select } from "@mantine/core";
import { useTranslation } from "react-i18next";
import { VectorConvertParameters } from "@app/hooks/tools/convert/useVectorConvertParameters";

interface VectorConvertSettingsProps {
  parameters: VectorConvertParameters;
  onParameterChange: <K extends keyof VectorConvertParameters>(key: K, value: VectorConvertParameters[K]) => void;
  disabled?: boolean;
}

const VectorConvertSettings = ({
  parameters,
  onParameterChange,
  disabled = false
}: VectorConvertSettingsProps) => {
  const { t } = useTranslation();

  const showOutputFormat = parameters.fromExtension === 'pdf' && ['eps', 'ps', 'pcl', 'xps'].includes(parameters.toExtension);
  const showPrepressOption = ['ps', 'eps', 'epsf'].includes(parameters.fromExtension) && parameters.toExtension === 'pdf';

  return (
    <Stack gap="md">
      {showOutputFormat && (
        <Group align="end" grow>
          <Select
            label={t("vectorConvert.outputFormat", "Output Format")}
            data={[
              { value: 'eps', label: t("vectorConvert.eps", "EPS") },
              { value: 'ps', label: t("vectorConvert.ps", "PS") },
              { value: 'pcl', label: t("vectorConvert.pcl", "PCL") },
              { value: 'xps', label: t("vectorConvert.xps", "XPS") },
            ]}
            value={parameters.outputFormat}
            onChange={(value) => onParameterChange('outputFormat', value || 'eps')}
            disabled={disabled}
            allowDeselect={false}
          />
        </Group>
      )}

      {showPrepressOption && (
        <Group>
          <Switch
            label={t("vectorConvert.prepress", "Apply Prepress Settings")}
            checked={parameters.prepress}
            onChange={(event) => onParameterChange('prepress', event.currentTarget.checked)}
            disabled={disabled}
          />
          <Text size="sm" c="dimmed">
            {t("vectorConvert.prepressDescription", "Apply Ghostscript prepress settings for high-quality output")}
          </Text>
        </Group>
      )}
    </Stack>
  );
};

export default VectorConvertSettings;

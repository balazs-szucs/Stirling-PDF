import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const useAdjustContrastTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("adjustContrast.tooltip.header.title", "Adjust Contrast")
    },
    tips: [
      {
        description: t("adjustContrast.tooltip.description", "Fine-tune contrast, brightness, saturation, and individual color channels (red, green, blue) for your PDF.")
      },
      {
        title: t("adjustContrast.tooltip.preview.title", "Live Preview"),
        description: t("adjustContrast.tooltip.preview.text", "See real-time changes in the preview pane before applying.")
      }
    ]
  };
};

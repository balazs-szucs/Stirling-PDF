import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const useConvertTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("convert.tooltip.header.title", "File Conversion")
    },
    tips: [
      {
        description: t("convert.tooltip.description", "Transform documents between different formats. Convert PDFs to images, images to PDFs, or documents to PDFs.")
      },
      {
        title: t("convert.tooltip.quality.title", "Quality Options"),
        description: t("convert.tooltip.quality.text", "Adjust DPI, color modes, and compression settings to control output quality and file size.")
      }
    ]
  };
};

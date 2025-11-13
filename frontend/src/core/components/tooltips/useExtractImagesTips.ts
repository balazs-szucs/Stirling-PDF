import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const useExtractImagesTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("extractImages.tooltip.header.title", "Extract Images")
    },
    tips: [
      {
        description: t("extractImages.tooltip.description", "Extract all embedded images from your PDF and save them as individual files.")
      },
      {
        title: t("extractImages.tooltip.format.title", "Output Format"),
        description: t("extractImages.tooltip.format.text", "Choose JPG for smaller files or PNG for transparency and higher quality.")
      }
    ]
  };
};

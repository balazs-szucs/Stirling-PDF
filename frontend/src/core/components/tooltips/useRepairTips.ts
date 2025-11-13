import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const useRepairTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("repair.tooltip.header.title", "PDF Repair")
    },
    tips: [
      {
        description: t("repair.tooltip.description", "Fix corrupted or damaged PDF files. Use this when PDFs won't open properly or display incorrectly.")
      }
    ]
  };
};


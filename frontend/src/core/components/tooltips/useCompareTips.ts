import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const useCompareTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("compare.tooltip.header.title", "PDF Comparison")
    },
    tips: [
      {
        description: t("compare.tooltip.description", "Compare two PDF documents to identify differences. Perfect for reviewing changes or checking document versions.")
      },
      {
        title: t("compare.tooltip.setup.title", "Setup"),
        description: t("compare.tooltip.setup.text", "Select your original PDF and the edited version. Use the swap button to reverse the comparison direction.")
      }
    ]
  };
};

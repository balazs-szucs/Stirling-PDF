import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const usePageLayoutTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("pageLayout.tooltip.header.title", "Page Layout")
    },
    tips: [
      {
        description: t("pageLayout.tooltip.description", "Rearrange pages by dragging and dropping, rotate pages, or delete unwanted pages from your PDF.")
      }
    ]
  };
};

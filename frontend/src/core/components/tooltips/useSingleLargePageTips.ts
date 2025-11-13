import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const useSingleLargePageTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("singleLargePage.tooltip.header.title", "Single Large Page")
    },
    tips: [
      {
        description: t("singleLargePage.tooltip.description", "Combine all pages into one continuous large page. Useful for posters or specialized printing needs.")
      }
    ]
  };
};


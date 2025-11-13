import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const useShowJSTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("showJS.tooltip.header.title", "Extract JavaScript")
    },
    tips: [
      {
        description: t("showJS.tooltip.description", "Extract and view JavaScript code embedded in PDF documents. Useful for security analysis and debugging.")
      }
    ]
  };
};

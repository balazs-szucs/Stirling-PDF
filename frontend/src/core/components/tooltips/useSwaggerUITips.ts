import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const useSwaggerUITips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("swaggerUI.tooltip.header.title", "API Documentation")
    },
    tips: [
      {
        description: t("swaggerUI.tooltip.description", "Interactive API documentation for developers. Test endpoints, view request/response formats, and generate code samples.")
      }
    ]
  };
};

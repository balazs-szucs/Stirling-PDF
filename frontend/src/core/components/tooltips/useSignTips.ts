import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const useSignTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("sign.tooltip.header.title", "Sign PDF")
    },
    tips: [
      {
        description: t("sign.tooltip.description", "Add digital signatures to verify authenticity and integrity. Draw, type, or upload your signature, then position it on the document.")
      }
    ]
  };
};

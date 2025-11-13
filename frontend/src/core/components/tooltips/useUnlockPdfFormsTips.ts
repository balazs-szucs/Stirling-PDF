import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const useUnlockPdfFormsTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("unlockPdfForms.tooltip.header.title", "Unlock PDF Forms")
    },
    tips: [
      {
        description: t("unlockPdfForms.tooltip.description", "Remove restrictions from PDF forms to make them fully editable and fillable.")
      }
    ]
  };
};


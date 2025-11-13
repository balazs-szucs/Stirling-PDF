import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const useRemoveAnnotationsTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("removeAnnotations.tooltip.header.title", "Remove Annotations")
    },
    tips: [
      {
        description: t("removeAnnotations.tooltip.description", "Remove all annotations, comments, highlights, and markup from your PDF while preserving the original content.")
      }
    ]
  };
};

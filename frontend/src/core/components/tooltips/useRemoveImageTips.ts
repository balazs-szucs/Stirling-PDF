import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const useRemoveImageTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("removeImage.tooltip.header.title", "Remove Images")
    },
    tips: [
      {
        description: t("removeImage.tooltip.description", "Remove all images from your PDF while preserving text content and layout. Great for reducing file size.")
      }
    ]
  };
};


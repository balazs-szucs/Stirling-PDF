import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const useSanitizeTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("sanitize.tooltip.header.title", "PDF Sanitization")
    },
    tips: [
      {
        description: t("sanitize.tooltip.description", "Remove potentially sensitive elements from your PDF: JavaScript, embedded files, metadata, links, and fonts. Makes documents safer for sharing.")
      },
      {
        title: t("sanitize.tooltip.options.title", "What Gets Removed"),
        description: t("sanitize.tooltip.options.text", "JavaScript, embedded files, metadata, links, and fonts. Note: Removing fonts may change document appearance.")
      }
    ]
  };
};

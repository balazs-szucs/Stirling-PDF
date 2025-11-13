import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const useAddPageNumbersTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("addPageNumbers.tooltip.header.title", "Add Page Numbers")
    },
    tips: [
      {
        description: t("addPageNumbers.tooltip.description", "Add page numbers to your PDF. Choose position, pages to number, starting number, custom text, and font style.")
      },
      {
        title: t("addPageNumbers.tooltip.customText.title", "Custom Text"),
        description: t("addPageNumbers.tooltip.customText.text", "Use {page} for current page and {total} for total pages. Example: 'Page {page} of {total}'")
      }
    ]
  };
};

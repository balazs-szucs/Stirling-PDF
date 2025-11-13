import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const useAddAttachmentsTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("addAttachments.tooltip.header.title", "Add Attachments")
    },
    tips: [
      {
        description: t("addAttachments.tooltip.description", "Embed files directly into your PDF document. These attachments become part of the PDF and can be viewed by anyone who opens it.")
      },
      {
        title: t("addAttachments.tooltip.fileTypes.title", "Supported Files"),
        description: t("addAttachments.tooltip.fileTypes.text", "You can attach any file type - documents, images, spreadsheets, presentations, and archives.")
      }
    ]
  };
};

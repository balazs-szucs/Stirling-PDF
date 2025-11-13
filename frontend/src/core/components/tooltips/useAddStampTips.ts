import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const useAddStampTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("addStamp.tooltip.header.title", "Add Stamp")
    },
    tips: [
      {
        description: t("addStamp.tooltip.description", "Add text stamps (like 'CONFIDENTIAL'), images, or watermarks to your PDF pages. Control their position, size, and opacity.")
      },
      {
        title: t("addStamp.tooltip.positioning.title", "Positioning"),
        description: t("addStamp.tooltip.positioning.text", "Use preset positions (corners, center, edges) or set exact coordinates with custom rotation and margins.")
      }
    ]
  };
};

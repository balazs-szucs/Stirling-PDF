import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const useRemoveCertificateSignTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t('removeCertSign.tooltip.header.title', 'About Removing Certificate Signatures')
    },
    tips: [
      {
        title: t('removeCertSign.tooltip.overview.title', 'What this does'),
        description: t('removeCertSign.tooltip.overview.text', 'Removes digital certificate signatures and signature fields from the PDF. Previously valid signatures will no longer be verifiable after removal.')
      },
      {
        title: t('removeCertSign.tooltip.consequences.title', 'Consequences'),
        bullets: [
          t('removeCertSign.tooltip.consequences.bullet1', 'Signature validation information is removed.'),
          t('removeCertSign.tooltip.consequences.bullet2', 'Document will appear unsigned in PDF viewers.'),
          t('removeCertSign.tooltip.consequences.bullet3', "This action cannot be undone — keep a backup of the original signed file.")
        ]
      },
      {
        title: t('removeCertSign.tooltip.whenToUse.title', 'When to use this'),
        description: t('removeCertSign.tooltip.whenToUse.text', 'Use when you need to edit a signed file or remove signature metadata before distribution.')
      },
      {
        title: t('removeCertSign.tooltip.safety.title', 'Safety tips'),
        bullets: [
          t('removeCertSign.tooltip.safety.bullet1', 'Keep the original signed PDF as a backup.'),
          t('removeCertSign.tooltip.safety.bullet2', 'Removing a signature may affect legal or official documents; verify you have permission.')
        ]
      }
    ]
  };
};


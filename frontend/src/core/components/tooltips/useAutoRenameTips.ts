import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const useAutoRenameTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t('auto-rename.tooltip.header.title', 'Auto Rename')
    },
    tips: [
      {
        title: t('auto-rename.tooltip.overview.title', 'What this does'),
        description: t(
          'auto-rename.tooltip.overview.text',
          'Rename your PDF automatically by detecting a likely document title or heading.'
        )
      },
      {
        title: t('auto-rename.tooltip.detection.title', 'How titles are chosen'),
        description: t(
          'auto-rename.tooltip.detection.text',
          'Scans the first pages for prominent headings, metadata, bookmarks, or form fields that look like a document title.'
        ),
        bullets: [
          t(
            'auto-rename.tooltip.detection.bullet1',
            'Prioritises large or bold text near the start of the document'
          ),
          t(
            'auto-rename.tooltip.detection.bullet2',
            'Cleans the detected text to form a safe, readable filename'
          ),
          t(
            'auto-rename.tooltip.detection.bullet3',
            'Keeps the original filename if no suitable title is found'
          )
        ]
      },
      {
        title: t('auto-rename.tooltip.bestResults.title', 'Get the best results'),
        bullets: [
          t(
            'auto-rename.tooltip.bestResults.bullet1',
            'Place a clear title or heading near the top of the first page.'
          ),
          t(
            'auto-rename.tooltip.bestResults.bullet2',
            'Avoid special characters in headings you want to use for filenames.'
          ),
          t(
            'auto-rename.tooltip.bestResults.bullet3',
            'Review the suggested name in the results panel before exporting.'
          )
        ]
      }
    ]
  };
};

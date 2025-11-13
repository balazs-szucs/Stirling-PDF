import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

export const useAutomateTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t('automate.tooltip.header.title', 'Automation Workflows')
    },
    tips: [
      {
        title: t('automate.tooltip.overview.title', 'What this does'),
        description: t(
          'automate.tooltip.overview.text',
          'Chain multiple PDF tools to run automatically in a single repeatable workflow.'
        )
      },
      {
        title: t('automate.tooltip.steps.title', 'How it is structured'),
        description: t(
          'automate.tooltip.steps.text',
          'Use Selection to pick an existing automation, Creation to build or edit one, and Run to execute it with your files.'
        )
      },
      {
        title: t('automate.tooltip.bestPractices.title', 'Best practices'),
        bullets: [
          t(
            'automate.tooltip.bestPractices.bullet1',
            'Test new automations with sample documents before running them on important files.'
          ),
          t(
            'automate.tooltip.bestPractices.bullet2',
            'Refresh saved automations after editing individual tools so their configuration stays in sync.'
          ),
          t(
            'automate.tooltip.bestPractices.bullet3',
            'Review the results step to confirm every tool completed successfully before sharing output files.'
          )
        ]
      }
    ]
  };
};

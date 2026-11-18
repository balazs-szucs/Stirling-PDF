import { useTranslation } from "react-i18next";
import { TooltipContent } from "@editor/types/tips";

export const useAutomateSelectionTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t(
        "automate.selection.tooltip.header.title",
        "Automation Selection Overview",
      ),
    },
    tips: [
      {
        title: t(
          "automate.selection.tooltip.lists.title",
          "Saved and Suggested",
        ),
        description: t(
          "automate.selection.tooltip.lists.text",
          "Saved automations are ones you created, imported or copied from Suggested. Suggested workflows ship with Stirling — run one as-is, copy it into Saved, or edit a copy first.",
        ),
      },
      {
        title: t(
          "automate.selection.tooltip.import.title",
          "Import and Export",
        ),
        description: t(
          "automate.selection.tooltip.import.text",
          "Each automation's menu exports a config file for sharing or folder scanning. The Import button loads one from disk and maps its tools to this server.",
        ),
      },
    ],
  };
};

export const useAutomateCreationTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t(
        "automate.creation.tooltip.header.title",
        "Automation Builder Overview",
      ),
    },
    tips: [
      {
        title: t("automate.creation.tooltip.order.title", "Tool order"),
        description: t(
          "automate.creation.tooltip.order.text",
          "Tools run top to bottom; each step's output becomes the next step's input. Change a step by picking a different tool in its row.",
        ),
      },
      {
        title: t(
          "automate.creation.tooltip.configuration.title",
          "Configuration",
        ),
        description: t(
          "automate.creation.tooltip.configuration.text",
          "Steps marked 'Configuration required' need their settings chosen before saving. The warning button, or the cog beside a step, opens its settings.",
        ),
      },
      {
        title: t(
          "automate.creation.tooltip.saving.title",
          "Saving and exporting",
        ),
        description: t(
          "automate.creation.tooltip.saving.text",
          "Save stores the automation in Saved. The export buttons download its config without saving, for sharing or for watched-folder scanning.",
        ),
      },
    ],
  };
};

export const useAutomateRunTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("automate.run.tooltip.header.title", "Run Automation Overview"),
    },
    tips: [
      {
        title: t("automate.run.tooltip.files.title", "Running"),
        description: t(
          "automate.run.tooltip.files.text",
          "Select at least one file, then run. The tools execute in order, each step's output feeding the next, with progress shown per step.",
        ),
      },
      {
        title: t("automate.run.tooltip.premade.title", "Pre-made workflows"),
        description: t(
          "automate.run.tooltip.premade.text",
          "A Suggested automation can be tailored first: Edit a copy opens the builder pre-filled and leaves the original untouched.",
        ),
      },
    ],
  };
};

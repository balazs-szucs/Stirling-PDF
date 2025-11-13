import React, { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { createToolFlow } from "@app/components/tools/shared/createToolFlow";
import { BaseToolProps, ToolComponent } from "@app/types/tool";
import { withBasePath } from "@app/constants/app";
import { useSwaggerUITips } from "@app/components/tooltips/useSwaggerUITips";

const SwaggerUI = (_props: BaseToolProps): React.ReactElement => {
  const { t } = useTranslation();
  const swaggerUITips = useSwaggerUITips();

  useEffect(() => {
    // Redirect to Swagger UI
    window.open(withBasePath("/swagger-ui/5.21.0/index.html"), "_blank");
  }, []);

  return createToolFlow({
    files: {
      selectedFiles: [],
      isCollapsed: false,
    },
    steps: [
      {
        title: t("swaggerUI.settings", "API Documentation"),
        isCollapsed: false,
        onCollapsedClick: undefined,
        tooltip: swaggerUITips,
        content: (
          <div style={{ textAlign: "center", padding: "2rem" }}>
            <p>Opening Swagger UI in a new tab...</p>
            <p>
              If it didn't open automatically,{" "}
              <a href={withBasePath("/swagger-ui/5.21.0/index.html")} target="_blank" rel="noopener noreferrer">
                click here
              </a>
            </p>
          </div>
        ),
      },
    ],
  });
};

export default SwaggerUI as ToolComponent;

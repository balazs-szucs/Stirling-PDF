import type { ReactNode } from "react";

interface StickyFooterProps {
  children: ReactNode;
}

export default function StickyFooter({ children }: StickyFooterProps) {
  return (
    <div
      style={{
        position: "sticky",
        bottom: 0,
        zIndex: 2,
        background: "var(--c-bg-raised)",
        paddingTop: "var(--mantine-spacing-sm)",
        borderTop: "1px solid var(--c-border-subtle)",
      }}
    >
      {children}
    </div>
  );
}

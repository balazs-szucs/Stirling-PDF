import React, { useCallback, useState, useMemo } from 'react';
import { useDocumentState } from '@embedpdf/core/react';
import { useScroll } from '@embedpdf/plugin-scroll/react';
import { usePdfLibLinks, type PdfLibLink, type LinkType } from '@app/hooks/usePdfLibLinks';

// ---------------------------------------------------------------------------
// Configurable link styles — tweak these to change appearance globally.
// ---------------------------------------------------------------------------

interface LinkStyleState {
  backgroundColor: string;
  borderColor: string;
}

interface LinkStyleConfig {
  default: LinkStyleState;
  hover: LinkStyleState;
}

const LINK_STYLES: Record<LinkType, LinkStyleConfig> = {
  internal: {
    default: {
      backgroundColor: 'transparent',
      borderColor: 'transparent',
    },
    hover: {
      backgroundColor: 'rgba(124, 58, 237, 0.08)',
      borderColor: 'rgba(124, 58, 237, 0.30)',
    },
  },
  external: {
    default: {
      backgroundColor: 'transparent',
      borderColor: 'transparent',
    },
    hover: {
      backgroundColor: 'rgba(13, 148, 136, 0.08)',
      borderColor: 'rgba(13, 148, 136, 0.30)',
    },
  },
  unknown: {
    default: {
      backgroundColor: 'transparent',
      borderColor: 'transparent',
    },
    hover: {
      backgroundColor: 'rgba(107, 114, 128, 0.08)',
      borderColor: 'rgba(107, 114, 128, 0.30)',
    },
  },
};

const TRANSITION = 'background-color 0.2s ease, border-color 0.2s ease';

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

interface LinkOverlayProps {
  link: PdfLibLink;
  scale: number;
  disabled: boolean;
  onClick: (link: PdfLibLink) => void;
}

const LinkOverlay: React.FC<LinkOverlayProps> = React.memo(
  ({ link, scale, disabled, onClick }) => {
    const style = LINK_STYLES[link.type];

    const left = link.rect.x * scale;
    const top = link.rect.y * scale;
    const width = link.rect.width * scale;
    const height = link.rect.height * scale;

    return (
      <a
        href="#"
        onClick={(e) => {
          e.preventDefault();
          e.stopPropagation();
          if (!disabled) onClick(link);
        }}
        onMouseDown={(e) => e.stopPropagation()}
        className={`absolute block ${disabled ? 'cursor-wait' : 'cursor-pointer'}`}
        style={{
          left: `${left}px`,
          top: `${top}px`,
          width: `${width}px`,
          height: `${height}px`,
          minWidth: '6px',
          minHeight: '6px',
          pointerEvents: 'auto',
          backgroundColor: style.default.backgroundColor,
          borderColor: style.default.borderColor,
          border: '1.5px solid transparent',
          borderRadius: '3px',
          zIndex: 11,
          transition: TRANSITION,
          textDecoration: 'none',
          outline: 'none',
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.backgroundColor = style.hover.backgroundColor;
          e.currentTarget.style.borderColor = style.hover.borderColor;
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.backgroundColor = style.default.backgroundColor;
          e.currentTarget.style.borderColor = style.default.borderColor;
        }}
        onFocus={(e) => {
          e.currentTarget.style.backgroundColor = style.hover.backgroundColor;
          e.currentTarget.style.borderColor = style.hover.borderColor;
        }}
        onBlur={(e) => {
          e.currentTarget.style.backgroundColor = style.default.backgroundColor;
          e.currentTarget.style.borderColor = style.default.borderColor;
        }}
        title={getLinkTitle(link)}
        aria-label={getLinkAriaLabel(link)}
        role="link"
        tabIndex={0}
      />
    );
  },
);

LinkOverlay.displayName = 'LinkOverlay';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function getLinkTitle(link: PdfLibLink): string {
  if (link.type === 'internal' && link.targetPage !== undefined) {
    return `Go to page ${link.targetPage + 1}`;
  }
  if (link.type === 'external' && link.uri) {
    return `Open: ${link.uri}`;
  }
  return 'Link';
}

function getLinkAriaLabel(link: PdfLibLink): string {
  if (link.type === 'internal' && link.targetPage !== undefined) {
    return `Navigate to page ${link.targetPage + 1}`;
  }
  if (link.type === 'external' && link.uri) {
    return 'Open external link';
  }
  return 'Link';
}

// ---------------------------------------------------------------------------
// Main component
// ---------------------------------------------------------------------------

interface LinkLayerProps {
  documentId: string;
  pageIndex: number;
  pageWidth: number;
  pageHeight: number;
  /** Blob/object URL of the current PDF (needed by pdf-lib). */
  pdfUrl: string | null;
}

export const LinkLayer: React.FC<LinkLayerProps> = ({
  documentId,
  pageIndex,
  pdfUrl,
}) => {
  const { provides: scroll } = useScroll(documentId);
  const documentState = useDocumentState(documentId);
  const [isNavigating, setIsNavigating] = useState(false);

  // Extract links via pdf-lib
  const { links } = usePdfLibLinks(pdfUrl, pageIndex);

  // EmbedPDF scale factor (accounts for zoom / device pixel ratio)
  const scale = documentState?.scale ?? 1;

  // Memoize filtered links (skip zero-area rects)
  const visibleLinks = useMemo(
    () => links.filter((l) => l.rect.width > 0 && l.rect.height > 0),
    [links],
  );

  const handleLinkClick = useCallback(
    async (link: PdfLibLink) => {
      if (isNavigating) return;

      try {
        setIsNavigating(true);

        if (link.type === 'internal' && link.targetPage !== undefined && scroll) {
          scroll.scrollToPage({
            pageNumber: link.targetPage + 1,
            behavior: 'smooth',
          });
        } else if (link.uri) {
          try {
            const url = new URL(link.uri, window.location.href);
            if (['http:', 'https:', 'mailto:'].includes(url.protocol)) {
              window.open(link.uri, '_blank', 'noopener,noreferrer');
            } else {
              console.warn('[LinkLayer] Blocked unsafe URL protocol:', url.protocol);
            }
          } catch {
            // Fallback: open as-is (relative URLs, etc.)
            window.open(link.uri, '_blank', 'noopener,noreferrer');
          }
        }
      } catch (error) {
        console.error('[LinkLayer] Navigation failed:', error);
      } finally {
        setIsNavigating(false);
      }
    },
    [isNavigating, scroll],
  );

  if (visibleLinks.length === 0) return null;

  return (
    <div
      className="absolute inset-0"
      style={{
        pointerEvents: 'none',
        zIndex: 10,
      }}
    >
      {visibleLinks.map((link) => (
        <LinkOverlay
          key={link.id}
          link={link}
          scale={scale}
          disabled={isNavigating}
          onClick={handleLinkClick}
        />
      ))}
    </div>
  );
};

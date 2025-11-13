import React from 'react';
import styles from '@app/components/shared/tooltip/Tooltip.module.css';
import { TooltipTip } from '@app/types/tips';

interface TooltipContentProps {
  content?: React.ReactNode;
  tips?: TooltipTip[];
  header?: { title: string; logo?: React.ReactNode };
  [key: string]: any;
}

export const TooltipContent: React.FC<TooltipContentProps> = ({
  content,
  tips,
  header,
  ...otherProps
}) => {
  // Check if we have named properties (new structure) or tips array (old structure)
  const hasNamedProperties = Object.keys(otherProps).length > 0 && !tips;
  const sections = hasNamedProperties ? otherProps : {};

  const renderSection = (key: string, sectionData: any, index: number, totalSections: number) => {
    if (!sectionData || typeof sectionData !== 'object') return null;

    return (
      <div key={key} style={{ marginBottom: index < totalSections - 1 ? '24px' : '0' }}>
        {sectionData.title && (
          <div style={{
            display: 'inline-block',
            backgroundColor: 'var(--tooltip-title-bg)',
            color: 'var(--tooltip-title-color)',
            padding: '6px 12px',
            borderRadius: '16px',
            fontSize: '12px',
            fontWeight: '600',
            marginBottom: '12px'
          }}>
            {sectionData.title}
          </div>
        )}
        {sectionData.text && (
          <p style={{ margin: '0 0 12px 0', color: 'var(--text-secondary)', fontSize: '13px' }} dangerouslySetInnerHTML={{ __html: sectionData.text }} />
        )}
        {/* Support both numbered bullet keys (bullet1, bullet2...) and a bullets array */}
        {Array.isArray(sectionData.bullets) ? (
          <ul style={{ margin: '0', paddingLeft: '16px', color: 'var(--text-secondary)', fontSize: '13px' }}>
            {sectionData.bullets.map((b: string, bi: number) => (
              <li key={bi} style={{ marginBottom: bi === sectionData.bullets.length - 1 ? '0' : '6px' }} dangerouslySetInnerHTML={{ __html: b }} />
            ))}
          </ul>
        ) : (
          Object.keys(sectionData).filter(k => k.startsWith('bullet')).map((bulletKey, bulletIndex, arr) => (
            <div key={bulletKey} style={{ marginBottom: bulletIndex === arr.length - 1 ? '0' : '6px' }}>
              {arr.length > 1 ? (
                <ul style={{ margin: '0', paddingLeft: '16px', color: 'var(--text-secondary)', fontSize: '13px' }}>
                  <li dangerouslySetInnerHTML={{ __html: sectionData[bulletKey] }} />
                </ul>
              ) : (
                <p style={{ margin: '0 0 12px 0', color: 'var(--text-secondary)', fontSize: '13px' }} dangerouslySetInnerHTML={{ __html: sectionData[bulletKey] }} />
              )}
            </div>
          ))
        )}
      </div>
    );
  };

  return (
    <div
      className={`${styles['tooltip-body']}`}
      style={{
        color: 'var(--text-primary)',
        padding: '16px',
        fontSize: '14px',
        lineHeight: '1.6'
      }}
    >
      <div style={{ color: 'var(--text-primary)' }}>
        {tips ? (
          // Original tips array structure
          <>
            {tips.map((tip, index) => (
              <div key={index} style={{ marginBottom: index < tips.length - 1 ? '24px' : '0' }}>
                {tip.title && (
                  <div style={{
                    display: 'inline-block',
                    backgroundColor: 'var(--tooltip-title-bg)',
                    color: 'var(--tooltip-title-color)',
                    padding: '6px 12px',
                    borderRadius: '16px',
                    fontSize: '12px',
                    fontWeight: '600',
                    marginBottom: '12px'
                  }}>
                    {tip.title}
                  </div>
                )}
                {tip.description && (
                  <p style={{ margin: '0 0 12px 0', color: 'var(--text-secondary)', fontSize: '13px' }} dangerouslySetInnerHTML={{ __html: tip.description }} />
                )}
                {tip.bullets && tip.bullets.length > 0 && (
                  <ul style={{ margin: '0', paddingLeft: '16px', color: 'var(--text-secondary)', fontSize: '13px' }}>
                    {tip.bullets.map((bullet, bulletIndex) => (
                      <li key={bulletIndex} style={{ marginBottom: '6px' }} dangerouslySetInnerHTML={{ __html: bullet }} />
                    ))}
                  </ul>
                )}
                {tip.body && (
                  <div style={{ marginTop: '12px' }}>
                    {tip.body}
                  </div>
                )}
              </div>
            ))}
            {content && (
              <div style={{ marginTop: '24px' }}>
                {content}
              </div>
            )}
          </>
        ) : hasNamedProperties ? (
          // New named properties structure
          <>
            {Object.keys(sections).filter(key => key !== 'header').map((key, index, arr) => {
              const sectionData = sections[key];
              return renderSection(key, sectionData, index, arr.length);
            })}
            {content && (
              <div style={{ marginTop: '24px' }}>
                {content}
              </div>
            )}
          </>
        ) : (
          // Fallback to simple content
          content
        )}
      </div>
    </div>
  );
}; 
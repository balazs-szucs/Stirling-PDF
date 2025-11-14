import React, { useEffect, useRef, useState } from 'react';
import LoginRightCarousel from '@app/components/shared/LoginRightCarousel';
import loginSlides from '@app/components/shared/loginSlides';
import styles from '@app/routes/authShared/AuthLayout.module.css';

interface AuthLayoutProps {
  children: React.ReactNode
}

export default function AuthLayout({ children }: AuthLayoutProps) {
  const cardRef = useRef<HTMLDivElement | null>(null);
  const [hideRightPanel, setHideRightPanel] = useState(false);

  // Force light mode on auth pages
  useEffect(() => {
    const htmlElement = document.documentElement;
    const previousColorScheme = htmlElement.dataset.mantineColorScheme;

    // Set light mode
    htmlElement.dataset.mantineColorScheme = 'light';

    // Cleanup: restore previous theme when leaving auth pages
    return () => {
      if (previousColorScheme) {
        htmlElement.dataset.mantineColorScheme = previousColorScheme;
      }
    };
  }, []);

  useEffect(() => {
    const update = () => {
      // Use viewport to avoid hysteresis when the card is already in single-column mode
      const viewportWidth = window.innerWidth;
      const viewportHeight = window.innerHeight;
      const cardWidthIfTwoCols = Math.min(1180, viewportWidth * 0.96); // matches min(73.75rem, 96vw)
      const columnWidth = cardWidthIfTwoCols / 2;
      const tooNarrow = columnWidth < 470;
      const tooShort = viewportHeight < 740;
      setHideRightPanel(tooNarrow || tooShort);
    };
    update();
    window.addEventListener('resize', update);
    window.addEventListener('orientationchange', update);
    return () => {
      window.removeEventListener('resize', update);
      window.removeEventListener('orientationchange', update);
    };
  }, []);

  return (
    <div className={styles.authContainer}>
      <div
        ref={cardRef}
        className={`${styles.authCard} ${!hideRightPanel ? styles.authCardTwoColumns : ''}`}
        style={{ marginBottom: 'auto' }}
      >
        <div className={styles.authLeftPanel}>
          <div className={styles.authContent}>
            {children}
          </div>
        </div>
        {!hideRightPanel && (
          <LoginRightCarousel imageSlides={loginSlides} initialSeconds={5} slideSeconds={8} />
        )}
      </div>
    </div>
  );
}

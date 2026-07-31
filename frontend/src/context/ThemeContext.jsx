// TICKET-ADV124 — ThemeProvider: context flips data-theme; CSS owns colours.
import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

const STORAGE_KEY = 'reconx-theme';

const ThemeContext = createContext(null);

/**
 * Lazy initialiser — runs once, before first paint.
 *
 * Order matters: an explicit stored choice always beats the OS preference,
 * otherwise a user who picked light on a dark-mode machine would be overridden
 * on every visit. Only when nothing is stored do we fall back to
 * prefers-color-scheme, which is what makes a first-time visitor on a dark
 * system land in dark mode.
 *
 * Guarded for a non-browser environment (jsdom without matchMedia, SSR) so the
 * provider can be rendered in tests without blowing up.
 */
function initialTheme() {
  if (typeof window === 'undefined') return 'light';

  const stored = window.localStorage?.getItem(STORAGE_KEY);
  if (stored === 'light' || stored === 'dark') return stored;

  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(initialTheme);

  // The single side effect: flip one attribute on <html> and persist the
  // choice. No colour values live in JS — [data-theme="dark"] in global.css
  // owns every token, so restyling is CSS's job the instant this lands.
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try {
      window.localStorage?.setItem(STORAGE_KEY, theme);
    } catch {
      // Private browsing / storage disabled — the theme still applies for
      // this session, it just won't survive a reload.
    }
  }, [theme]);

  const toggle = useCallback(
    () => setTheme((prev) => (prev === 'light' ? 'dark' : 'light')),
    [],
  );

  // Memoised so consumers don't re-render on every provider render — the
  // object identity only changes when the theme actually changes.
  const value = useMemo(() => ({ theme, setTheme, toggle }), [theme, toggle]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error('useTheme() must be called inside a <ThemeProvider>');
  }
  return ctx;
}

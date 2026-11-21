import React, { createContext, useContext, useState, useMemo, ReactNode, useEffect } from 'react';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import Cookies from 'js-cookie';

type ThemeMode = 'light' | 'dark';

// "Stiller Frachtbrief" design tokens: warm graphite neutrals, one ochre accent.
// Semantics: ochre = act/selected, green = arrived, coral = handle with care. All text pairs are >= 4.5:1 (see App.test.tsx).
export const PRIMARY = {
  light: { main: '#a35c00', dark: '#824800', light: '#b86d10', contrastText: '#ffffff' },
  dark: { main: '#e6a94c', dark: '#cf9338', light: '#efc17a', contrastText: '#1c1405' },
} as const;

export const SURFACE = {
  light: { bg: '#f3f1ea', paper: '#fbfaf6', raised: '#ffffff', sunk: '#ebe8de', outline: '#8a8678', line: '#dedbd0', text: '#1b1e1c', muted: '#55594f' },
  dark: { bg: '#121413', paper: '#1a1d1b', raised: '#232724', sunk: '#0d0f0e', outline: '#6b746d', line: '#2f3531', text: '#ebede7', muted: '#a4aba3' },
} as const;

/** Semantic status colours per theme (text/icon on bg, and ink for filled use). */
export const STATUS = {
  light: { done: '#1d6a43', warn: '#a6402a' },
  dark: { done: '#86cfa6', warn: '#f4a08a' },
} as const;

/** Shipping-tag colour for the box number: same in both themes, always paired with dark ink. */
export const TILE = { bg: '#e6a94c', ink: '#1c1405' } as const;

export const FONT_UI = '"Schibsted Grotesk Variable", system-ui, -apple-system, "Segoe UI", sans-serif';
export const FONT_MONO = '"IBM Plex Mono", ui-monospace, Consolas, monospace';

interface ThemeContextType {
  mode: ThemeMode;
  toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export const useThemeContext = () => {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useThemeContext must be used within CookieThemeProvider');
  }
  return context;
};

interface CookieThemeProviderProps {
  children: ReactNode;
}

export const CookieThemeProvider: React.FC<CookieThemeProviderProps> = ({ children }) => {
  const [mode, setMode] = useState<ThemeMode>(() => {
    const savedMode = Cookies.get('theme-mode') as ThemeMode | undefined;
    return savedMode && (savedMode === 'light' || savedMode === 'dark') ? savedMode : 'dark';
  });

  useEffect(() => {
    Cookies.set('theme-mode', mode, { expires: 365 });
  }, [mode]);

  const theme = useMemo(() => {
    const S = SURFACE[mode];
    return createTheme({
        palette: {
          mode,
          primary: PRIMARY[mode],
          secondary: mode === 'light' ? { main: '#3d4a41' } : { main: '#c9d0c7', contrastText: '#121413' },
          warning: mode === 'light' ? { main: '#8a5300', contrastText: '#ffffff' } : { main: '#f4a08a', contrastText: '#1c0a05' },
          error: mode === 'light' ? { main: '#b3261e', contrastText: '#ffffff' } : { main: '#ff9d94', contrastText: '#1c0805' },
          success: mode === 'light' ? { main: '#1d6a43', contrastText: '#ffffff' } : { main: '#86cfa6', contrastText: '#08170f' },
          info: mode === 'light' ? { main: '#1f5f8b', contrastText: '#ffffff' } : { main: '#9ccbf0', contrastText: '#08141c' },
          background: { default: S.bg, paper: S.paper },
          text: { primary: S.text, secondary: S.muted },
          divider: S.line,
        },
        shape: { borderRadius: 10 },
        typography: {
          fontFamily: FONT_UI,
          h1: { fontWeight: 700, letterSpacing: '-0.02em' },
          h2: { fontWeight: 700, letterSpacing: '-0.02em' },
          h3: { fontWeight: 700, letterSpacing: '-0.015em' },
          h4: { fontWeight: 700, letterSpacing: '-0.015em' },
          h5: { fontWeight: 700, letterSpacing: '-0.01em' },
          h6: { fontWeight: 650, letterSpacing: '-0.005em' },
          subtitle1: { fontWeight: 600 },
          overline: { fontFamily: FONT_MONO, letterSpacing: '0.08em' },
          button: { fontWeight: 650, textTransform: 'none', letterSpacing: 0 },
        },
        components: {
          MuiCssBaseline: {
            styleOverrides: {
              html: { colorScheme: mode },
              'input, button, textarea, select': { fontFamily: 'inherit' },
              ':focus-visible': {
                outline: `2px solid ${PRIMARY[mode].main}`,
                outlineOffset: 2,
              },
              '@media (prefers-reduced-motion: reduce)': {
                '*, *::before, *::after': { animationDuration: '0.01ms !important', transitionDuration: '0.01ms !important', scrollBehavior: 'auto !important' },
              },
            },
          },
          MuiPaper: {
            styleOverrides: {
              root: { backgroundImage: 'none' },
              outlined: { border: `1px solid ${S.line}`, backgroundColor: S.paper },
            },
          },
          MuiAppBar: {
            defaultProps: { color: 'default', elevation: 0 },
            styleOverrides: {
              root: { backgroundColor: S.bg, color: S.text, borderBottom: `1px solid ${S.line}`, backgroundImage: 'none' },
            },
          },
          MuiChip: {
            styleOverrides: { root: { borderRadius: 6, fontWeight: 600, fontFamily: FONT_MONO, fontSize: '0.75rem' } },
          },
          MuiOutlinedInput: {
            styleOverrides: {
              root: { borderRadius: 10, backgroundColor: S.paper },
              notchedOutline: { borderWidth: 1, borderColor: S.outline },
            },
          },
          MuiDialog: {
            styleOverrides: { paper: { border: `1px solid ${S.line}`, borderRadius: 16, backgroundColor: S.raised } },
          },
          MuiDrawer: {
            styleOverrides: { paper: { backgroundColor: S.raised, backgroundImage: 'none' } },
          },
          MuiMenu: {
            styleOverrides: { paper: { backgroundColor: S.raised, border: `1px solid ${S.line}`, borderRadius: 12 } },
          },
          MuiButton: {
            styleOverrides: {
              root: { borderRadius: 10, minHeight: 44, boxShadow: 'none', '&:hover': { boxShadow: 'none' } },
              outlined: { borderColor: S.outline, color: S.text, '&:hover': { borderColor: S.muted, backgroundColor: 'transparent' } },
              sizeLarge: { minHeight: 52, fontSize: '1rem' },
            },
          },
          MuiFab: {
            styleOverrides: {
              root: { width: 56, height: 56, borderRadius: 18, boxShadow: 'none' },
            },
          },
          MuiIconButton: {
            styleOverrides: { root: { minWidth: 44, minHeight: 44 } },
          },
        },
    });
  }, [mode]);

  const toggleTheme = () => {
    setMode((prevMode) => (prevMode === 'light' ? 'dark' : 'light'));
  };

  return (
    <ThemeContext.Provider value={{ mode, toggleTheme }}>
      <ThemeProvider theme={theme}>{children}</ThemeProvider>
    </ThemeContext.Provider>
  );
};

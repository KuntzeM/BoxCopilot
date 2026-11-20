import React, { createContext, useContext, useState, useMemo, ReactNode, useEffect } from 'react';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import Cookies from 'js-cookie';

type ThemeMode = 'light' | 'dark';

// "Cargo Bold" design tokens. WCAG AA ratios verified in App.test.tsx.
// light: white on #b04a14 = 5.47; dark: #1a0d05 on #f08a4b = 7.65, #f08a4b on #11100f = 7.64
export const PRIMARY = {
  light: { main: '#b04a14', dark: '#8f3a0f', light: '#c95f26', contrastText: '#ffffff' },
  dark: { main: '#f08a4b', dark: '#d96f2e', light: '#f6a572', contrastText: '#1a0d05' },
} as const;

export const SURFACE = {
  light: { bg: '#faf8f5', paper: '#ffffff', sunk: '#f2eee8', outline: '#7a6f63', line: '#e6e0d8', text: '#1b1713', muted: '#6b635a' },
  dark: { bg: '#11100f', paper: '#1f1c19', sunk: '#0b0a09', outline: '#6b6258', line: '#2a2622', text: '#f3efea', muted: '#a39c94' },
} as const;

/** Orange label tile colour: same in both themes, always paired with dark ink. */
export const TILE = { bg: '#f08a4b', ink: '#1a0d05' } as const;

export const FONT_UI = '"Bricolage Grotesque Variable", system-ui, -apple-system, "Segoe UI", sans-serif';
export const FONT_MONO = '"JetBrains Mono", ui-monospace, Consolas, monospace';

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
          secondary: mode === 'light' ? { main: '#4a423a' } : { main: '#d9cfc4', contrastText: '#1a0d05' },
          // Contrast (approx): light white on #9a5a00 5.6, #b3261e 6.5, #2e6b34 6.4, #1f5f8b 6.9;
          // dark #1a0d05 on #ffa726 9.6, #ffb4ab 10.3, #8fd29a 11, #9ccbf0 10.5
          warning: mode === 'light' ? { main: '#9a5a00', contrastText: '#ffffff' } : { main: '#ffa726', contrastText: '#1a0d05' },
          error: mode === 'light' ? { main: '#b3261e', contrastText: '#ffffff' } : { main: '#ffb4ab', contrastText: '#1a0d05' },
          success: mode === 'light' ? { main: '#2e6b34', contrastText: '#ffffff' } : { main: '#8fd29a', contrastText: '#1a0d05' },
          info: mode === 'light' ? { main: '#1f5f8b', contrastText: '#ffffff' } : { main: '#9ccbf0', contrastText: '#1a0d05' },
          background: { default: S.bg, paper: S.paper },
          text: { primary: S.text, secondary: S.muted },
          divider: S.line,
        },
        shape: { borderRadius: 8 },
        typography: {
          fontFamily: FONT_UI,
          h1: { fontWeight: 800, letterSpacing: '-0.03em' },
          h2: { fontWeight: 800, letterSpacing: '-0.03em' },
          h3: { fontWeight: 800, letterSpacing: '-0.02em' },
          h4: { fontWeight: 800, letterSpacing: '-0.02em' },
          h5: { fontWeight: 800, letterSpacing: '-0.02em' },
          h6: { fontWeight: 800, letterSpacing: '-0.01em' },
          subtitle1: { fontWeight: 700 },
          button: { fontWeight: 800, textTransform: 'none', letterSpacing: 0 },
        },
        components: {
          MuiCssBaseline: {
            styleOverrides: {
              html: { colorScheme: mode },
              'input, button, textarea, select': { fontFamily: 'inherit' },
              ':focus-visible': {
                outline: `3px solid ${mode === 'light' ? PRIMARY.light.main : PRIMARY.dark.main}`,
                outlineOffset: 2,
              },
            },
          },
          MuiPaper: {
            styleOverrides: {
              root: { backgroundImage: 'none' },
              outlined: { border: `2px solid ${S.outline}` },
            },
          },
          MuiAppBar: {
            defaultProps: { color: 'default', elevation: 0 },
            styleOverrides: {
              root: { backgroundColor: S.sunk, color: S.text, borderBottom: `2px solid ${S.outline}`, backgroundImage: 'none' },
            },
          },
          MuiChip: {
            styleOverrides: { root: { borderRadius: 4, fontWeight: 800 } },
          },
          MuiOutlinedInput: {
            styleOverrides: {
              root: { borderRadius: 8, backgroundColor: S.paper },
              notchedOutline: { borderWidth: 2, borderColor: S.outline },
            },
          },
          MuiDialog: {
            styleOverrides: { paper: { border: `2px solid ${S.outline}`, borderRadius: 12 } },
          },
          MuiButton: {
            styleOverrides: {
              root: { borderRadius: 8, minHeight: 48 },
              outlined: { borderWidth: 2, '&:hover': { borderWidth: 2 } },
              sizeLarge: {
                minHeight: 56,
                fontSize: '1.1rem',
              },
            },
          },
          MuiFab: {
            styleOverrides: {
              root: {
                width: 56,
                height: 56,
                borderRadius: 10,
                border: `3px solid ${S.sunk}`,
                boxShadow: 'none',
              },
            },
          },
          MuiIconButton: {
            styleOverrides: {
              root: {
                minWidth: 44,
                minHeight: 44,
              },
            },
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

import React, { createContext, useContext, useState, useMemo, ReactNode, useEffect } from 'react';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import Cookies from 'js-cookie';

type ThemeMode = 'light' | 'dark';

// WCAG AA primaries (ratios computed with the WCAG relative-luminance formula, verified in App.test.tsx):
// light: white on #b04a14 = 5.47, #b04a14 on #fff = 5.47, on #f5f5f5 = 5.02
// dark:  #121212 on #e8793a = 6.45, #e8793a on #1e1e1e = 5.74, on #121212 = 6.45
export const PRIMARY = {
  light: { main: '#b04a14', dark: '#8f3a0f', light: '#c95f26', contrastText: '#ffffff' },
  dark: { main: '#e8793a', dark: '#c95f26', light: '#f08a4b', contrastText: '#121212' },
} as const;

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

  const theme = useMemo(
    () =>
      createTheme({
        palette: {
          mode,
          ...(mode === 'light'
            ? {
                // Light mode palette
                primary: PRIMARY.light,
                secondary: {
                  main: '#2196F3',
                },
                background: {
                  default: '#f5f5f5',
                  paper: '#ffffff',
                },
                text: {
                  primary: '#000000',
                  secondary: '#666666',
                },
              }
            : {
                // Dark mode palette
                primary: PRIMARY.dark,
                secondary: {
                  main: '#42A5F5',
                },
                background: {
                  default: '#121212',
                  paper: '#1e1e1e',
                },
                text: {
                  primary: '#ffffff',
                  secondary: '#b0b0b0',
                },
              }),
        },
        typography: {
          fontFamily: 'Roboto, sans-serif',
        },
        components: {
          MuiCssBaseline: {
            styleOverrides: {
              ':focus-visible': {
                outline: `3px solid ${mode === 'light' ? PRIMARY.light.main : PRIMARY.dark.main}`,
                outlineOffset: 2,
              },
            },
          },
          MuiButton: {
            styleOverrides: {
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
      }),
    [mode],
  );

  const toggleTheme = () => {
    setMode((prevMode) => (prevMode === 'light' ? 'dark' : 'light'));
  };

  return (
    <ThemeContext.Provider value={{ mode, toggleTheme }}>
      <ThemeProvider theme={theme}>{children}</ThemeProvider>
    </ThemeContext.Provider>
  );
};

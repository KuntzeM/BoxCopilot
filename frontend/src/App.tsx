import React, { useEffect, useState } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { CssBaseline, Container, AppBar, Toolbar, Typography, Box, Button, CircularProgress, IconButton } from '@mui/material';
import { Inventory2, Login, WbSunny, DarkMode, Logout } from '@mui/icons-material';
import axios from './services/axiosConfig';
import BoxList from './pages/BoxList';
import PublicPreview from './pages/PublicPreview';
import BoxEditPage from './pages/BoxEditPage';
import { CookieThemeProvider, useThemeContext } from './context/ThemeContext';
import { LanguageProvider } from './context/LanguageContext';
import { useTranslation } from './hooks/useTranslation';
import LanguageSelector from './components/LanguageSelector';

function LoadingScreen() {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
      <CircularProgress />
    </Box>
  );
}

function LoginScreen({ onLogin }: { onLogin: () => void }) {
  const { t } = useTranslation();
  
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh',
        gap: 3,
        px: 2,
      }}
    >
      <Inventory2 sx={{ fontSize: 80, color: 'primary.main' }} />
      <Typography variant="h3" component="h1" align="center">
        {t('app.title')}
      </Typography>
      <Typography variant="body1" color="text.secondary" align="center">
        {t('auth.loginPrompt')}
      </Typography>
      <Button
        variant="contained"
        size="large"
        startIcon={<Login />}
        onClick={onLogin}
        sx={{ mt: 2 }}
      >
        {t('auth.loginButton')}
      </Button>
    </Box>
  );
}

function ProtectedRoute({
  isAuthenticated,
  isLoading,
  children,
  onLogin,
}: {
  isAuthenticated: boolean;
  isLoading: boolean;
  children: React.ReactElement;
  onLogin: () => void;
}) {
  const location = useLocation();

  if (isLoading) {
    return <LoadingScreen />;
  }

  if (!isAuthenticated) {
    return <LoginScreen onLogin={onLogin} key={location.pathname} />;
  }

  return children;
}

function AppShell({ children }: { children: React.ReactNode }) {
  const { toggleTheme, mode } = useThemeContext();
  const { t } = useTranslation();

  const handleLogout = async () => {
    try {
      await axios.post('/api/auth/logout');
      window.location.href = '/';
    } catch (error) {
      console.error('Logout failed:', error);
      // Force reload anyway to clear state
      window.location.href = '/';
    }
  };

  return (
    <>
      <AppBar position="static" elevation={2}>
        <Toolbar>
          <Inventory2 sx={{ mr: 2 }} />
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            {t('app.title')}
          </Typography>
          <IconButton
            onClick={toggleTheme}
            color="inherit"
            aria-label={t('theme.toggleDarkMode')}
            size="medium"
          >
            {mode === 'light' ? <DarkMode /> : <WbSunny />}
          </IconButton>
          <LanguageSelector />
          <Button
            color="inherit"
            startIcon={<Logout />}
            onClick={handleLogout}
            sx={{ ml: 2 }}
          >
            {t('auth.logout')}
          </Button>
        </Toolbar>
      </AppBar>
      <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
        {children}
      </Container>
    </>
  );
}

export default function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const checkAuth = async () => {
      try {
        // Create a temporary axios instance without credentials to avoid CORS issues
        const tempAxios = axios.create({
          baseURL: axios.defaults.baseURL,
          withCredentials: true,
          timeout: 5000,
        });
        const response = await tempAxios.get('/api/v1/me');
        setIsAuthenticated(response.data.authenticated === true);
        // Capture CSRF token from backend and set it for subsequent unsafe requests
        if (response.data?.csrfToken) {
          axios.defaults.headers.common['X-XSRF-TOKEN'] = response.data.csrfToken;
        }
      } catch (error) {
        // If the request fails (401, network error, etc.), user is not authenticated
        setIsAuthenticated(false);
      } finally {
        setIsLoading(false);
      }
    };

    checkAuth();
  }, []);

  const handleLogin = () => {
    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;
    window.location.href = `${apiBaseUrl}/oauth2/authorization/nextcloud`;
  };

  return (
    <CookieThemeProvider>
      <LanguageProvider>
        <CssBaseline />
        <Routes>
          <Route path="/public/:token" element={<PublicPreview />} />
          <Route
            path="/app/boxes"
            element={
              <ProtectedRoute isAuthenticated={isAuthenticated} isLoading={isLoading} onLogin={handleLogin}>
                <AppShell>
                  <BoxList />
                </AppShell>
              </ProtectedRoute>
            }
          />
          <Route
            path="/app/boxes/:id/edit"
            element={
              <ProtectedRoute isAuthenticated={isAuthenticated} isLoading={isLoading} onLogin={handleLogin}>
                <AppShell>
                  <BoxEditPage />
                </AppShell>
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<Navigate to="/app/boxes" replace />} />
        </Routes>
      </LanguageProvider>
    </CookieThemeProvider>
  );
}

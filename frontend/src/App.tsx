import React, { useEffect, useState } from 'react';
import { Routes, Route, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { CssBaseline, Container, AppBar, Toolbar, Typography, Box, Button, CircularProgress, IconButton } from '@mui/material';
import { Inventory2, WbSunny, DarkMode, Logout, AdminPanelSettings } from '@mui/icons-material';
import axios from './services/axiosConfig';
import BoxList from './pages/BoxList';
import PublicPreview from './pages/PublicPreview';
import BoxEditPage from './pages/BoxEditPage';
import LoginPage from './pages/LoginPage';
import AdminPanel from './pages/AdminPanel';
import { CookieThemeProvider, useThemeContext } from './context/ThemeContext';
import { LanguageProvider } from './context/LanguageContext';
import { useTranslation } from './hooks/useTranslation';
import LanguageSelector from './components/LanguageSelector';
import { UserPrincipal } from './types/models';

function LoadingScreen() {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
      <CircularProgress />
    </Box>
  );
}

function ProtectedRoute({
  isAuthenticated,
  isLoading,
  user,
  children,
  requireAdmin = false,
}: {
  isAuthenticated: boolean;
  isLoading: boolean;
  user: UserPrincipal | null;
  children: React.ReactElement;
  requireAdmin?: boolean;
}) {
  const location = useLocation();
  const navigate = useNavigate();

  if (isLoading) {
    return <LoadingScreen />;
  }

  if (!isAuthenticated) {
    return <LoginPage onLoginSuccess={() => window.location.reload()} />;
  }

  // Check admin requirement
  if (requireAdmin && !user?.isAdmin) {
    // Redirect non-admins away from admin routes
    navigate('/app/boxes');
    return null;
  }

  return children;
}

function AppShell({ children, user }: { children: React.ReactNode; user: UserPrincipal | null }) {
  const { toggleTheme, mode } = useThemeContext();
  const { t } = useTranslation();
  const navigate = useNavigate();

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
          {user?.isAdmin && (
            <Button
              color="inherit"
              startIcon={<AdminPanelSettings />}
              onClick={() => navigate('/app/admin')}
              sx={{ ml: 2 }}
            >
              Admin
            </Button>
          )}
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
  const [user, setUser] = useState<UserPrincipal | null>(null);

  useEffect(() => {
    const checkAuth = async () => {
      try {
        const response = await axios.get<UserPrincipal>('/api/v1/me');
        setIsAuthenticated(response.data.authenticated === true);
        setUser(response.data);
        // Capture CSRF token from backend and set it for subsequent unsafe requests
        if (response.data?.csrfToken) {
          axios.defaults.headers.common['X-XSRF-TOKEN'] = response.data.csrfToken;
        }
      } catch (error) {
        // If the request fails (401, network error, etc.), user is not authenticated
        setIsAuthenticated(false);
        setUser(null);
      } finally {
        setIsLoading(false);
      }
    };

    checkAuth();
  }, []);

  return (
    <CookieThemeProvider>
      <LanguageProvider>
        <CssBaseline />
        <Routes>
          <Route path="/public/:token" element={<PublicPreview />} />
          <Route
            path="/app/boxes"
            element={
              <ProtectedRoute isAuthenticated={isAuthenticated} isLoading={isLoading} user={user}>
                <AppShell user={user}>
                  <BoxList />
                </AppShell>
              </ProtectedRoute>
            }
          />
          <Route
            path="/app/boxes/:id/edit"
            element={
              <ProtectedRoute isAuthenticated={isAuthenticated} isLoading={isLoading} user={user}>
                <AppShell user={user}>
                  <BoxEditPage />
                </AppShell>
              </ProtectedRoute>
            }
          />
          <Route
            path="/app/admin"
            element={
              <ProtectedRoute 
                isAuthenticated={isAuthenticated} 
                isLoading={isLoading} 
                user={user}
                requireAdmin={true}
              >
                <AppShell user={user}>
                  <AdminPanel />
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

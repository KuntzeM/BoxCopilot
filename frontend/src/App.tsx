import React, { useEffect, useState } from 'react';
import { Routes, Route, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { 
  CssBaseline, 
  Container, 
  AppBar, 
  Toolbar, 
  Typography, 
  Box, 
  Button, 
  CircularProgress, 
  IconButton,
  Drawer,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemButton,
  Divider,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import { 
  Inventory2, 
  WbSunny, 
  DarkMode, 
  Logout, 
  AdminPanelSettings,
  Menu as MenuIcon,
  Language as LanguageIcon,
} from '@mui/icons-material';
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
import { Role, UserPrincipal } from './types/models';

function LoadingScreen() {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
      <CircularProgress />
    </Box>
  );
}

function hasAdminAccess(user: UserPrincipal | null) {
  if (!user) return false;
  const anyUser = user as any;
  return user.role === Role.ADMIN || user.isAdmin === true || anyUser.admin === true;
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
  if (requireAdmin && !hasAdminAccess(user)) {
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
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

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

  const handleCloseMobileMenu = () => {
    setMobileMenuOpen(false);
  };

  const handleMobileThemeToggle = () => {
    toggleTheme();
    handleCloseMobileMenu();
  };

  const handleMobileAdminClick = () => {
    navigate('/app/admin');
    handleCloseMobileMenu();
  };

  const handleMobileLogout = async () => {
    handleCloseMobileMenu();
    await handleLogout();
  };

  return (
    <>
      <AppBar position="static" elevation={2}>
        <Toolbar>
          <Box
            onClick={() => navigate('/app/boxes')}
            sx={{ display: 'flex', alignItems: 'center', gap: 1, cursor: 'pointer' }}
            aria-label={t('app.title')}
          >
            <Inventory2 />
            <Typography variant="h6" component="div" sx={{ display: { xs: 'none', sm: 'block' } }}>
              {t('app.title')}
            </Typography>
          </Box>
          <Box sx={{ flexGrow: 1 }} />
          
          {/* Mobile: Hamburger Menu */}
          {isMobile && (
            <IconButton
              onClick={() => setMobileMenuOpen(true)}
              color="inherit"
              aria-label="Menu"
              size="large"
              sx={{ minWidth: 48, minHeight: 48 }}
            >
              <MenuIcon />
            </IconButton>
          )}
          
          {/* Desktop: All Controls */}
          {!isMobile && (
            <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
              <IconButton
                onClick={toggleTheme}
                color="inherit"
                aria-label={t('theme.toggleDarkMode')}
                size="medium"
              >
                {mode === 'light' ? <DarkMode /> : <WbSunny />}
              </IconButton>
              <LanguageSelector variant="icon" />
              {hasAdminAccess(user) && (
                <Button
                  color="inherit"
                  startIcon={<AdminPanelSettings />}
                  onClick={() => navigate('/app/admin')}
                  sx={{ ml: 1 }}
                >
                  Admin
                </Button>
              )}
              <Button
                color="inherit"
                startIcon={<Logout />}
                onClick={handleLogout}
                sx={{ ml: 1 }}
              >
                {t('auth.logout')}
              </Button>
            </Box>
          )}
        </Toolbar>
      </AppBar>

      {/* Mobile Menu Drawer */}
      <Drawer
        anchor="right"
        open={mobileMenuOpen}
        onClose={handleCloseMobileMenu}
        PaperProps={{
          sx: { width: 280 },
        }}
      >
        <Box sx={{ pt: 2 }}>
          <List>
            {/* User Info */}
            <ListItem sx={{ pb: 2 }}>
              <Box>
                <Typography variant="subtitle1" fontWeight={600}>
                  {user?.name || user?.username}
                </Typography>
                {user?.name && user?.username && (
                  <Typography variant="caption" color="text.secondary">
                    {user.username}
                  </Typography>
                )}
              </Box>
            </ListItem>
            <Divider />

            {/* Theme Toggle */}
            <ListItem sx={{ minHeight: 56 }}>
              <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                <IconButton
                  onClick={handleMobileThemeToggle}
                  color="inherit"
                  aria-label={t('theme.toggleDarkMode')}
                  size="large"
                >
                  {mode === 'dark' ? <WbSunny /> : <DarkMode />}
                </IconButton>
                <LanguageSelector variant="icon" buttonSx={{ ml: 0 }} />
              </Box>
            </ListItem>

            <Divider />

            {/* Admin Access */}
            {hasAdminAccess(user) && (
              <ListItemButton onClick={handleMobileAdminClick} sx={{ minHeight: 56 }}>
                <ListItemIcon>
                  <AdminPanelSettings color="primary" />
                </ListItemIcon>
                <ListItemText primary="Admin" />
              </ListItemButton>
            )}

            {/* Logout */}
            <ListItemButton onClick={handleMobileLogout} sx={{ minHeight: 56 }}>
              <ListItemIcon>
                <Logout color="error" />
              </ListItemIcon>
              <ListItemText primary={t('auth.logout')} />
            </ListItemButton>
          </List>
        </Box>
      </Drawer>

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
        const normalizedUser: UserPrincipal = {
          ...response.data,
          isAdmin:
            response.data.isAdmin ?? (response.data as any).admin ?? response.data.role === Role.ADMIN,
        };
        setIsAuthenticated(normalizedUser.authenticated === true);
        setUser(normalizedUser);
        // Capture CSRF token from backend and set it for subsequent unsafe requests
        if (normalizedUser?.csrfToken) {
          axios.defaults.headers.common['X-XSRF-TOKEN'] = normalizedUser.csrfToken;
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

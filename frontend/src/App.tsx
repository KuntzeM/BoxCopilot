import React, { Suspense, lazy, useEffect, useState } from 'react';
import { Routes, Route, Navigate, Link as RouterLink, useLocation, useNavigate } from 'react-router-dom';
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
import SmartToyIcon from '@mui/icons-material/SmartToy';
import axios from './services/axiosConfig';
import PublicPreview from './pages/PublicPreview';
import LoginPage from './pages/LoginPage';
import MagicLoginPage from './pages/MagicLoginPage';
import ClaudeConnectionDialog from './components/ClaudeConnectionDialog';
import { CookieThemeProvider, useThemeContext } from './context/ThemeContext';
import { LanguageProvider } from './context/LanguageContext';
import { useTranslation } from './hooks/useTranslation';
import { useMcpContinue } from './hooks/useMcpContinue';
import LanguageSelector from './components/LanguageSelector';
import { Role, UserPrincipal } from './types/models';

// Authenticated-only pages are split out so /public/:token and login stay light.
const BoxList = lazy(() => import('./pages/BoxList'));
const BoxEditPage = lazy(() => import('./pages/BoxEditPage'));
const AdminPanel = lazy(() => import('./pages/AdminPanel'));

function LoadingScreen() {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
      <CircularProgress />
    </Box>
  );
}

function hasAdminAccess(user: UserPrincipal | null) {
  if (!user) return false;
  return user.role === Role.ADMIN || user.admin === true;
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
  if (isLoading) {
    return <LoadingScreen />;
  }

  if (!isAuthenticated) {
    return <LoginPage onLoginSuccess={() => window.location.reload()} />;
  }

  // Check admin requirement
  if (requireAdmin && !hasAdminAccess(user)) {
    return <Navigate to="/app/boxes" replace />;
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
  const [claudeDialogOpen, setClaudeDialogOpen] = useState(false);

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

  const handleMobileClaudeClick = () => {
    setClaudeDialogOpen(true);
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
            component={RouterLink}
            to="/app/boxes"
            sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'inherit', textDecoration: 'none' }}
            aria-label={t('app.title')}
          >
            <Inventory2 />
            <Typography variant="h6" component="div" sx={{ display: { xs: 'none', sm: 'block' } }}>
              {t('app.title')}
            </Typography>
          </Box>

          {/* Mobile: Username next to icon */}
          {isMobile && user && (
            <Typography variant="h6" component="div" sx={{ fontWeight: 600, ml: 1 }}>
              {user.name || user.username}
            </Typography>
          )}

          <Box sx={{ flexGrow: 1 }} />
          
          {/* Mobile: Hamburger Menu */}
          {isMobile && (
            <IconButton
              onClick={() => setMobileMenuOpen(true)}
              color="inherit"
              aria-label={t('app.menu')}
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
              {user && (
                <Typography variant="body2" sx={{ ml: 1, fontWeight: 600 }}>
                  {user.name || user.username}
                </Typography>
              )}
              {hasAdminAccess(user) && (
                <Button
                  color="inherit"
                  startIcon={<AdminPanelSettings />}
                  onClick={() => navigate('/app/admin')}
                  sx={{ ml: 1 }}
                >
                  {t('app.admin')}
                </Button>
              )}
              {user?.mcpAllowed === true && (
                <Button
                  color="inherit"
                  startIcon={<SmartToyIcon />}
                  onClick={() => setClaudeDialogOpen(true)}
                  sx={{ ml: 1 }}
                >
                  {t('mcp.title')}
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
        slotProps={{
          paper: { sx: { width: 280 } },
        }}
      >
        <Box sx={{ pt: 2 }}>
          <List>
            {/* User Info */}
            <ListItem sx={{ pb: 2 }}>
              <Box>
                <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
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
                <ListItemText primary={t('app.admin')} />
              </ListItemButton>
            )}

            {/* Claude Connection */}
            {user?.mcpAllowed === true && (
              <ListItemButton onClick={handleMobileClaudeClick} sx={{ minHeight: 56 }}>
                <ListItemIcon>
                  <SmartToyIcon color="primary" />
                </ListItemIcon>
                <ListItemText primary={t('mcp.title')} />
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

      <Container component="main" maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
        <Suspense fallback={<CircularProgress aria-label="loading" />}>{children}</Suspense>
      </Container>

      {user?.mcpAllowed === true && (
        <ClaudeConnectionDialog open={claudeDialogOpen} onClose={() => setClaudeDialogOpen(false)} />
      )}
    </>
  );
}

export default function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [user, setUser] = useState<UserPrincipal | null>(null);
  const location = useLocation();

  useEffect(() => {
    const checkAuth = async () => {
      try {
        const response = await axios.get<UserPrincipal>('/api/v1/me');
        const normalizedUser = response.data;
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

  useMcpContinue(isAuthenticated, user?.mcpPending);

  return (
    <CookieThemeProvider>
      <LanguageProvider>
        <CssBaseline />
        <Routes>
          <Route path="/public/:token" element={<PublicPreview />} />
          <Route path="/magic" element={<MagicLoginPage />} />
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
          {/* keep the query string so LoginPage can read ?error=login_failed */}
          <Route path="*" element={<Navigate to={{ pathname: '/app/boxes', search: location.search }} replace />} />
        </Routes>
      </LanguageProvider>
    </CookieThemeProvider>
  );
}

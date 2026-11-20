import React, { Fragment, Suspense, lazy, useEffect, useState } from 'react';
import { Routes, Route, Navigate, Link as RouterLink, useLocation, useMatch, useNavigate, useOutlet } from 'react-router-dom';
import { 
  CssBaseline, 
  Container, 
  AppBar, 
  Toolbar, 
  Typography, 
  Box, 
  Button, 
  Paper,
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
  Menu as MenuIcon,
  Language as LanguageIcon,
  AdminPanelSettings,
  SmartToy,
} from '@mui/icons-material';
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
import ErrorBoundary from './components/ErrorBoundary';

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
  // <900px: bottom nav (+ hamburger for theme/language/logout); >=900px: side rail + full top bar.
  // jsdom has no matchMedia, so tests render the wide (rail) layout.
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  // Hooks must run unconditionally: a short-circuited useMatch crashed the app when crossing 900px live.
  const onBoxes = !!useMatch('/app/boxes/*');
  const onBoxList = !!useMatch('/app/boxes');
  const wideSplit = !isMobile && onBoxes;
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [claudeDialogOpen, setClaudeDialogOpen] = useState(false);

  const handleLogout = async () => {
    try {
      // Same-origin answer {redirectUrl}: the IdP end-session URL (OIDC users) or the frontend itself.
      const { data } = await axios.post<{ redirectUrl?: string }>('/api/auth/logout');
      window.location.assign(data?.redirectUrl || '/');
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

  const handleMobileLogout = async () => {
    handleCloseMobileMenu();
    await handleLogout();
  };

  return (
    <Box>
      <AppBar position="static" elevation={2}>
        <Toolbar>
          <Box
            component={RouterLink}
            to="/app/boxes"
            sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'inherit', textDecoration: 'none' }}
            aria-label={t('app.title')}
          >
            <Inventory2 />
            <Typography variant="h6" component="div">
              {t('app.title')}
            </Typography>
          </Box>

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
              {hasAdminAccess(user) && (
                <Button color="inherit" startIcon={<AdminPanelSettings />} component={RouterLink} to="/app/admin">
                  {t('app.admin')}
                </Button>
              )}
              {user?.mcpAllowed === true && (
                <Button color="inherit" startIcon={<SmartToy />} onClick={() => setClaudeDialogOpen(true)}>
                  {t('mcp.title')}
                </Button>
              )}
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

            {hasAdminAccess(user) && (
              <ListItemButton component={RouterLink} to="/app/admin" onClick={handleCloseMobileMenu} sx={{ minHeight: 56 }}>
                <ListItemIcon><AdminPanelSettings /></ListItemIcon>
                <ListItemText primary={t('app.admin')} />
              </ListItemButton>
            )}
            {user?.mcpAllowed === true && (
              <ListItemButton onClick={() => { handleCloseMobileMenu(); setClaudeDialogOpen(true); }} sx={{ minHeight: 56 }}>
                <ListItemIcon><SmartToy /></ListItemIcon>
                <ListItemText primary={t('mcp.title')} />
              </ListItemButton>
            )}

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

      <Container
        component="main"
        maxWidth={wideSplit ? false : 'lg'}
        sx={{ mt: wideSplit ? 2 : 4, mb: 4, ...(wideSplit && { maxWidth: 1440 }), ...(isMobile && { maxWidth: onBoxList ? 960 : 720 }) }}
      >
        <ErrorBoundary>
          <Suspense fallback={<CircularProgress aria-label="loading" />}>{children}</Suspense>
        </ErrorBoundary>
      </Container>


      {user?.mcpAllowed === true && (
        <ClaudeConnectionDialog open={claudeDialogOpen} onClose={() => setClaudeDialogOpen(false)} />
      )}
    </Box>
  );
}

/**
 * Boxes list + optional edit child. <900px: single pane (list OR edit, as before).
 * >=900px: master-detail; the list stays mounted while the selected box changes.
 */
function BoxesLayout() {
  const outlet = useOutlet();
  const id = useMatch('/app/boxes/:id/edit')?.params.id;
  // One stable tree for every width (BoxList never remounts); <900px just hides the inactive pane via CSS.
  const narrow = (show: boolean) => ({ xs: show ? 'block' : 'none', md: 'block' });

  return (
    <Box sx={{ display: { md: 'flex' }, gap: 3, alignItems: 'flex-start' }}>
      <Box
        sx={{
          display: narrow(!outlet),
          width: { md: 'clamp(360px, 36vw, 460px)' },
          flexShrink: 0,
          overflowX: 'hidden',
          position: { md: 'sticky' },
          top: 16,
          maxHeight: { md: 'calc(100vh - 96px)' },
          overflowY: { md: 'auto' },
          // Breathing room between cards and the scrollbar; thin scrollbar, gutter reserved so width never jumps.
          pr: { md: 2 },
          scrollbarGutter: { md: 'stable' },
          scrollbarWidth: 'thin',
        }}
      >
        <BoxList />
      </Box>
      <Box sx={{ display: narrow(!!outlet), flex: { md: 1 }, minWidth: 0, maxWidth: { md: 900 } }}>
        {outlet ? (
          <Fragment key={id}>{outlet}</Fragment>
        ) : (
          <Paper variant="outlined" sx={{ minHeight: 240, display: 'grid', placeItems: 'center', color: 'text.disabled' }}>
            <Inventory2 sx={{ fontSize: 64 }} />
          </Paper>
        )}
      </Box>
    </Box>
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
                  <BoxesLayout />
                </AppShell>
              </ProtectedRoute>
            }
          >
            <Route path=":id/edit" element={<BoxEditPage />} />
          </Route>
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

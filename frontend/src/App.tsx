import React, { Fragment, Suspense, lazy, useEffect, useState } from 'react';
import { Routes, Route, Navigate, Link as RouterLink, useLocation, useMatch, useNavigate, useOutlet } from 'react-router-dom';
import { 
  CssBaseline, 
  Container, 
  AppBar, 
  Toolbar, 
  Typography, 
  Box, 
  Paper,
  CircularProgress, 
  IconButton,
  Avatar,
  ButtonBase,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Divider,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import {
  Inventory2,
  WbSunny,
  DarkMode,
  Logout,
  Add,
  Search,
  Language as LanguageIcon,
  AdminPanelSettings,
  SmartToy,
} from '@mui/icons-material';
import axios from './services/axiosConfig';
import PublicPreview from './pages/PublicPreview';
import LoginPage from './pages/LoginPage';
import MagicLoginPage from './pages/MagicLoginPage';
import ClaudeConnectionDialog from './components/ClaudeConnectionDialog';
import { CookieThemeProvider, useThemeContext, FONT_MONO } from './context/ThemeContext';
import { LanguageProvider } from './context/LanguageContext';
import { useTranslation } from './hooks/useTranslation';
import { useMcpContinue } from './hooks/useMcpContinue';
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

/** Navigation intent passed via router state so the thumb bar / rail work from every screen (BoxList reads it). */
export type NavIntent = { intent: 'search' | 'new' | 'item'; at: number };

function AppShell({ children, user }: { children: React.ReactNode; user: UserPrincipal | null }) {
  const { toggleTheme, mode } = useThemeContext();
  const { t, language, setLanguage } = useTranslation();
  const navigate = useNavigate();
  const theme = useTheme();
  // <900px: bottom thumb bar; >=900px: left rail. jsdom has no matchMedia, so tests render the wide (rail) layout.
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  // Hooks must run unconditionally: a short-circuited useMatch crashed the app when crossing 900px live.
  const onBoxes = !!useMatch('/app/boxes/*');
  const onBoxList = !!useMatch('/app/boxes');
  const wideSplit = !isMobile && onBoxes;
  const onBoxEdit = !!useMatch('/app/boxes/:id/edit');
  const { pathname } = useLocation();
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [claudeDialogOpen, setClaudeDialogOpen] = useState(false);
  const isAdmin = hasAdminAccess(user);
  const displayName = user?.name || user?.username || '';
  const initial = (displayName.trim()[0] || '?').toUpperCase();

  const handleLogout = async () => {
    setMenuAnchor(null);
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

  const go = (intent?: NavIntent['intent']) =>
    navigate('/app/boxes', { state: intent ? ({ intent, at: Date.now() } satisfies NavIntent) : undefined });
  // Inside a box the primary action adds an item (stay on the page); everywhere else it creates a box.
  const primaryLabel = onBoxEdit ? t('items.addNew') : t('boxes.createNew');
  const primaryAction = () =>
    onBoxEdit ? navigate(pathname, { state: { intent: 'item', at: Date.now() } satisfies NavIntent }) : go('new');
  const closeMenu = () => setMenuAnchor(null);

  const navItemSx = (active: boolean) => ({
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 0.25,
    minWidth: 64,
    minHeight: 56,
    borderRadius: 2,
    color: active ? 'primary.main' : 'text.secondary',
    fontFamily: FONT_MONO,
    fontSize: 11,
    fontWeight: 600,
    letterSpacing: '0.04em',
    textTransform: 'uppercase',
    '&:hover': { color: 'text.primary' },
  });

  const newBoxButton = (
    <ButtonBase
      onClick={primaryAction}
      aria-label={primaryLabel}
      sx={{
        width: isMobile ? 60 : 56,
        height: isMobile ? 60 : 56,
        borderRadius: '18px',
        bgcolor: 'primary.main',
        color: 'primary.contrastText',
        mt: isMobile ? -3 : 0,
        border: isMobile ? `4px solid ${theme.palette.background.default}` : 'none',
        '&:hover': { bgcolor: 'primary.dark' },
      }}
    >
      <Add />
    </ButtonBase>
  );

  const navBoxes = (
    <ButtonBase
      component={RouterLink}
      to="/app/boxes"
      aria-current={onBoxes ? 'page' : undefined}
      sx={navItemSx(onBoxes)}
    >
      <Inventory2 />
      {t('boxes.boxes')}
    </ButtonBase>
  );
  const navSearch = (
    <ButtonBase onClick={() => go('search')} sx={navItemSx(false)}>
      <Search />
      {t('app.search')}
    </ButtonBase>
  );
  const hasAdminGroup = isAdmin || user?.mcpAllowed === true;

  return (
    <Box sx={{ minHeight: '100vh' }}>
      <AppBar position="sticky">
        <Toolbar sx={{ gap: 1, minHeight: { xs: 56, md: 64 } }}>
          <Box
            component={RouterLink}
            to="/app/boxes"
            sx={{ display: 'flex', alignItems: 'center', gap: 1.25, color: 'inherit', textDecoration: 'none' }}
            aria-label={t('app.title')}
          >
            <Box
              aria-hidden
              sx={{
                width: 30,
                height: 30,
                borderRadius: '8px 8px 8px 3px',
                border: `2px dashed ${theme.palette.primary.main}`,
                display: 'grid',
                placeItems: 'center',
                color: 'primary.main',
              }}
            >
              <Inventory2 sx={{ fontSize: 16 }} />
            </Box>
            <Typography variant="h6" component="div" sx={{ fontFamily: FONT_MONO, fontWeight: 600, fontSize: '1.05rem' }}>
              {t('app.title')}
            </Typography>
          </Box>

          <Box sx={{ flexGrow: 1 }} />

          <IconButton
            onClick={(e) => setMenuAnchor(e.currentTarget)}
            aria-label={t('app.menu')}
            aria-haspopup="menu"
            aria-expanded={menuAnchor ? 'true' : undefined}
            sx={{ p: 0.5 }}
          >
            <Avatar sx={{ width: 36, height: 36, bgcolor: 'background.paper', color: 'primary.main', border: 1, borderColor: 'divider', fontFamily: FONT_MONO, fontWeight: 600, fontSize: 15 }}>
              {initial}
            </Avatar>
          </IconButton>
        </Toolbar>
      </AppBar>

      {/* Overflow menu: preferences first, admin tucked away in its own small group */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={closeMenu}
        slotProps={{ paper: { sx: { minWidth: 260, mt: 1 } } }}
      >
        <Box role="presentation" sx={{ px: 2, py: 1.25 }}>
          <Typography variant="subtitle1" sx={{ lineHeight: 1.2 }}>{displayName}</Typography>
          {user?.name && user?.username && (
            <Typography variant="caption" color="text.secondary" sx={{ fontFamily: FONT_MONO }}>{user.username}</Typography>
          )}
        </Box>
        <Divider />
        <MenuItem onClick={() => toggleTheme()} sx={{ minHeight: 48 }}>
          <ListItemIcon>{mode === 'light' ? <DarkMode fontSize="small" /> : <WbSunny fontSize="small" />}</ListItemIcon>
          <ListItemText>{t('theme.toggleDarkMode')}</ListItemText>
        </MenuItem>
        <MenuItem onClick={() => setLanguage(language === 'de' ? 'en' : 'de')} sx={{ minHeight: 48 }}>
          <ListItemIcon><LanguageIcon fontSize="small" /></ListItemIcon>
          <ListItemText>{language === 'de' ? t('language.german') : t('language.english')}</ListItemText>
          <Typography variant="caption" color="text.secondary" sx={{ fontFamily: FONT_MONO }}>
            {language === 'de' ? 'DE → EN' : 'EN → DE'}
          </Typography>
        </MenuItem>
        {hasAdminGroup && <Divider />}
        {hasAdminGroup && (
          <Typography role="presentation" variant="overline" color="text.secondary" sx={{ px: 2, pt: 0.5, display: 'block' }}>
            {t('app.administration')}
          </Typography>
        )}
        {isAdmin && (
          <MenuItem component={RouterLink} to="/app/admin" onClick={closeMenu} sx={{ minHeight: 48 }}>
            <ListItemIcon><AdminPanelSettings fontSize="small" /></ListItemIcon>
            <ListItemText>{t('app.admin')}</ListItemText>
          </MenuItem>
        )}
        {user?.mcpAllowed === true && (
          <MenuItem onClick={() => { closeMenu(); setClaudeDialogOpen(true); }} sx={{ minHeight: 48 }}>
            <ListItemIcon><SmartToy fontSize="small" /></ListItemIcon>
            <ListItemText>{t('mcp.title')}</ListItemText>
          </MenuItem>
        )}
        <Divider />
        <MenuItem onClick={handleLogout} sx={{ minHeight: 48 }}>
          <ListItemIcon><Logout fontSize="small" color="error" /></ListItemIcon>
          <ListItemText>{t('auth.logout')}</ListItemText>
        </MenuItem>
      </Menu>

      <Box sx={{ display: 'flex', alignItems: 'flex-start' }}>
        {/* Wide: the same three actions as a left rail */}
        {!isMobile && (
          <Box
            component="nav"
            aria-label={t('app.navigation')}
            sx={{ position: 'sticky', top: 64, width: 88, flexShrink: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1.5, py: 3, borderRight: 1, borderColor: 'divider', minHeight: 'calc(100vh - 64px)' }}
          >
            {newBoxButton}
            {navBoxes}
            {navSearch}
          </Box>
        )}

        <Container
          component="main"
          maxWidth={wideSplit ? false : 'lg'}
          sx={{
            flex: 1,
            minWidth: 0,
            mt: wideSplit ? 2 : { xs: 2, md: 4 },
            mb: isMobile ? 14 : 4,
            ...(wideSplit && { maxWidth: 1440 }),
            ...(isMobile && { maxWidth: onBoxList ? 960 : 720 }),
          }}
        >
          <ErrorBoundary>
            <Suspense fallback={<CircularProgress aria-label="loading" />}>{children}</Suspense>
          </ErrorBoundary>
        </Container>
      </Box>

      {/* Phone: thumb bar with the raised primary action in the centre */}
      {isMobile && (
        <Box
          component="nav"
          aria-label={t('app.navigation')}
          sx={{
            position: 'fixed',
            left: 0,
            right: 0,
            bottom: 0,
            zIndex: 1100,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-around',
            px: 1,
            pt: 0.5,
            pb: 'calc(4px + env(safe-area-inset-bottom))',
            bgcolor: 'background.default',
            borderTop: 1,
            borderColor: 'divider',
          }}
        >
          {navBoxes}
          {newBoxButton}
          {navSearch}
        </Box>
      )}

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

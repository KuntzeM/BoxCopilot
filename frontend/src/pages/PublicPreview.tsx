import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Box, Card, CardContent, CircularProgress, Container, Typography, Alert, Stack, Divider, Chip, AppBar, Toolbar, IconButton, Paper, Dialog, DialogTitle, DialogContent, DialogActions, Button, Avatar } from '@mui/material';
import { Brightness4, Brightness7, Close, Edit } from '@mui/icons-material';
import { QRCodeCanvas } from 'qrcode.react';
import axios from '../services/axiosConfig';
import { fetchPublicPreview } from '../services/publicPreviewService';
import { BoxPreview } from '../types/models';
import { truncateToFirstLine } from '../utils/textUtils';
import { LanguageProvider } from '../context/LanguageContext';
import { CookieThemeProvider, useThemeContext } from '../context/ThemeContext';
import { useTranslation } from '../hooks/useTranslation';
import LanguageSelector from '../components/LanguageSelector';

function PublicPreviewContent() {
  const { token } = useParams();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { mode, toggleTheme } = useThemeContext();
  const [data, setData] = useState<BoxPreview | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fullImageUrl, setFullImageUrl] = useState<string | null>(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoadingAuth, setIsLoadingAuth] = useState(true);

  // Ensure image requests go to the API origin (prod without nginx proxy)
  const apiBase = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');
  const withApiBase = (path: string) =>
    apiBase ? `${apiBase}${path.startsWith('/') ? path : `/${path}`}` : path;
  const resolveImageUrl = (url?: string) => {
    if (!url) return undefined;
    if (url.startsWith('http://') || url.startsWith('https://')) return url;
    return withApiBase(url.startsWith('/') ? url : `/${url}`);
  };

  // Check if user is authenticated
  useEffect(() => {
    const checkAuth = async () => {
      try {
        const response = await axios.get('/api/v1/me', {
          // Accept all statuses so we can handle 3xx/4xx ourselves
          validateStatus: () => true,
        });
        console.log('Auth check response:', response.status, response.data);

        // Treat only explicit 200 + authenticated true as logged in
        const isOk = response.status === 200 && response.data?.authenticated === true;
        setIsAuthenticated(isOk);
      } catch (error: any) {
        console.log('Auth check failed:', error.response?.status, error.message);
        setIsAuthenticated(false);
      } finally {
        setIsLoadingAuth(false);
      }
    };

    checkAuth();
  }, []);

  useEffect(() => {
    const load = async () => {
      if (!token) {
        setError(t('errors.tokenMissing'));
        setIsLoading(false);
        return;
      }
      try {
        const preview = await fetchPublicPreview(token);
        setData(preview);
      } catch (err) {
        setError(t('errors.boxLoadFailed'));
      } finally {
        setIsLoading(false);
      }
    };

    load();
  }, [token, t]);

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error || !data) {
    return (
      <Container maxWidth="sm" sx={{ py: 6 }}>
        <Alert severity="error">{error || t('errors.unknownError')}</Alert>
      </Container>
    );
  }

  const publicUrl = `${window.location.origin}/public/${token}`;

  return (
    <>
      <AppBar position="static" color="default" elevation={1}>
        <Toolbar sx={{ justifyContent: 'space-between' }}>
          {isAuthenticated && (
            <Button
              startIcon={<Edit />}
              variant="contained"
              color="primary"
              onClick={() => navigate(`/app/boxes/${data?.id}/edit`)}
              disabled={!data}
            >
              {t('boxes.edit') || 'Bearbeiten'}
            </Button>
          )}
          <Box sx={{ display: 'flex', gap: 1, ml: 'auto' }}>
            <IconButton onClick={toggleTheme} color="inherit" aria-label={t('theme.toggle')}>
              {mode === 'dark' ? <Brightness7 /> : <Brightness4 />}
            </IconButton>
            <LanguageSelector />
          </Box>
        </Toolbar>
      </AppBar>
      <Container maxWidth="sm" sx={{ py: 4 }}>
        <Card sx={{ boxShadow: 3 }}>
          <CardContent>
            <Stack spacing={2}>
              {(data.isFragile || data.noStack) && (
                <Alert severity="warning" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    {data.isFragile && (
                      <Chip
                        label={`🔔 ${t('boxes.fragile')}`}
                        color="warning"
                        size="small"
                      />
                    )}
                    {data.noStack && (
                      <Chip
                        label={`⛔ ${t('boxes.noStack')}`}
                        color="error"
                        size="small"
                      />
                    )}
                  </Stack>
                </Alert>
              )}
              
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
                <Box>
                  <Typography variant="h5" fontWeight={700}>
                    {t('boxes.boxNumber', { number: data.id })}
                  </Typography>
                  <Typography variant="body1" sx={{ mt: 0.5 }}>
                    {t('boxes.current')}: <strong>{data.currentRoom || '-'}</strong>
                  </Typography>
                  <Typography variant="body1">
                    {t('boxes.target')}: <strong>{data.targetRoom || '-'}</strong>
                  </Typography>
                  {data.description && (
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 1, fontStyle: 'italic' }}>
                      {truncateToFirstLine(data.description)}
                    </Typography>
                  )}
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                    {t('boxes.uuid')}: {data.uuid}
                  </Typography>
                </Box>
                <Box sx={{ display: 'grid', placeItems: 'center' }}>
                  <QRCodeCanvas value={publicUrl} size={120} includeMargin />
                  <Typography variant="caption" sx={{ mt: 1 }}>
                    {t('boxes.publicLink')}
                  </Typography>
                </Box>
              </Box>

              <Divider />

              <Box>
                <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 1 }}>
                  {t('boxes.items')}
                </Typography>
                {data.items && data.items.length > 0 ? (
                  <Stack spacing={1.5}>
                    {data.items.map((item, idx) => (
                      <Paper 
                        key={`${item.name}-${idx}`} 
                        variant="outlined"
                        sx={{ 
                          display: 'flex', 
                          justifyContent: 'space-between', 
                          alignItems: 'center', 
                          p: 1.5,
                          backgroundColor: 'background.default',
                          '&:hover': {
                            backgroundColor: mode === 'dark' ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.02)'
                          }
                        }}
                      >
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                          {item.imageUrl ? (
                            <Avatar
                              src={resolveImageUrl(item.imageUrl)}
                              alt={item.name}
                              sx={{ width: 50, height: 50, cursor: 'pointer' }}
                              onClick={() => item.imageUrl && setFullImageUrl(withApiBase(item.imageUrl.replace('/image', '/image/large')))}
                            />
                          ) : (
                            <Avatar sx={{ width: 50, height: 50, bgcolor: 'grey.300' }}>
                              📦
                            </Avatar>
                          )}
                          <Typography>{item.name}</Typography>
                        </Box>
                      </Paper>
                    ))}
                  </Stack>
                ) : (
                  <Alert severity="info">{t('items.noItems')}</Alert>
                )}
              </Box>
            </Stack>
          </CardContent>
        </Card>

        {/* Full Image Dialog */}
        <Dialog open={fullImageUrl !== null} onClose={() => setFullImageUrl(null)} maxWidth="md" fullWidth>
          <DialogTitle>
            Bild Vorschau
            <IconButton
              onClick={() => setFullImageUrl(null)}
              sx={{ position: 'absolute', right: 8, top: 8 }}
            >
              <Close />
            </IconButton>
          </DialogTitle>
          <DialogContent sx={{ textAlign: 'center', p: 2 }}>
            {fullImageUrl && (
              <img
                src={fullImageUrl}
                alt="Item large"
                style={{ 
                  maxWidth: '100%', 
                  maxHeight: '80vh',
                  objectFit: 'contain'
                }}
                onError={(e) => {
                  // Fallback to thumbnail if large image not available
                  const thumbnailUrl = fullImageUrl.replace('/image/large', '/image');
                  e.currentTarget.src = thumbnailUrl;
                }}
              />
            )}
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setFullImageUrl(null)}>Schließen</Button>
          </DialogActions>
        </Dialog>
      </Container>
    </>
  );
}

export default function PublicPreview() {
  return (
    <CookieThemeProvider>
      <LanguageProvider>
        <PublicPreviewContent />
      </LanguageProvider>
    </CookieThemeProvider>
  );
}


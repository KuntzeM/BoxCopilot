import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Box, Card, CardContent, CircularProgress, Container, Typography, Alert, Stack, Divider, Chip, AppBar, Toolbar, IconButton, Paper } from '@mui/material';
import { Brightness4, Brightness7 } from '@mui/icons-material';
import { QRCodeCanvas } from 'qrcode.react';
import { fetchPublicPreview } from '../services/publicPreviewService';
import { BoxPreview } from '../types/models';
import { truncateToFirstLine } from '../utils/textUtils';
import { LanguageProvider } from '../context/LanguageContext';
import { CookieThemeProvider, useThemeContext } from '../context/ThemeContext';
import { useTranslation } from '../hooks/useTranslation';
import LanguageSelector from '../components/LanguageSelector';

function PublicPreviewContent() {
  const { token } = useParams();
  const { t } = useTranslation();
  const { mode, toggleTheme } = useThemeContext();
  const [data, setData] = useState<BoxPreview | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

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
        <Toolbar sx={{ justifyContent: 'flex-end' }}>
          <IconButton onClick={toggleTheme} color="inherit" aria-label={t('theme.toggle')}>
            {mode === 'dark' ? <Brightness7 /> : <Brightness4 />}
          </IconButton>
          <LanguageSelector />
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
                        <Typography>{item.name}</Typography>
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


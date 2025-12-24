import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Box, Card, CardContent, CircularProgress, Container, Typography, Alert, Stack, Divider } from '@mui/material';
import { QRCodeCanvas } from 'qrcode.react';
import { fetchPublicPreview } from '../services/publicPreviewService';
import { BoxPreview } from '../types/models';
import { truncateToFirstLine } from '../utils/textUtils';

export default function PublicPreview() {
  const { token } = useParams();
  const [data, setData] = useState<BoxPreview | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const load = async () => {
      if (!token) {
        setError('Token fehlt');
        setIsLoading(false);
        return;
      }
      try {
        const preview = await fetchPublicPreview(token);
        setData(preview);
      } catch (err) {
        setError('Box konnte nicht geladen werden.');
      } finally {
        setIsLoading(false);
      }
    };

    load();
  }, [token]);

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
        <Alert severity="error">{error || 'Unbekannter Fehler'}</Alert>
      </Container>
    );
  }

  const publicUrl = `${window.location.origin}/public/${token}`;

  return (
    <Container maxWidth="sm" sx={{ py: 4 }}>
      <Card sx={{ boxShadow: 3 }}>
        <CardContent>
          <Stack spacing={2}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
              <Box>
                <Typography variant="h5" fontWeight={700}>
                  Box #{data.id}
                </Typography>
                <Typography variant="body1" sx={{ mt: 0.5 }}>
                  Aktuell: <strong>{data.currentRoom || '-'}</strong>
                </Typography>
                <Typography variant="body1">
                  Ziel: <strong>{data.targetRoom || '-'}</strong>
                </Typography>
                {data.description && (
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 1, fontStyle: 'italic' }}>
                    {truncateToFirstLine(data.description)}
                  </Typography>
                )}
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                  UUID: {data.uuid}
                </Typography>
              </Box>
              <Box sx={{ display: 'grid', placeItems: 'center' }}>
                <QRCodeCanvas value={publicUrl} size={120} includeMargin />
                <Typography variant="caption" sx={{ mt: 1 }}>
                  Public Link
                </Typography>
              </Box>
            </Box>

            <Divider />

            <Box>
              <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 1 }}>
                Items
              </Typography>
              {data.items && data.items.length > 0 ? (
                <Stack spacing={1.5}>
                  {data.items.map((item, idx) => (
                    <Box key={`${item.name}-${idx}`} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', p: 1.25, borderRadius: 1, border: '1px solid #e0e0e0' }}>
                      <Typography>{item.name}</Typography>
                      <Typography fontWeight={700}>x{item.qty}</Typography>
                    </Box>
                  ))}
                </Stack>
              ) : (
                <Alert severity="info">Keine Items vorhanden.</Alert>
              )}
            </Box>
          </Stack>
        </CardContent>
      </Card>
    </Container>
  );
}

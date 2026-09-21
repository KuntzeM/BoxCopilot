import { Alert, Box, Button, CircularProgress, Container, Paper, Typography } from '@mui/material';
import Inventory2 from '@mui/icons-material/Inventory2';
import { Link as RouterLink } from 'react-router-dom';
import { useTranslation } from '../hooks/useTranslation';
import { useMagicLogin } from '../hooks/useMagicLogin';

export default function MagicLoginPage() {
  const { t } = useTranslation();
  const status = useMagicLogin();

  return (
    <Container maxWidth="sm">
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: '100vh',
          py: 4,
        }}
      >
        <Paper elevation={3} sx={{ p: 4, width: '100%', borderRadius: 2, textAlign: 'center' }}>
          <Inventory2 sx={{ fontSize: 64, color: 'primary.main', mb: 2 }} />
          <Typography variant="h4" component="h1" gutterBottom>
            {t('app.title')}
          </Typography>

          {status === 'processing' ? (
            <Box role="status" sx={{ mt: 3 }}>
              <CircularProgress aria-hidden />
              <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                {t('auth.magicLoginProcessing')}
              </Typography>
            </Box>
          ) : (
            <Box sx={{ mt: 3 }}>
              <Alert severity="error" sx={{ mb: 3, textAlign: 'left' }}>
                {t('auth.magicLoginFailed')}
              </Alert>
              <Button component={RouterLink} to="/" variant="contained" size="large" fullWidth>
                {t('auth.backToLogin')}
              </Button>
            </Box>
          )}
        </Paper>
      </Box>
    </Container>
  );
}

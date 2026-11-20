import { Component, ReactNode } from 'react';
import { Box, Button, Typography } from '@mui/material';
import { useTranslation } from '../hooks/useTranslation';

function Fallback() {
  const { t } = useTranslation();
  return (
    <Box role="alert" sx={{ textAlign: 'center', py: 6 }}>
      <Typography variant="h6" gutterBottom>{t('app.title')}</Typography>
      <Button variant="contained" onClick={() => window.location.reload()}>{t('common.retry')}</Button>
    </Box>
  );
}

/** Catches render errors in routed content and offers a reload instead of a white screen. */
export default class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch(error: unknown) { console.error('Render error:', error); }
  render() { return this.state.failed ? <Fallback /> : this.props.children; }
}

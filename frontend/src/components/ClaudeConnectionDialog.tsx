import { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  IconButton,
  InputAdornment,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { useTranslation } from '../hooks/useTranslation';
import { getMcpConnectorUrl, mcpService } from '../services/mcpService';

interface ClaudeConnectionDialogProps {
  open: boolean;
  onClose: () => void;
}

type ConfirmAction = 'regenerate' | 'revoke';

interface Notice {
  severity: 'success' | 'error';
  message: string;
}

interface CopyableFieldProps {
  label: string;
  value: string;
  copyLabel: string;
  onCopy: (value: string) => void;
}

function CopyableField({ label, value, copyLabel, onCopy }: CopyableFieldProps) {
  return (
    <TextField
      fullWidth
      margin="dense"
      size="small"
      label={label}
      value={value}
      onFocus={(event) => event.target.select()}
      slotProps={{
        input: {
          readOnly: true,
          sx: { fontFamily: 'monospace' },
          endAdornment: (
            <InputAdornment position="end">
              <Tooltip title={copyLabel}>
                <IconButton edge="end" size="small" aria-label={copyLabel} onClick={() => onCopy(value)}>
                  <ContentCopyIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </InputAdornment>
          ),
        },
      }}
    />
  );
}

export default function ClaudeConnectionDialog({ open, onClose }: ClaudeConnectionDialogProps) {
  const { t } = useTranslation();
  const [clientId, setClientId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(null);
  const [busy, setBusy] = useState(false);

  const connectorUrl = getMcpConnectorUrl();

  useEffect(() => {
    if (!open) {
      setClientId(null);
      setLoadFailed(false);
      setNotice(null);
      setConfirmAction(null);
      return;
    }

    let active = true;
    setLoading(true);
    setLoadFailed(false);
    mcpService
      .getClient()
      .then((client) => {
        if (active) setClientId(client.clientId);
      })
      .catch(() => {
        if (active) setLoadFailed(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [open, loadAttempt]);

  const handleCopy = async (value: string) => {
    try {
      await navigator.clipboard.writeText(value);
      setNotice({ severity: 'success', message: t('mcp.copied') });
    } catch {
      setNotice({ severity: 'error', message: t('mcp.copyFailed') });
    }
  };

  const handleConfirm = async () => {
    const action = confirmAction;
    if (!action) return;

    setBusy(true);
    try {
      if (action === 'regenerate') {
        const client = await mcpService.regenerateClient();
        setClientId(client.clientId);
        setNotice({ severity: 'success', message: t('mcp.regenerated') });
      } else {
        await mcpService.revokeConnections();
        setNotice({ severity: 'success', message: t('mcp.revoked') });
      }
    } catch {
      setNotice({
        severity: 'error',
        message: t(action === 'regenerate' ? 'mcp.regenerateFailed' : 'mcp.revokeFailed'),
      });
    } finally {
      setBusy(false);
      setConfirmAction(null);
    }
  };

  const confirmTexts =
    confirmAction === 'regenerate'
      ? { title: t('mcp.regenerateTitle'), warning: t('mcp.regenerateWarning'), submit: t('mcp.regenerate') }
      : { title: t('mcp.revokeTitle'), warning: t('mcp.revokeWarning'), submit: t('mcp.revoke') };

  return (
    <>
      <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth aria-labelledby="claude-connection-title">
        <DialogTitle id="claude-connection-title">{t('mcp.title')}</DialogTitle>
        <DialogContent>
          <Typography variant="body2" gutterBottom>
            {t('mcp.description')}
          </Typography>
          <Typography variant="body2" color="text.secondary" gutterBottom>
            {t('mcp.privacyNote')}
          </Typography>

          {notice && (
            <Alert severity={notice.severity} sx={{ my: 1 }} onClose={() => setNotice(null)}>
              {notice.message}
            </Alert>
          )}

          {loading && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, my: 2 }} role="status">
              <CircularProgress size={20} />
              <Typography variant="body2">{t('mcp.loading')}</Typography>
            </Box>
          )}

          {loadFailed && (
            <Alert
              severity="error"
              sx={{ my: 1 }}
              action={
                <Button color="inherit" size="small" onClick={() => setLoadAttempt((attempt) => attempt + 1)}>
                  {t('mcp.retry')}
                </Button>
              }
            >
              {t('mcp.loadFailed')}
            </Alert>
          )}

          {clientId && (
            <CopyableField
              label={t('mcp.clientIdLabel')}
              value={clientId}
              copyLabel={t('mcp.copyClientId')}
              onCopy={handleCopy}
            />
          )}
          {connectorUrl && (
            <CopyableField
              label={t('mcp.connectorUrlLabel')}
              value={connectorUrl}
              copyLabel={t('mcp.copyConnectorUrl')}
              onCopy={handleCopy}
            />
          )}

          <Typography variant="subtitle2" sx={{ mt: 2 }} gutterBottom>
            {t('mcp.howToTitle')}
          </Typography>
          <Typography variant="body2">{t('mcp.howTo')}</Typography>
        </DialogContent>
        <DialogActions sx={{ p: 2, flexWrap: 'wrap', gap: 1 }}>
          <Button variant="outlined" disabled={!clientId || busy} onClick={() => setConfirmAction('regenerate')}>
            {t('mcp.regenerate')}
          </Button>
          <Button
            variant="outlined"
            color="error"
            disabled={!clientId || busy}
            onClick={() => setConfirmAction('revoke')}
          >
            {t('mcp.revokeAll')}
          </Button>
          <Box sx={{ flexGrow: 1 }} />
          <Button onClick={onClose}>{t('mcp.close')}</Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={confirmAction !== null}
        onClose={() => !busy && setConfirmAction(null)}
        aria-labelledby="claude-connection-confirm-title"
      >
        <DialogTitle id="claude-connection-confirm-title">{confirmTexts.title}</DialogTitle>
        <DialogContent>
          <DialogContentText>{confirmTexts.warning}</DialogContentText>
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button onClick={() => setConfirmAction(null)} disabled={busy}>
            {t('common.cancel')}
          </Button>
          <Button variant="contained" color="error" onClick={handleConfirm} disabled={busy}>
            {confirmTexts.submit}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

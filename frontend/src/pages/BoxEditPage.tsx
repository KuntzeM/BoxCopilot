import React, { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Box, Button, ButtonBase, Paper, Typography, Alert, CircularProgress, Stack, Snackbar, Collapse, TextField, IconButton, Dialog, DialogTitle, DialogContent, DialogActions } from '@mui/material';
import { LocalShipping, ArrowBack, Save, ExpandMore, ExpandLess, Print, ContentCopy, Autorenew, Edit } from '@mui/icons-material';
import * as boxService from '../services/boxService';
import * as itemService from '../services/itemService';
import { Box as BoxType, Item, UpdateItemPayload } from '../types/models';
import BoxForm, { BoxFormData } from '../components/BoxForm';
import EnhancedItemsTable from '../components/EnhancedItemsTable';
import { ItemForm } from '../components/ItemForm';
import { TILE, FONT_MONO } from '../context/ThemeContext';
import { useTranslation } from '../hooks/useTranslation';
import { extractApiError } from '../services/apiError';
import { generateLabelPdf } from '../utils/labelPdf';
import { LabelGenerationProgress } from '../components/LabelGenerationProgress';

const BoxEditPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { t } = useTranslation();

  const [box, setBox] = useState<BoxType | null>(null);
  const [items, setItems] = useState<Item[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [snackbar, setSnackbar] = useState<{ message: string; severity: 'success' | 'error' } | null>(null);
  const [expandBoxAttributes, setExpandBoxAttributes] = useState(false);
  const [rotateOpen, setRotateOpen] = useState(false);
  const [isRotating, setIsRotating] = useState(false);
  const [pdfProgress, setPdfProgress] = useState({ open: false, current: 0, total: 0, currentBoxNumber: '' });

  const boxId = id ? parseInt(id) : null;

  const loadBoxAndItems = useCallback(async (silent = false) => {
    if (!boxId) return;

    if (!silent) setIsLoading(true);
    try {
      // Fetch all boxes and find the one with matching ID
      const allBoxes = await boxService.fetchBoxes({ includeItems: false });
      const boxData = allBoxes.find((b) => b.id === boxId);

      if (!boxData) {
        setError(t('errors.boxNotFound'));
        setIsLoading(false);
        return;
      }

      setBox(boxData);

      const boxItems = await itemService.fetchItemsByBoxUuid(boxData.uuid);
      setItems(boxItems);
      setError(null);
    } catch (err) {
      setError(extractApiError(err, t('errors.itemsLoadFailed')));
      console.error(err);
    } finally {
      setIsLoading(false);
    }
  }, [boxId, t]);

  useEffect(() => {
    if (!boxId) {
      setError(t('errors.invalidBoxId'));
      setIsLoading(false);
      return;
    }

    loadBoxAndItems();
  }, [boxId, loadBoxAndItems]);

  const [formData, setFormData] = useState<BoxFormData>({
    currentRoom: '',
    targetRoom: '',
    description: '',
    isFragile: false,
    noStack: false,
    isMovedToTarget: false,
    labelPrinted: false,
  });

  useEffect(() => {
    if (box) {
      setFormData({
        currentRoom: box.currentRoom || '',
        targetRoom: box.targetRoom || '',
        description: box.description || '',
        isFragile: box.isFragile || false,
        noStack: box.noStack || false,
        isMovedToTarget: box.isMovedToTarget || false,
        labelPrinted: box.labelPrinted || false,
      });
    }
  }, [box]);

  const handleSave = async () => {
    if (!boxId || !box) return;

    setIsSaving(true);
    try {
      await boxService.updateBox(boxId, {
        currentRoom: formData.currentRoom,
        targetRoom: formData.targetRoom,
        description: formData.description,
        isFragile: formData.isFragile,
        noStack: formData.noStack,
        isMovedToTarget: formData.isMovedToTarget,
        labelPrinted: formData.labelPrinted,
      });

      setSnackbar({ message: t('success.boxSaved'), severity: 'success' });
      setTimeout(() => {
        navigate('/app/boxes');
      }, 1500);
    } catch (err) {
      setSnackbar({ message: extractApiError(err, t('errors.boxSaveFailed')), severity: 'error' });
      console.error(err);
    } finally {
      setIsSaving(false);
    }
  };

  const handleToggleMoved = async () => {
    if (!boxId || !box) return;
    const next = !formData.isMovedToTarget;
    setIsSaving(true);
    try {
      await boxService.updateBox(boxId, { isMovedToTarget: next });
      setFormData((prev) => ({ ...prev, isMovedToTarget: next }));
      setSnackbar({ message: t('success.boxSaved'), severity: 'success' });
    } catch (err) {
      setSnackbar({ message: extractApiError(err, t('errors.boxSaveFailed')), severity: 'error' });
    } finally {
      setIsSaving(false);
    }
  };

  const handleAddItem = async () => {
    // Just reload the box and items - ItemForm now handles creation
    await loadBoxAndItems(true);
  };

  const handleUpdateItem = async (itemId: number, data: UpdateItemPayload) => {
    try {
      const updatedItem = await itemService.updateItem(itemId, data);
      setItems(items.map((item) => (item.id === itemId ? updatedItem : item)));
      setSnackbar({ message: t('success.itemUpdated'), severity: 'success' });
    } catch (err) {
      setSnackbar({ message: extractApiError(err, t('errors.itemUpdateFailed')), severity: 'error' });
      console.error(err);
    }
  };

  const handleDeleteItem = async (itemId: number) => {
    try {
      await itemService.deleteItem(itemId);
      setItems(items.filter((item) => item.id !== itemId));
      setSnackbar({ message: t('success.itemDeleted'), severity: 'success' });
    } catch (err) {
      setSnackbar({ message: extractApiError(err, t('errors.itemDeleteFailed')), severity: 'error' });
      console.error(err);
    }
  };

  const handleMoveItems = async (itemIds: number[], targetBoxId: number) => {
    try {
      await itemService.moveItems(itemIds, targetBoxId);
      // Reload items after moving
      await loadBoxAndItems(true);
    } catch (err) {
      console.error(err);
      throw err;
    }
  };

  const handleImageUpdated = async () => {
    // Reload items to get updated image URLs
    await loadBoxAndItems(true);
  };

  const handleError = (message: string) => {
    setSnackbar({ message, severity: 'error' });
  };

  const handleSuccess = (message: string) => {
    setSnackbar({ message, severity: 'success' });
  };

  const publicUrl = box ? `${window.location.origin}/public/${box.uuid}` : '';

  const handleCopyLink = async () => {
    try {
      await navigator.clipboard.writeText(publicUrl);
      setSnackbar({ message: t('success.linkCopied'), severity: 'success' });
    } catch {
      setSnackbar({ message: t('errors.copyFailed'), severity: 'error' });
    }
  };

  const handleRotateShare = async () => {
    if (!boxId || isRotating) return;
    setIsRotating(true);
    try {
      const updated = await boxService.rotateShare(boxId);
      // The new uuid drives the public link and every label printed from now on.
      setBox((prev) => (prev ? { ...prev, ...updated } : updated));
      setRotateOpen(false);
      setSnackbar({ message: t('boxes.rotateShareSuccess'), severity: 'success' });
    } catch (err) {
      setRotateOpen(false);
      setSnackbar({ message: extractApiError(err, t('boxes.rotateShareFailed')), severity: 'error' });
    } finally {
      setIsRotating(false);
    }
  };

  const handlePrintLabel = async () => {
    if (!box || pdfProgress.open) return;
    try {
      const blob = await generateLabelPdf([{ ...box, publicUrl }], t, (current, total, boxNumber) =>
        setPdfProgress({ open: true, current, total, currentBoxNumber: boxNumber }),
      );
      setPdfProgress({ open: false, current: 0, total: 0, currentBoxNumber: '' });
      const blobUrl = URL.createObjectURL(blob);
      const win = window.open(blobUrl, '_blank');
      if (!win) {
        URL.revokeObjectURL(blobUrl);
        throw new Error('popup blocked');
      }
      setTimeout(() => URL.revokeObjectURL(blobUrl), 300000);
    } catch (err) {
      console.error('[PDF] Label generation failed:', err);
      setPdfProgress({ open: false, current: 0, total: 0, currentBoxNumber: '' });
      setSnackbar({ message: t('boxes.pdfGenerationFailed'), severity: 'error' });
    }
  };

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '400px' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error || !box) {
    return (
      <Box>
        <Button
          startIcon={<ArrowBack />}
          onClick={() => navigate('/app/boxes')}
          sx={{ mb: 2 }}
        >
          {t('boxes.back')}
        </Button>
        <Alert
          severity="error"
          action={
            <Button color="inherit" size="small" onClick={() => loadBoxAndItems()}>
              {t('common.retry')}
            </Button>
          }
        >
          {error || t('errors.boxLoadFailed')}
        </Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ pb: { xs: 12, md: 10 }, minWidth: 0 }}>
      <Stack direction="row" sx={{ mb: 3, justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
        <Button startIcon={<ArrowBack />} onClick={() => navigate('/app/boxes')}>
          {t('boxes.back')}
        </Button>
        <Button variant="outlined" startIcon={<Print />} onClick={handlePrintLabel} disabled={pdfProgress.open}>
          {t('boxes.printLabel')}
        </Button>
      </Stack>

      <Paper variant="outlined" sx={{ p: 0, mb: 3, overflow: 'hidden' }}>
        <Stack direction="row" spacing={2} sx={{ bgcolor: TILE.bg, color: TILE.ink, p: 2, alignItems: 'flex-end', justifyContent: 'space-between' }}>
          <Box sx={{ minWidth: 0 }}>
            <Typography sx={{ fontWeight: 800 }}>{formData.currentRoom || '-'} → {formData.targetRoom || '-'}</Typography>
            {formData.isFragile && <Typography sx={{ fontFamily: FONT_MONO, fontWeight: 800, fontSize: 12 }}>{t('boxes.fragile').toUpperCase()}</Typography>}
          </Box>
          <Typography aria-hidden sx={{ fontFamily: FONT_MONO, fontWeight: 800, fontSize: 'clamp(56px, 20vw, 96px)', lineHeight: 0.85, letterSpacing: '-0.08em' }}>
            {box.boxNumber}
          </Typography>
        </Stack>
        <Button
          fullWidth
          size="large"
          variant={formData.isMovedToTarget ? 'outlined' : 'contained'}
          startIcon={<LocalShipping />}
          onClick={handleToggleMoved}
          disabled={isSaving}
          sx={{ borderRadius: 0, minHeight: 56 }}
        >
          {formData.isMovedToTarget ? t('boxes.unmarkMoved') : t('boxes.markMoved')}
        </Button>
      </Paper>

      <Paper sx={{ p: 3, mb: 3 }}>
        <ButtonBase
          onClick={() => setExpandBoxAttributes(!expandBoxAttributes)}
          aria-expanded={expandBoxAttributes}
          sx={{ display: 'flex', alignItems: 'center', gap: 1, width: '100%', justifyContent: 'flex-start', textAlign: 'left', borderRadius: 1, mb: expandBoxAttributes ? 2 : 0 }}
        >
          {expandBoxAttributes ? <ExpandLess /> : <ExpandMore />}
          <Typography variant="h5" component="span" sx={{ flex: 1 }}>
            {t('boxes.editBox', { number: box.boxNumber })}
          </Typography>
          {!expandBoxAttributes && (
            <Typography component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, color: 'primary.main', fontWeight: 800 }}>
              <Edit fontSize="small" aria-hidden />
              {t('boxes.edit')}
            </Typography>
          )}
        </ButtonBase>

        <Collapse in={expandBoxAttributes} timeout="auto" unmountOnExit>
          {box && (
            <>
              <BoxForm data={formData} onChange={setFormData} />

              <Button
                variant="contained"
                startIcon={<Save />}
                onClick={handleSave}
                disabled={isSaving}
                fullWidth
                sx={{ mt: 3 }}
              >
                {isSaving ? t('common.saving') : t('boxes.saveAndReturn')}
              </Button>
            </>
          )}
        </Collapse>
      </Paper>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" sx={{ mb: 2 }}>
          {t('boxes.shareTitle')}
        </Typography>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' } }}>
          <TextField
            value={publicUrl}
            size="small"
            fullWidth
            slotProps={{
              input: {
                readOnly: true,
                sx: { '& input': { textOverflow: 'ellipsis' } },
                endAdornment: (
                  <IconButton onClick={handleCopyLink} aria-label={t('boxes.copyPublicLink')} edge="end">
                    <ContentCopy fontSize="small" />
                  </IconButton>
                ),
              },
              htmlInput: { 'aria-label': t('boxes.publicLink') },
            }}
          />
          <Button variant="outlined" startIcon={<Autorenew />} onClick={() => setRotateOpen(true)} sx={{ flexShrink: 0 }}>
            {t('boxes.rotateShare')}
          </Button>
        </Stack>
      </Paper>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
          <Typography variant="h6">
            {t('boxes.items')}
          </Typography>
        </Box>

        {/* ItemForm always rendered to maintain stable boxId */}
        {box && (
          <ItemForm
            onAddItem={handleAddItem}
            boxId={box.id}
            isLoading={isSaving || isLoading}
            onSuccess={handleSuccess}
            onError={handleError}
          />
        )}

        {items.length === 0 ? (
          <Alert severity="info">{t('items.noItemsInBox')}</Alert>
        ) : (
          <EnhancedItemsTable
            items={items}
            onUpdateItem={handleUpdateItem}
            onDeleteItem={handleDeleteItem}
            onMoveItems={handleMoveItems}
            onImageUpdated={handleImageUpdated}
            onError={handleError}
            onSuccess={handleSuccess}
          />
        )}
      </Paper>

      <Dialog open={rotateOpen} onClose={() => !isRotating && setRotateOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>{t('boxes.rotateShareTitle')}</DialogTitle>
        <DialogContent>
          <Alert severity="warning">{t('boxes.rotateShareWarning')}</Alert>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRotateOpen(false)} disabled={isRotating}>
            {t('common.cancel')}
          </Button>
          <Button variant="contained" onClick={handleRotateShare} disabled={isRotating}>
            {t('boxes.rotateShareConfirm')}
          </Button>
        </DialogActions>
      </Dialog>

      <LabelGenerationProgress {...pdfProgress} />

      <Snackbar
        open={snackbar !== null}
        autoHideDuration={4000}
        onClose={() => setSnackbar(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        sx={{ bottom: { xs: 'calc(16px + env(safe-area-inset-bottom))', md: 24 } }}
      >
        <Alert severity={snackbar?.severity} onClose={() => setSnackbar(null)}>
          {snackbar?.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default BoxEditPage;

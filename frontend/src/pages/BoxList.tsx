import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  Collapse,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  GlobalStyles,
  IconButton,
  Paper,
  Snackbar,
  Stack,
  TextField,
  Typography,
  useMediaQuery,
  useTheme,
  Avatar,
} from '@mui/material';
import {
  Add,
  ExpandMore,
  ExpandLess,
  ContentCopy,
  Delete,
  Edit,
  Link as LinkIcon,
  Print,
  Search,
  OpenInNew,
  BrokenImage,
  DoNotDisturb,
  Close,
} from '@mui/icons-material';
import { QRCodeCanvas } from 'qrcode.react';
import { Box as BoxModel, Item, CreateBoxPayload } from '../types/models';
import { fetchBoxes, createBox, deleteBox } from '../services/boxService';
import { searchItems } from '../services/itemService';
import { truncateToFirstLine } from '../utils/textUtils';
import { useTranslation } from '../hooks/useTranslation';

type SnackbarState = { open: boolean; message: string; severity: 'success' | 'error' | 'info' };

interface BoxHandlingBadgesProps {
  box: BoxModel;
}

const BoxHandlingBadges: React.FC<BoxHandlingBadgesProps> = ({ box }) => {
  const { t } = useTranslation();
  
  if (!box.isFragile && !box.noStack) {
    return null;
  }

  return (
    <Box sx={{ display: 'flex', gap: 1, mt: 1, flexWrap: 'wrap' }}>
      {box.isFragile && (
        <Chip
          icon={<BrokenImage />}
          label={t('boxes.fragile')}
          color="warning"
          size="small"
        />
      )}
      {box.noStack && (
        <Chip
          icon={<DoNotDisturb />}
          label={t('boxes.noStack')}
          color="error"
          size="small"
        />
      )}
    </Box>
  );
};

export default function BoxList() {
  const navigate = useNavigate();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const { t } = useTranslation();

  const [allBoxes, setAllBoxes] = useState<BoxModel[]>([]);
  const [filteredBoxes, setFilteredBoxes] = useState<BoxModel[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [itemQuery, setItemQuery] = useState('');
  const [roomQuery, setRoomQuery] = useState('');
  const [filterFragile, setFilterFragile] = useState(false);
  const [filterNoStack, setFilterNoStack] = useState(false);
  const [showAdvancedFilters, setShowAdvancedFilters] = useState(false);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [expandedBoxes, setExpandedBoxes] = useState<Set<number>>(new Set());
  const [snackbar, setSnackbar] = useState<SnackbarState>({ open: false, message: '', severity: 'success' });

  const [openBoxDialog, setOpenBoxDialog] = useState(false);
  const [boxFormData, setBoxFormData] = useState<CreateBoxPayload>({ 
    currentRoom: '', 
    targetRoom: '', 
    description: '',
    isFragile: false,
    noStack: false
  });

  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null);

  const [printOpen, setPrintOpen] = useState(false);

  const [fullImageUrl, setFullImageUrl] = useState<string | null>(null);

  const selectedBoxes = useMemo(
    () => filteredBoxes.filter((b) => selectedIds.includes(b.id)),
    [filteredBoxes, selectedIds]
  );

  useEffect(() => {
    loadData();
  }, []);

  // Automatische Filterung mit Debounce für Textfelder
  useEffect(() => {
    // Nur filtern, wenn allBoxes bereits geladen wurde
    if (allBoxes.length === 0 && !isLoading) {
      return;
    }

    const timeoutId = setTimeout(() => {
      handleSearch();
    }, 400); // 400ms Debounce

    return () => clearTimeout(timeoutId);
  }, [itemQuery, roomQuery, filterFragile, filterNoStack, allBoxes]);

  const loadData = async () => {
    setIsLoading(true);
    try {
      const boxes = await fetchBoxes();

      const withUrls = boxes.map((b) => ({
        ...b,
        publicUrl: `${window.location.origin}/public/${b.uuid}`,
      })) as BoxModel[];

      setAllBoxes(withUrls);
      setFilteredBoxes(withUrls);
      setSelectedIds([]);
    } catch (error) {
      setSnackbar({ open: true, message: t('errors.boxesFetchFailed'), severity: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  const handleResetFilter = () => {
    setItemQuery('');
    setRoomQuery('');
    setFilterFragile(false);
    setFilterNoStack(false);
    setFilteredBoxes(allBoxes);
  };

  const handleSearch = async () => {
    try {
      let next = [...allBoxes];
      const roomTerm = roomQuery.trim().toLowerCase();
      const itemTerm = itemQuery.trim();

      if (roomTerm) {
        next = next.filter((b) => (b.currentRoom || '').toLowerCase().includes(roomTerm));
      }

      if (itemTerm) {
        const items = await searchItems(itemTerm);
        const ids = new Set(items.map((i) => i.boxId));
        next = next.filter((b) => ids.has(b.id));
      }

      if (filterFragile) {
        next = next.filter((b) => b.isFragile === true);
      }

      if (filterNoStack) {
        next = next.filter((b) => b.noStack === true);
      }

      setFilteredBoxes(next);
      if (next.length === 0) {
        setSnackbar({ open: true, message: t('errors.noSearchResults'), severity: 'info' });
      }
    } catch (error) {
      setSnackbar({ open: true, message: t('errors.searchFailed'), severity: 'error' });
    }
  };

  const toggleSelect = (id: number) => {
    setSelectedIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
  };

  const toggleExpandBox = (id: number) => {
    setExpandedBoxes((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const handleCopyLink = async (url?: string) => {
    if (!url) {
      setSnackbar({ open: true, message: t('errors.noPublicLink'), severity: 'error' });
      return;
    }
    try {
      await navigator.clipboard.writeText(url);
      setSnackbar({ open: true, message: t('success.linkCopied'), severity: 'success' });
    } catch (error) {
      setSnackbar({ open: true, message: t('errors.copyFailed'), severity: 'error' });
    }
  };

  const handleOpenPublicLink = (url?: string) => {
    if (!url) {
      setSnackbar({ open: true, message: t('errors.noPublicLink'), severity: 'error' });
      return;
    }
    window.open(url, '_blank');
  };

  // Box Dialogs
  const handleOpenBoxDialog = () => {
    setBoxFormData({ currentRoom: '', targetRoom: '', description: '', isFragile: false, noStack: false });
    setOpenBoxDialog(true);
  };

  const handleSubmitBox = async () => {
    try {
      const newBox = await createBox(boxFormData as CreateBoxPayload);
      setSnackbar({ open: true, message: t('success.boxCreated'), severity: 'success' });
      setOpenBoxDialog(false);
      setBoxFormData({ currentRoom: '', targetRoom: '', description: '', isFragile: false, noStack: false });
      navigate(`/app/boxes/${newBox.id}/edit`);
    } catch (error) {
      setSnackbar({ open: true, message: t('errors.boxCreateFailed'), severity: 'error' });
    }
  };

  const handleDeleteBox = async () => {
    if (deleteConfirmId === null) return;
    try {
      await deleteBox(deleteConfirmId);
      setSnackbar({ open: true, message: t('success.boxDeleted'), severity: 'success' });
      setDeleteConfirmId(null);
      await loadData();
    } catch (error) {
      setSnackbar({ open: true, message: t('errors.boxDeleteFailed'), severity: 'error' });
    }
  };

  return (
    <Box sx={{ width: '100%', pb: 4 }}>
      <GlobalStyles
        styles={{
          '@page': { size: 'A4 portrait', margin: '10mm' },
          '@media print': {
            'body *': { visibility: 'hidden' },
            '.print-area, .print-area *': { visibility: 'visible' },
            '.print-area': { position: 'absolute', left: 0, top: 0, width: '100%' },
            '.print-label': {
              border: 'none',
              borderRadius: '0',
              padding: '8px 12px',
              height: '9.2cm',
              boxSizing: 'border-box',
              display: 'flex',
              flexDirection: 'row',
              justifyContent: 'flex-start',
              alignItems: 'center',
              pageBreakInside: 'avoid',
              borderBottom: '2px dashed #000',
            },
            '.print-label:last-child': {
              borderBottom: 'none',
            },
          },
        }}
      />

      <Stack spacing={2} sx={{ mb: 2 }}>
        <Paper sx={{ p: 2, borderRadius: 2, boxShadow: 2 }}>
          <Stack spacing={2}>
            <Stack direction={isMobile ? 'column' : 'row'} spacing={2} alignItems={isMobile ? 'stretch' : 'center'}>
              <TextField
                fullWidth
                label={t('boxes.itemName')}
                placeholder={t('boxes.itemNamePlaceholder')}
                value={itemQuery}
                onChange={(e) => setItemQuery(e.target.value)}
              />
              <TextField
                fullWidth
                label={t('boxes.targetRoomFilter')}
                placeholder={t('boxes.targetRoomFilterPlaceholder')}
                value={roomQuery}
                onChange={(e) => setRoomQuery(e.target.value)}
              />
              {!isMobile && (
                <Button 
                  variant="outlined" 
                  onClick={handleResetFilter}
                  size="small"
                  sx={{ minWidth: '120px' }}
                >
                  {t('boxes.reset')}
                </Button>
              )}
            </Stack>
            
            {/* Mobile Reset Button */}
            {isMobile && (
              <Button 
                fullWidth 
                variant="outlined" 
                onClick={handleResetFilter}
                size="small"
              >
                {t('boxes.resetFilters')}
              </Button>
            )}

            {/* Advanced Filters Toggle */}
            <Button
              size="small"
              onClick={() => setShowAdvancedFilters(!showAdvancedFilters)}
              sx={{ alignSelf: 'flex-start' }}
            >
              {showAdvancedFilters ? '▼' : '▶'} {t('boxes.advancedFilters')}
              {(filterFragile || filterNoStack) && (
                <Chip 
                  label={[filterFragile && '🔔', filterNoStack && '⛔'].filter(Boolean).join(' ')} 
                  size="small" 
                  sx={{ ml: 1 }} 
                />
              )}
            </Button>

            {/* Advanced Filters */}
            <Collapse in={showAdvancedFilters}>
              <Stack 
                direction={isMobile ? 'column' : 'row'} 
                spacing={isMobile ? 1 : 2} 
                sx={{ pl: isMobile ? 0 : 1, pt: 1 }}
              >
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={filterFragile}
                      onChange={(e) => setFilterFragile(e.target.checked)}
                      color="warning"
                      size={isMobile ? 'small' : 'medium'}
                    />
                  }
                  label={isMobile ? `🔔 ${t('boxes.fragile')}` : t('boxes.onlyFragileBoxes')}
                  sx={{ m: 0 }}
                />
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={filterNoStack}
                      onChange={(e) => setFilterNoStack(e.target.checked)}
                      color="error"
                      size={isMobile ? 'small' : 'medium'}
                    />
                  }
                  label={isMobile ? `⛔ ${t('boxes.noStack')}` : t('boxes.onlyNoStackBoxes')}
                  sx={{ m: 0 }}
                />
              </Stack>
            </Collapse>
          </Stack>
        </Paper>

        <Stack direction={isMobile ? 'column' : 'row'} spacing={1.5} justifyContent="space-between" alignItems={isMobile ? 'stretch' : 'center'}>
          <Typography variant="h5" fontWeight={700}>{t('boxes.boxes')}</Typography>
          <Stack direction="row" spacing={1} justifyContent={isMobile ? 'flex-start' : 'flex-end'}>
            <Button
              variant="outlined"
              startIcon={<Print />}
              onClick={() => setPrintOpen(true)}
              disabled={selectedIds.length === 0}
            >
              {t('boxes.printLabels')}
            </Button>
            <Button variant="contained" startIcon={<Add />} onClick={handleOpenBoxDialog}>
              {t('boxes.createNew')}
            </Button>
          </Stack>
        </Stack>
      </Stack>

      {isLoading ? (
        <Paper sx={{ p: 3, textAlign: 'center' }}>{t('boxes.loadingBoxes')}</Paper>
      ) : filteredBoxes.length === 0 ? (
        <Alert severity="info">{t('boxes.noBoxesFound')}</Alert>
      ) : (
        <Stack spacing={2}>
          {filteredBoxes.map((box) => (
            <Paper key={box.id} sx={{ borderRadius: 2, boxShadow: 2 }}>
              <Box sx={{ p: 2 }}>
                <Stack direction="row" spacing={2} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                  <Stack direction="row" spacing={2} alignItems="center" sx={{ flex: 1, minWidth: 200 }}>
                    <Checkbox
                      checked={selectedIds.includes(box.id)}
                      onChange={() => toggleSelect(box.id)}
                      inputProps={{ 'aria-label': t('boxes.selectBox', { number: box.id }) }}
                    />
                    <Box sx={{ flex: 1 }}>
                      <Typography variant="h6">{t('boxes.boxNumber', { number: box.id })}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {t('boxes.current')}: {box.currentRoom || '-'}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {t('boxes.target')}: {box.targetRoom || '-'}
                      </Typography>
                      {box.description && (
                        <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic', mt: 0.5 }}>
                          {truncateToFirstLine(box.description)}
                        </Typography>
                      )}
                      <BoxHandlingBadges box={box} />
                    </Box>
                  </Stack>

                  <Stack direction="row" spacing={0.5} flexWrap="wrap" justifyContent="flex-end">
                    <IconButton
                      size="small"
                      title={t('boxes.edit')}
                      onClick={() => navigate(`/app/boxes/${box.id}/edit`)}
                      color="primary"
                    >
                      <Edit fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      title={t('boxes.copyPublicLink')}
                      onClick={() => handleCopyLink(box.publicUrl)}
                    >
                      <ContentCopy fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      title={t('boxes.openPublicLink')}
                      onClick={() => handleOpenPublicLink(box.publicUrl)}
                      color="info"
                    >
                      <OpenInNew fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      title={t('boxes.delete')}
                      color="error"
                      onClick={() => setDeleteConfirmId(box.id)}
                    >
                      <Delete fontSize="small" />
                    </IconButton>
                  </Stack>
                </Stack>
              </Box>

              <Divider />

              <Box sx={{ p: 2 }}>
                <Stack
                  direction="row"
                  spacing={1}
                  alignItems="center"
                  onClick={() => toggleExpandBox(box.id)}
                  sx={{ cursor: 'pointer', userSelect: 'none' }}
                >
                  {expandedBoxes.has(box.id) ? <ExpandLess /> : <ExpandMore />}
                  <Typography variant="subtitle1" fontWeight={700}>
                    {t('items.itemsCount', { count: box.items?.length || 0 })}
                  </Typography>
                </Stack>

                <Collapse in={expandedBoxes.has(box.id)} timeout="auto" unmountOnExit>
                  <Stack spacing={1.5} sx={{ mt: 2 }}>
                    {box.items && box.items.length > 0 ? (
                      box.items.map((item) => (
                        <Paper key={item.id} variant="outlined" sx={{ p: 1.25, borderRadius: 1.5 }}>
                          <Stack direction="row" alignItems="center" spacing={1.5}>
                            {item.imageUrl ? (
                              <Avatar
                                src={item.imageUrl}
                                alt={item.name}
                                sx={{ width: 40, height: 40, cursor: 'pointer', flexShrink: 0 }}
                                onClick={() => setFullImageUrl(`/api/v1/items/${item.id}/image/large`)}
                              />
                            ) : (
                              <Avatar sx={{ width: 40, height: 40, bgcolor: 'grey.300', flexShrink: 0 }}>
                                📦
                              </Avatar>
                            )}
                            <Box sx={{ flex: 1, minWidth: 160 }}>
                              <Typography variant="body2" fontWeight={500}>
                                {item.name}
                              </Typography>
                            </Box>
                          </Stack>
                        </Paper>
                      ))
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        {t('items.noItemsInBox')}
                      </Typography>
                    )}
                  </Stack>
                </Collapse>
              </Box>
            </Paper>
          ))}
        </Stack>
      )}

      {/* Box Dialog - Create new box */}
      <Dialog open={openBoxDialog} onClose={() => setOpenBoxDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('boxes.createNewBox')}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label={t('boxes.currentRoom')}
              fullWidth
              value={boxFormData.currentRoom || ''}
              onChange={(e) => setBoxFormData({ ...boxFormData, currentRoom: e.target.value })}
              placeholder={t('boxes.placeholderCurrentRoom')}
            />
            <TextField
              label={t('boxes.targetRoom')}
              fullWidth
              value={boxFormData.targetRoom || ''}
              onChange={(e) => setBoxFormData({ ...boxFormData, targetRoom: e.target.value })}
              placeholder={t('boxes.placeholderTargetRoom')}
            />
            <TextField
              label={t('boxes.description')}
              fullWidth
              multiline
              rows={4}
              value={boxFormData.description || ''}
              onChange={(e) => setBoxFormData({ ...boxFormData, description: e.target.value })}
              placeholder={t('boxes.placeholderDescription')}
            />
            <Box sx={{ p: 2, bgcolor: 'action.hover', borderRadius: 1, border: 1, borderColor: 'divider' }}>
              <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1.5 }}>
                {t('boxes.transportHints')}
              </Typography>
              <FormControlLabel
                control={
                  <Checkbox
                    checked={boxFormData.isFragile || false}
                    onChange={(e) => setBoxFormData({ ...boxFormData, isFragile: e.target.checked })}
                    color="warning"
                  />
                }
                label={t('boxes.fragileEmoji')}
              />
              <FormControlLabel
                control={
                  <Checkbox
                    checked={boxFormData.noStack || false}
                    onChange={(e) => setBoxFormData({ ...boxFormData, noStack: e.target.checked })}
                    color="error"
                  />
                }
                label={t('boxes.noStackEmoji')}
              />
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpenBoxDialog(false)}>{t('boxes.cancel')}</Button>
          <Button 
            variant="contained" 
            onClick={handleSubmitBox}
          >
            {t('boxes.create')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog
        open={deleteConfirmId !== null}
        onClose={() => setDeleteConfirmId(null)}
      >
        <DialogTitle>{t('boxes.deleteBoxTitle')}</DialogTitle>
        <DialogContent>
          <Typography>{t('boxes.deleteBoxMessage')}</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteConfirmId(null)}>{t('boxes.cancel')}</Button>
          <Button color="error" variant="contained" onClick={handleDeleteBox}>
            {t('boxes.delete')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Label print dialog */}
      <Dialog open={printOpen} onClose={() => setPrintOpen(false)} maxWidth="lg" fullWidth>
        <DialogTitle>{t('boxes.printLabels')}</DialogTitle>
        <DialogContent>
          {selectedBoxes.length === 0 ? (
            <Alert severity="info">{t('boxes.noBoxSelected')}</Alert>
          ) : (
            <Box className="print-area" sx={{ display: 'grid', gap: 0 }}>
              {selectedBoxes.map((box) => (
                <Paper key={`print-${box.id}`} className="print-label" sx={{ borderColor: '#000', background: '#fff' }}>
                  <Box sx={{ display: 'grid', placeItems: 'center', flexShrink: 0, width: '9cm', height: '9cm' }}>
                    <QRCodeCanvas value={box.publicUrl || ''} size={240} includeMargin level="H" />
                  </Box>
                  <Stack direction="column" spacing={0.5} sx={{ flex: 1, justifyContent: 'flex-start', pl: 1.5 }}>
                    <Box sx={{ height: '5cm', display: 'flex', flexDirection: 'column', alignItems: 'flex-start', justifyContent: 'center' }}>
                      <Typography variant="h2" fontWeight={900} sx={{ lineHeight: 1, color: '#000', fontSize: '3rem' }}>
                        {t('boxes.boxNumber', { number: box.id })}
                      </Typography>
                      <Typography variant="h2" fontWeight={900} sx={{ lineHeight: 1, color: '#000', fontSize: '3rem' }}>
                        {box.currentRoom || t('boxes.targetRoom')}
                      </Typography>
                    </Box>
                    {box.description && (
                      <Typography variant="body2" sx={{ fontSize: '0.75rem', fontStyle: 'italic', color: '#000', overflow: 'hidden', textOverflow: 'ellipsis', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical' }}>
                        {truncateToFirstLine(box.description)}
                      </Typography>
                    )}
                    
                    {(box.isFragile || box.noStack) && (
                      <Box sx={{ borderTop: '2px dashed #000', mt: 1.5, pt: 1.5, display: 'flex', gap: 2, alignItems: 'center' }}>
                        {box.isFragile && (
                          <Box sx={{ textAlign: 'center' }}>
                            <Typography sx={{ fontSize: '48px', lineHeight: 1 }}>🔔</Typography>
                            <Typography sx={{ fontSize: '0.7rem', fontWeight: 700, color: '#000', mt: 0.5 }}>{t('boxes.fragilePrintLabel')}</Typography>
                          </Box>
                        )}
                        {box.noStack && (
                          <Box sx={{ textAlign: 'center' }}>
                            <Typography sx={{ fontSize: '48px', lineHeight: 1 }}>⛔</Typography>
                            <Typography sx={{ fontSize: '0.7rem', fontWeight: 700, color: '#000', mt: 0.5 }}>{t('boxes.noStackPrintLabel')}</Typography>
                          </Box>
                        )}
                      </Box>
                    )}
                  </Stack>
                </Paper>
              ))}
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPrintOpen(false)}>{t('boxes.close')}</Button>
          <Button variant="contained" startIcon={<Print />} onClick={() => window.print()} disabled={selectedBoxes.length === 0}>
            {t('boxes.print')}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={4000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
      >
        <Alert severity={snackbar.severity} onClose={() => setSnackbar({ ...snackbar, open: false })}>
          {snackbar.message}
        </Alert>
      </Snackbar>

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
                const match = fullImageUrl.match(/\/api\/v1\/items\/(\d+)\/image/);
                if (match) {
                  e.currentTarget.src = `/api/v1/items/${match[1]}/image`;
                }
              }}
            />
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setFullImageUrl(null)}>Schließen</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

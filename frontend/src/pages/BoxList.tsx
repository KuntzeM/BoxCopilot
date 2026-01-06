import React, { useEffect, useMemo, useState, useCallback } from 'react';
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
  Fab,
  Drawer,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemButton,
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
  MoreVert,
} from '@mui/icons-material';
import { QRCodeCanvas } from 'qrcode.react';
import { List as VirtualList } from 'react-window';
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
  const [matchedItemIds, setMatchedItemIds] = useState<Set<number>>(new Set());
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

  const [fullImageUrl, setFullImageUrl] = useState<string | null>(null);

  // State for box actions drawer
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedBoxForActions, setSelectedBoxForActions] = useState<BoxModel | null>(null);

  // Build absolute API URLs for images (works in prod without nginx proxy)
  const apiBase = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');
  const withApiBase = (path: string) =>
    apiBase ? `${apiBase}${path.startsWith('/') ? path : `/${path}`}` : path;
  const resolveImageUrl = (url?: string) => {
    if (!url) return undefined;
    if (url.startsWith('http://') || url.startsWith('https://')) return url;
    return withApiBase(url.startsWith('/') ? url : `/${url}`);
  };

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

      const withUrls = boxes
        .sort((a, b) => b.id - a.id) // Newest first
        .map((b) => ({
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
    setMatchedItemIds(new Set());
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
        setMatchedItemIds(new Set(items.map((i) => i.id)));
        next = next.filter((b) => ids.has(b.id));
      } else {
        setMatchedItemIds(new Set());
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
      setMatchedItemIds(new Set());
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

  // Direct print handler
  const handlePrintLabels = () => {
    if (selectedBoxes.length === 0) {
      setSnackbar({ open: true, message: t('boxes.noBoxSelected'), severity: 'info' });
      return;
    }
    // Print-area is always in DOM, CSS handles visibility based on @media print
    window.print();
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

  const handleOpenBoxActions = (box: BoxModel) => {
    setSelectedBoxForActions(box);
    setDrawerOpen(true);
  };

  const handleCloseDrawer = () => {
    setDrawerOpen(false);
    setSelectedBoxForActions(null);
  };

  const handleDrawerEdit = () => {
    if (selectedBoxForActions) {
      navigate(`/app/boxes/${selectedBoxForActions.id}/edit`);
    }
    handleCloseDrawer();
  };

  const handleDrawerCopyLink = async () => {
    if (selectedBoxForActions?.publicUrl) {
      await handleCopyLink(selectedBoxForActions.publicUrl);
    }
    handleCloseDrawer();
  };

  const handleDrawerOpenLink = () => {
    if (selectedBoxForActions?.publicUrl) {
      handleOpenPublicLink(selectedBoxForActions.publicUrl);
    }
    handleCloseDrawer();
  };

  const handleDrawerDelete = () => {
    if (selectedBoxForActions) {
      setDeleteConfirmId(selectedBoxForActions.id);
    }
    handleCloseDrawer();
  };

  // Render a single box card (for both regular and virtualized rendering)
  const renderBoxCard = useCallback((box: BoxModel) => (
    <Paper key={box.id} sx={{ borderRadius: 2, boxShadow: 2 }}>
      <Box sx={{ p: 2 }}>
        <Stack direction="row" spacing={2} alignItems="center" justifyContent="space-between" flexWrap="wrap">
          <Stack direction="row" spacing={2} alignItems="center" sx={{ flex: 1, minWidth: 200 }}>
            <Checkbox
              checked={selectedIds.includes(box.id)}
              onChange={() => toggleSelect(box.id)}
              inputProps={{ 'aria-label': t('boxes.selectBox', { number: box.id }) }}
            />
            <Box 
              sx={{ 
                flex: 1,
                cursor: 'pointer',
                '&:hover': {
                  opacity: 0.8
                }
              }}
              onClick={() => navigate(`/app/boxes/${box.id}/edit`)}
            >
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
              size="medium"
              title={t('boxes.actions')}
              onClick={() => handleOpenBoxActions(box)}
              color="primary"
              sx={{ minWidth: 48, minHeight: 48 }}
            >
              <MoreVert />
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
                <Paper
                  key={item.id}
                  variant="outlined"
                  sx={{
                    p: 1.25,
                    borderRadius: 1.5,
                    borderColor: matchedItemIds.has(item.id) ? theme.palette.primary.main : undefined,
                    backgroundColor: matchedItemIds.has(item.id)
                      ? theme.palette.primary.main + '22'
                      : undefined,
                  }}
                >
                  <Stack direction="row" alignItems="center" spacing={1.5}>
                    {item.imageUrl ? (
                      <Avatar
                        src={resolveImageUrl(item.imageUrl)}
                        alt={item.name}
                        loading="lazy"
                        sx={{ width: 40, height: 40, cursor: 'pointer', flexShrink: 0 }}
                        onClick={() => setFullImageUrl(withApiBase(item.imageUrl.replace('/image', '/image/large')))}
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
  ), [selectedIds, expandedBoxes, t, resolveImageUrl, withApiBase, matchedItemIds, theme]);

  const useVirtualization = filteredBoxes.length > 50;

  return (
    <Box sx={{ width: '100%', pb: 4 }}>
      <GlobalStyles
        styles={{
          '@page': { 
            size: 'A4 portrait', 
            margin: 0,
          },
          /* Normal view: print-area MUST be hidden */
          '.print-area': {
            display: 'none !important',
            position: 'absolute !important',
            visibility: 'hidden !important',
            width: '0 !important',
            height: '0 !important',
            overflow: 'hidden !important',
            margin: '0 !important',
            padding: '0 !important',
            border: 'none !important',
            pointerEvents: 'none !important',
          },
          '@media print': {
            'html, body': {
              margin: 0,
              padding: 0,
              background: '#fff',
              width: '100%',
              height: '100%',
            },
            /* Hide everything EXCEPT print-area by hiding body children */
            'body > :not(.print-area)': {
              display: 'none !important',
            },
            /* Make print-area visible and full width */
            '.print-area': {
              display: 'grid !important',
              gap: 0,
              width: '100% !important',
              margin: 0,
              padding: 0,
              background: '#fff !important',
              color: '#000 !important',
              position: 'relative !important',
              WebkitPrintColorAdjust: 'exact !important',
              printColorAdjust: 'exact !important',
              colorAdjust: 'exact !important',
            },
            /* Ensure all print-area children are visible */
            '.print-area, .print-area *': {
              visibility: 'visible !important',
              WebkitPrintColorAdjust: 'exact !important',
              printColorAdjust: 'exact !important',
              colorAdjust: 'exact !important',
            },
            /* Individual labels */
            '.print-label': {
              display: 'flex !important',
              border: 'none !important',
              borderRadius: '0 !important',
              padding: '10mm 12mm !important',
              height: '7cm !important',
              boxSizing: 'border-box !important',
              flexDirection: 'row !important',
              justifyContent: 'flex-start !important',
              alignItems: 'center !important',
              pageBreakInside: 'avoid !important',
              position: 'relative !important',
              backgroundColor: '#fff !important',
              color: '#000 !important',
              margin: '0 !important',
              visibility: 'visible !important',
              WebkitPrintColorAdjust: 'exact !important',
              printColorAdjust: 'exact !important',
            },
            /* QR Code Canvas */
            '.print-label canvas': {
              display: 'block !important',
              width: '7cm !important',
              height: '7cm !important',
              WebkitPrintColorAdjust: 'exact !important',
              printColorAdjust: 'exact !important',
              margin: '0 !important',
              padding: '0 !important',
            },
            /* Label separators */
            '.print-label:not(:last-child)::after': {
              content: '""',
              position: 'absolute !important',
              left: 0,
              right: 0,
              bottom: 0,
              borderTop: '1px dashed #000 !important',
            },
            /* Text styling for labels */
            '.print-label Typography': {
              color: '#000 !important',
            },
          },
        }}
      />

      <Stack spacing={2} sx={{ mb: 2 }}>
        <Paper sx={{ p: 2, borderRadius: 2, boxShadow: 2 }}>
          <Stack spacing={2}>
            <Stack direction="column" spacing={2}>
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
              <Button 
                fullWidth
                variant="outlined" 
                onClick={handleResetFilter}
                size="small"
              >
                {t('boxes.reset')}
              </Button>
            </Stack>

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
              onClick={handlePrintLabels}
              disabled={selectedIds.length === 0}
            >
              {t('boxes.printLabels')}
            </Button>
            {!isMobile && (
              <Button variant="contained" startIcon={<Add />} onClick={handleOpenBoxDialog}>
                {t('boxes.createNew')}
              </Button>
            )}
          </Stack>
        </Stack>
      </Stack>

      {isLoading ? (
        <Paper sx={{ p: 3, textAlign: 'center' }}>{t('boxes.loadingBoxes')}</Paper>
      ) : filteredBoxes.length === 0 ? (
        <Alert severity="info">{t('boxes.noBoxesFound')}</Alert>
      ) : useVirtualization ? (
        <Box sx={{ height: 600, width: '100%' }}>
          <VirtualList
            defaultHeight={600}
            rowCount={filteredBoxes.length}
            rowHeight={200}
          >
            {({ index, style }) => (
              <Box style={style} sx={{ px: 0, py: 1 }}>
                {renderBoxCard(filteredBoxes[index])}
              </Box>
            )}
          </VirtualList>
        </Box>
      ) : (
        <Stack spacing={2}>
          {filteredBoxes.map((box) => renderBoxCard(box))}
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

      {/* Direct print area (always in DOM, hidden by CSS except during print) */}
      {selectedBoxes.length > 0 && (
        <Box className="print-area" sx={{ display: 'grid', gap: 0 }}>
          {selectedBoxes.map((box) => (
            <Paper
              key={`print-${box.id}`}
              className="print-label"
              elevation={0}
              sx={{ border: 'none', boxShadow: 'none', background: '#fff', color: '#000' }}
            >
              {/* Left: QR Code - optimized for mobile print */}
              <Box 
                sx={{ 
                  display: 'grid', 
                  placeItems: 'center', 
                  flexShrink: 0, 
                  width: '7cm', 
                  height: '100%',
                  /* Ensure QR renders on Android - critical for print */
                  '@media print': {
                    WebkitPrintColorAdjust: 'exact !important',
                    printColorAdjust: 'exact !important',
                  }
                }}
              >
                <QRCodeCanvas 
                  value={box.publicUrl || ''} 
                  size={160} 
                  includeMargin 
                  level="H"
                  /* Canvas rendering optimization for mobile print */
                  style={{ display: 'block', maxWidth: '100%', height: 'auto' }}
                />
              </Box>

              {/* Right: Text content */}
              <Stack direction="column" spacing={1} sx={{ flex: 1, pl: 2, pr: 1 }}>
                {/* Prominent transport badges above ID */}
                {(box.isFragile || box.noStack) && (
                  <Stack direction="row" spacing={1} sx={{ mb: 1 }}>
                    {box.isFragile && (
                      <Typography
                        sx={{
                          fontSize: '1.1rem',
                          fontWeight: 900,
                          textTransform: 'uppercase',
                          backgroundColor: '#000',
                          color: '#fff',
                          px: 1.5,
                          py: 0.5,
                          lineHeight: 1,
                          letterSpacing: '0.04em',
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 0.5,
                        }}
                      >
                        <span style={{ fontSize: '1.2rem', lineHeight: 1 }}>🔔</span>
                        {t('boxes.fragilePrintLabel')}
                      </Typography>
                    )}
                    {box.noStack && (
                      <Typography
                        sx={{
                          fontSize: '1.1rem',
                          fontWeight: 900,
                          textTransform: 'uppercase',
                          backgroundColor: '#000',
                          color: '#fff',
                          px: 1.5,
                          py: 0.5,
                          lineHeight: 1,
                          letterSpacing: '0.04em',
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 0.5,
                        }}
                      >
                        <span style={{ fontSize: '1.2rem', lineHeight: 1 }}>⛔</span>
                        {t('boxes.noStackPrintLabel')}
                      </Typography>
                    )}
                  </Stack>
                )}
                {/* Box #ID */}
                <Typography sx={{ fontSize: '2rem', fontWeight: 800, lineHeight: 1 }}>
                  {t('boxes.boxNumber', { number: box.id })}
                </Typography>

                {/* ZielRaum (target room) */}
                <Typography sx={{ fontSize: '1.5rem', fontWeight: 700, lineHeight: 1.2, textTransform: 'uppercase' }}>
                   {t('boxes.targetRoom')}: {box.targetRoom || '-'}
                </Typography>

                {/* Beschreibung */}
                {box.description && (
                  <Typography
                    sx={{
                      fontSize: '0.9rem',
                      fontStyle: 'italic',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      display: '-webkit-box',
                      WebkitLineClamp: 3,
                      WebkitBoxOrient: 'vertical',
                    }}
                  >
                    {truncateToFirstLine(box.description)}
                  </Typography>
                )}
              </Stack>
            </Paper>
          ))}
        </Box>
      )}

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

      {/* Floating Action Button for Mobile */}
      {isMobile && (
        <Fab
          color="primary"
          aria-label={t('boxes.createNew')}
          onClick={handleOpenBoxDialog}
          sx={{
            position: 'fixed',
            bottom: 80,
            right: 16,
            zIndex: 1000,
          }}
        >
          <Add />
        </Fab>
      )}

      {/* Bottom Sheet for Box Actions */}
      <Drawer
        anchor="bottom"
        open={drawerOpen}
        onClose={handleCloseDrawer}
        PaperProps={{
          sx: {
            borderTopLeftRadius: 16,
            borderTopRightRadius: 16,
            maxHeight: '70vh',
          },
        }}
      >
        <Box sx={{ p: 2 }}>
          <Typography variant="h6" sx={{ mb: 2, textAlign: 'center' }}>
            {selectedBoxForActions ? t('boxes.boxNumber', { number: selectedBoxForActions.id }) : ''}
          </Typography>
          <List>
            <ListItemButton
              onClick={handleDrawerEdit}
              sx={{ minHeight: 56, borderRadius: 1, mb: 1 }}
            >
              <ListItemIcon>
                <Edit color="primary" />
              </ListItemIcon>
              <ListItemText primary={t('boxes.edit')} />
            </ListItemButton>
            <ListItemButton
              onClick={handleDrawerCopyLink}
              sx={{ minHeight: 56, borderRadius: 1, mb: 1 }}
            >
              <ListItemIcon>
                <ContentCopy />
              </ListItemIcon>
              <ListItemText primary={t('boxes.copyPublicLink')} />
            </ListItemButton>
            <ListItemButton
              onClick={handleDrawerOpenLink}
              sx={{ minHeight: 56, borderRadius: 1, mb: 1 }}
            >
              <ListItemIcon>
                <OpenInNew color="info" />
              </ListItemIcon>
              <ListItemText primary={t('boxes.openPublicLink')} />
            </ListItemButton>
            <ListItemButton
              onClick={handleDrawerDelete}
              sx={{ minHeight: 56, borderRadius: 1, mb: 1 }}
            >
              <ListItemIcon>
                <Delete color="error" />
              </ListItemIcon>
              <ListItemText primary={t('boxes.delete')} />
            </ListItemButton>
          </List>
        </Box>
      </Drawer>
    </Box>
  );
}

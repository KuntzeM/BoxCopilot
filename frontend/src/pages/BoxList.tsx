import React, { useMemo, useState, useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Alert,
  ButtonBase,
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
  IconButton,
  Paper,
  Snackbar,
  Stack,
  TextField,
  Typography,
  useMediaQuery,
  useTheme,
  Avatar,
  Badge,
  InputAdornment,
  Drawer,
  List,
  ListItemIcon,
  ListItemText,
  ListItemButton,
  CircularProgress,
  alpha,
} from '@mui/material';
import { FONT_MONO, SURFACE } from '../context/ThemeContext';
import {
  Add,
  Check,
  ExpandMore,
  ExpandLess,
  ContentCopy,
  Delete,
  Edit,
  Print,
  Search,
  OpenInNew,
  BrokenImage,
  DoNotDisturb,
  Close,
  ArrowForward,
  FilterList,
  MoreVert,
  LocalShipping,
  Label,
  Inventory2,
} from '@mui/icons-material';
import { Box as BoxModel } from '../types/models';
import { updateBox } from '../services/boxService';
import { truncateToFirstLine } from '../utils/textUtils';
import { useTranslation } from '../hooks/useTranslation';
import { LabelGenerationProgress } from '../components/LabelGenerationProgress';
import { useBoxListLogic } from '../hooks/useBoxListLogic';

const srOnly: React.CSSProperties = { position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)', whiteSpace: 'nowrap' };

type SnackbarState = { open: boolean; message: string; severity: 'success' | 'error' | 'info' };

interface BoxHandlingBadgesProps {
  box: BoxModel;
}

const BoxHandlingBadges: React.FC<BoxHandlingBadgesProps> = ({ box }) => {
  const { t } = useTranslation();

  // Quiet by default: only boxes that need care show flags. "Moved" is already carried by the green tag and check.
  if (!box.isFragile && !box.noStack && !box.labelPrinted) {
    return null;
  }

  // Every status is an icon + text label, never colour alone
  const flagSx = { fontWeight: 600, maxWidth: '100%' } as const;
  return (
    <Box sx={{ display: 'flex', gap: 0.75, mt: 0.75, flexWrap: 'wrap', alignItems: 'center' }}>
      {box.isFragile && (
        <Chip icon={<BrokenImage />} label={t('boxes.fragile')} size="small" variant="outlined" sx={{ ...flagSx, borderColor: 'warning.main', color: 'warning.main', '& .MuiChip-icon': { color: 'warning.main' } }} />
      )}
      {box.noStack && (
        <Chip icon={<DoNotDisturb />} label={t('boxes.noStack')} variant="outlined" size="small" sx={{ ...flagSx, borderColor: 'divider', color: 'text.secondary', '& .MuiChip-icon': { color: 'text.secondary' } }} />
      )}
      {box.labelPrinted && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, fontFamily: FONT_MONO }}>
          <Label sx={{ fontSize: 14 }} aria-hidden />
          {t('boxes.labelPrinted')}
        </Typography>
      )}
    </Box>
  );
};

export default function BoxList() {
  const navigate = useNavigate();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const { t } = useTranslation();

  // Use custom hook for all business logic
  const {
    filteredBoxes,
    matchedItemIds,
    isLoading,
    loadError,
    allBoxes,
    itemQuery,
    setItemQuery,
    roomQuery,
    setRoomQuery,
    filterFragile,
    setFilterFragile,
    filterNoStack,
    setFilterNoStack,
    statusFilter,
    setStatusFilter,
    filterLabelPrinted,
    setFilterLabelPrinted,
    showAdvancedFilters,
    setShowAdvancedFilters,
    selectedIds,
    expandedBoxes,
    snackbar,
    setSnackbar,
    pdfProgress,
    openBoxDialog,
    setOpenBoxDialog,
    boxFormData,
    setBoxFormData,
    deleteConfirmId,
    setDeleteConfirmId,
    isSubmittingBox,
    isDeletingBox,
    fullImageUrl,
    setFullImageUrl,
    drawerOpen,
    selectedBoxForActions,
    resolveImageUrl,
    getBoxItems,
    getBoxItemCount,
    isLoadingItemsForBox,
    loadData,
    handleResetFilter,
    toggleSelect,
    clearSelection,
    toggleExpandBox,
    handlePrintLabels,
    handleOpenBoxDialog,
    handleSubmitBox,
    handleDeleteBox,
    handleOpenBoxActions,
    handleCloseDrawer,
    handleDrawerEdit,
    handleDrawerCopyLink,
    handleDrawerOpenLink,
    handleDrawerToggleMoved,
    handleDrawerDelete,
  } = useBoxListLogic();

  // Master-detail keeps this list mounted while boxes are created/edited in the detail pane: refresh quietly on route change.
  const location = useLocation();
  const { pathname } = location;
  const firstRoute = useRef(true);
  useEffect(() => {
    if (firstRoute.current) {
      firstRoute.current = false;
      return;
    }
    loadData(true);
  }, [pathname]);

  const [showFilterPanel, setShowFilterPanel] = useState(false);
  const activeFilters = [filterFragile, filterNoStack, filterLabelPrinted, roomQuery.trim() !== ''].filter(Boolean).length;

  // Bulk update state
  const [showPrintDialog, setShowPrintDialog] = useState(false);
  const [setLabelPrintedAfterPrint, setSetLabelPrintedAfterPrint] = useState(true);

  // Bulk update handlers
  const handleBulkUpdateMoved = async () => {
    const selectedBoxes = filteredBoxes.filter((b) => selectedIds.includes(b.id));
    const allMoved = selectedBoxes.every((b) => b.isMovedToTarget);

    try {
      await Promise.all(
        selectedBoxes.map((box) => updateBox(box.id, { isMovedToTarget: !allMoved }))
      );
      await loadData();
    } catch (err) {
      setSnackbar({ open: true, message: t('errors.boxUpdateFailed'), severity: 'error' });
      console.error(err);
    }
  };

  const handleBulkUpdateLabelPrinted = async () => {
    const selectedBoxes = filteredBoxes.filter((b) => selectedIds.includes(b.id));
    const allPrinted = selectedBoxes.every((b) => b.labelPrinted);

    try {
      await Promise.all(
        selectedBoxes.map((box) => updateBox(box.id, { labelPrinted: !allPrinted }))
      );
      await loadData();
    } catch (err) {
      setSnackbar({ open: true, message: t('errors.boxUpdateFailed'), severity: 'error' });
      console.error(err);
    }
  };

  const selectedBoxes = useMemo(
    () => filteredBoxes.filter((b) => selectedIds.includes(b.id)),
    [selectedIds, filteredBoxes]
  );

  const deleteTarget = allBoxes.find((b) => b.id === deleteConfirmId);

  const allSelectedMoved = selectedBoxes.every((b) => b.isMovedToTarget);
  const allSelectedPrinted = selectedBoxes.every((b) => b.labelPrinted);
  const hasUnprintedLabels = selectedBoxes.some((b) => !b.labelPrinted);

  const handlePrintWithDialog = () => {
    if (hasUnprintedLabels) {
      setShowPrintDialog(true);
    } else {
      handlePrintLabels();
    }
  };

  const handleProceedWithPrint = async () => {
    setShowPrintDialog(false);

    // Only mark labels as printed once the PDF was actually generated
    const generated = await handlePrintLabels();
    if (generated && setLabelPrintedAfterPrint) {
      try {
        await Promise.all(
          selectedBoxes.map((box) => !box.labelPrinted ? updateBox(box.id, { labelPrinted: true }) : Promise.resolve())
        );
        await loadData();
      } catch (err) {
        console.error('Failed to update labelPrinted status', err);
        setSnackbar({ open: true, message: t('errors.boxUpdateFailed'), severity: 'error' });
      }
    }
  };


  // One-tap "moved" with undo instead of a confirm dialog
  const [undo, setUndo] = useState<{ open: boolean; id: number; number: number; wasMoved: boolean }>({ open: false, id: 0, number: 0, wasMoved: false });
  const searchRef = useRef<HTMLInputElement>(null);

  const handleQuickMoved = async (box: BoxModel) => {
    const wasMoved = !!box.isMovedToTarget;
    try {
      await updateBox(box.id, { isMovedToTarget: !wasMoved });
      await loadData(true);
      setUndo({ open: true, id: box.id, number: box.boxNumber ?? 0, wasMoved });
    } catch (err) {
      setSnackbar({ open: true, message: t('errors.boxUpdateFailed'), severity: 'error' });
      console.error(err);
    }
  };

  const handleUndo = async () => {
    setUndo((u) => ({ ...u, open: false }));
    try {
      await updateBox(undo.id, { isMovedToTarget: undo.wasMoved });
      await loadData(true);
    } catch (err) {
      setSnackbar({ open: true, message: t('errors.boxUpdateFailed'), severity: 'error' });
      console.error(err);
    }
  };

  // Thumb bar / rail actions arrive as router state (see AppShell)
  const intent = (location.state as { intent?: 'search' | 'new' | 'item'; at?: number } | null) ?? null;
  useEffect(() => {
    if (!intent?.at || (intent.intent !== 'new' && intent.intent !== 'search')) return;
    if (intent.intent === 'new') {
      handleOpenBoxDialog();
    } else if (intent.intent === 'search') {
      searchRef.current?.scrollIntoView({ block: 'center', behavior: 'smooth' });
      searchRef.current?.focus();
    }
    // Consume the intent so a reload or back navigation does not repeat it
    navigate(pathname, { replace: true, state: null });
  }, [intent?.at]);

  // Progress ledger: one tick per box, grouped per target room (a room chip doubles as the room filter)
  const ledger = useMemo(() => {
    const total = allBoxes.length;
    const moved = allBoxes.filter((b) => b.isMovedToTarget).length;
    const rooms = new Map<string, { moved: number; total: number }>();
    allBoxes.forEach((b) => {
      const room = (b.targetRoom || '').trim();
      if (!room) return;
      const r = rooms.get(room) ?? { moved: 0, total: 0 };
      r.total += 1;
      if (b.isMovedToTarget) r.moved += 1;
      rooms.set(room, r);
    });
    return { total, moved, rooms: [...rooms.entries()].sort((a, b) => b[1].total - a[1].total) };
  }, [allBoxes]);

  const outline = SURFACE[theme.palette.mode].outline;
  const barButtonSx = { minHeight: 44, borderColor: 'divider', whiteSpace: 'nowrap' } as const;

  // Render a single box card
  const renderBoxCard = (box: BoxModel) => {
    const selected = selectedIds.includes(box.id);
    const moved = !!box.isMovedToTarget;
    const hits = matchedItemIds.size > 0 ? getBoxItems(box).filter((i) => matchedItemIds.has(i.id)) : [];
    const tagColor = moved ? theme.palette.success.main : theme.palette.primary.main;
    return (
      <Paper
        key={box.id}
        variant="outlined"
        sx={{
          position: 'relative',
          overflow: 'hidden',
          borderRadius: 3.5,
          borderColor: selected ? 'primary.main' : 'divider',
          boxShadow: selected ? `0 0 0 1px ${theme.palette.primary.main}` : 'none',
          transition: 'border-color .15s, box-shadow .15s',
          ...(box.isFragile && !moved && { borderLeft: `4px solid ${theme.palette.warning.main}` }),
        }}
      >
        <Box sx={{ p: 1.5, display: 'flex', gap: 1.5, alignItems: 'flex-start' }}>
          {/* The shipping tag: its number is the box identity, tapping it selects the box */}
          <ButtonBase
            onClick={() => toggleSelect(box.id)}
            aria-pressed={selected}
            aria-label={t('boxes.selectBox', { number: box.boxNumber })}
            sx={{
              position: 'relative',
              width: 68,
              height: 68,
              flexShrink: 0,
              borderRadius: '14px 14px 14px 4px',
              border: `2px ${selected ? 'solid' : 'dashed'} ${tagColor}`,
              bgcolor: selected ? tagColor : 'transparent',
              color: selected ? (moved ? 'success.contrastText' : 'primary.contrastText') : tagColor,
              fontFamily: FONT_MONO,
              fontWeight: 600,
              fontSize: 28,
              letterSpacing: '-0.04em',
              display: 'flex',
              alignItems: 'flex-end',
              justifyContent: 'flex-end',
              p: 1,
              '&::before': {
                content: '""',
                position: 'absolute',
                top: 7,
                left: 7,
                width: 8,
                height: 8,
                borderRadius: '50%',
                bgcolor: selected ? 'background.paper' : 'divider',
              },
            }}
          >
            <span aria-hidden>{box.boxNumber ?? 0}</span>
          </ButtonBase>

          <ButtonBase
            component="div"
            role="link"
            sx={{ flex: 1, minWidth: 0, display: 'block', textAlign: 'left', borderRadius: 1, '&:hover': { opacity: 0.85 } }}
            onClick={() => navigate(`/app/boxes/${box.id}/edit`)}
          >
            <Typography component="div" sx={{ fontWeight: 650, lineHeight: 1.25, display: 'flex', flexWrap: 'wrap', alignItems: 'center', columnGap: 0.5 }}>
              <span style={srOnly}>{t('boxes.current')}: </span>
              <span>{box.currentRoom || '-'}</span>
              <ArrowForward aria-hidden sx={{ fontSize: 16, color: 'text.secondary' }} />
              <span style={srOnly}>{t('boxes.target')}: </span>
              <span>{box.targetRoom || '-'}</span>
            </Typography>
            {box.description && (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }} noWrap>
                {truncateToFirstLine(box.description)}
              </Typography>
            )}
            {hits.length > 0 && (
              <Typography variant="caption" sx={{ fontFamily: FONT_MONO, color: 'primary.main', display: 'block', mt: 0.5 }}>
                {t('boxes.contains', { name: hits.map((i) => i.name).join(', ') })}
              </Typography>
            )}
            <BoxHandlingBadges box={box} />
          </ButtonBase>

          <Stack sx={{ flexShrink: 0, alignItems: 'center' }}>
            <IconButton
              onClick={() => handleQuickMoved(box)}
              aria-label={t('boxes.markMoved')}
              aria-pressed={moved}
              title={moved ? t('boxes.unmarkMoved') : t('boxes.markMoved')}
              sx={{
                width: 44,
                height: 44,
                border: `1.5px solid ${moved ? theme.palette.success.main : outline}`,
                bgcolor: moved ? 'success.main' : 'transparent',
                color: moved ? 'success.contrastText' : 'text.secondary',
                '&:hover': { bgcolor: moved ? 'success.main' : 'action.hover', color: moved ? 'success.contrastText' : 'success.main' },
              }}
            >
              <Check />
            </IconButton>
            <IconButton
              title={t('boxes.actions')}
              aria-label={t('boxes.actions')}
              onClick={() => handleOpenBoxActions(box)}
              sx={{ color: 'text.secondary' }}
            >
              <MoreVert />
            </IconButton>
          </Stack>
        </Box>

        <Divider />

        <Box sx={{ px: 1.5, py: 0.5 }}>
          <ButtonBase
            onClick={() => toggleExpandBox(box)}
            aria-expanded={expandedBoxes.has(box.id)}
            aria-label={t('boxes.expandItems', { number: box.boxNumber ?? 0 })}
            sx={{ display: 'flex', alignItems: 'center', gap: 1, width: '100%', minHeight: 44, justifyContent: 'flex-start', borderRadius: 1, userSelect: 'none', color: 'text.secondary' }}
          >
            {expandedBoxes.has(box.id) ? <ExpandLess /> : <ExpandMore />}
            <Typography variant="body2" sx={{ fontFamily: FONT_MONO, fontWeight: 500 }}>
              {getBoxItemCount(box) === 1 ? t('items.itemsCountOne') : t('items.itemsCount', { count: getBoxItemCount(box) })}
            </Typography>
          </ButtonBase>

          <Collapse in={expandedBoxes.has(box.id)} timeout="auto" unmountOnExit>
            <Stack spacing={1} sx={{ pb: 1.5, pt: 0.5 }}>
              {isLoadingItemsForBox(box.id) ? (
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                  <CircularProgress size={18} />
                  <Typography variant="body2" color="text.secondary">
                    {t('common.loading')}
                  </Typography>
                </Stack>
              ) : getBoxItems(box).length > 0 ? (
                getBoxItems(box).map((item) => (
                  <Stack
                    key={item.id}
                    direction="row"
                    spacing={1.5}
                    sx={{
                      alignItems: 'center',
                      p: 1,
                      borderRadius: 2,
                      bgcolor: matchedItemIds.has(item.id) ? alpha(theme.palette.primary.main, 0.14) : 'action.hover',
                    }}
                  >
                    {item.imageUrl ? (
                      <ButtonBase
                        aria-label={t('boxes.showPhoto', { name: item.name })}
                        sx={{ borderRadius: '50%', flexShrink: 0 }}
                        onClick={() => {
                          if (item.imageUrl) {
                            setFullImageUrl(item.imageUrl.replace('/image', '/image/large'));
                          }
                        }}
                      >
                        <Avatar src={resolveImageUrl(item.imageUrl)} alt="" slotProps={{ img: { loading: 'lazy' } }} sx={{ width: 40, height: 40 }} />
                      </ButtonBase>
                    ) : (
                      <Avatar sx={{ width: 40, height: 40, bgcolor: 'background.default', color: 'text.secondary', flexShrink: 0 }}>
                        <Inventory2 fontSize="small" />
                      </Avatar>
                    )}
                    <Typography variant="body2" sx={{ fontWeight: 500, minWidth: 0 }}>
                      {item.name}
                    </Typography>
                  </Stack>
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
    );
  };

  return (
    <Box sx={{ width: '100%', minWidth: 0, containerType: 'inline-size', pb: 4 }}>
      <Stack spacing={2} sx={{ mb: 2 }}>
        {ledger.total > 0 && (
          <Paper variant="outlined" sx={{ p: 2, borderRadius: 3.5 }} role="group" aria-label={t('boxes.progressMoved', { moved: ledger.moved, total: ledger.total })}>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'baseline', flexWrap: 'wrap' }}>
              <Typography component="span" sx={{ fontFamily: FONT_MONO, fontWeight: 600, fontSize: 40, lineHeight: 1, letterSpacing: '-0.04em', color: 'success.main' }}>
                {ledger.moved}
                <Box component="span" sx={{ color: 'text.secondary', fontSize: 24 }}>/{ledger.total}</Box>
              </Typography>
              <Typography component="span" color="text.secondary">
                {t('boxes.progressOf')}
              </Typography>
            </Stack>
            <Box aria-hidden sx={{ mt: 1.5, display: 'flex', gap: '3px' }}>
              {Array.from({ length: Math.min(ledger.total, 30) }, (_, i) => {
                const filled = i < Math.round((ledger.moved / ledger.total) * Math.min(ledger.total, 30));
                return <Box key={i} sx={{ flex: 1, height: 14, borderRadius: '3px', bgcolor: filled ? 'success.main' : 'divider' }} />;
              })}
            </Box>
            {ledger.rooms.length > 0 && (
              <Stack direction="row" spacing={1} role="group" aria-label={t('boxes.targetRoomFilter')} sx={{ mt: 1.5, overflowX: 'auto', scrollbarWidth: 'none', '&::-webkit-scrollbar': { display: 'none' }, '& > *': { flexShrink: 0 } }}>
                {ledger.rooms.map(([room, r]) => {
                  const active = roomQuery.trim().toLowerCase() === room.toLowerCase();
                  return (
                    <ButtonBase
                      key={room}
                      onClick={() => setRoomQuery(active ? '' : room)}
                      aria-pressed={active}
                      sx={{
                        minHeight: 44,
                        px: 1.5,
                        borderRadius: 2,
                        border: 1,
                        borderColor: active ? 'primary.main' : outline,
                        bgcolor: active ? 'primary.main' : 'transparent',
                        color: active ? 'primary.contrastText' : 'text.primary',
                        fontFamily: FONT_MONO,
                        fontSize: 13,
                        fontWeight: 500,
                        gap: 0.75,
                      }}
                    >
                      {room}
                      <Box component="span">{r.moved}/{r.total}</Box>
                    </ButtonBase>
                  );
                })}
              </Stack>
            )}
          </Paper>
        )}

        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <TextField
            fullWidth
            inputRef={searchRef}
            placeholder={t('boxes.itemNamePlaceholder')}
            value={itemQuery}
            onChange={(e) => setItemQuery(e.target.value)}
            slotProps={{
              input: { startAdornment: <InputAdornment position="start"><Search /></InputAdornment> },
              htmlInput: { 'aria-label': t('boxes.itemName') },
            }}
          />
          <IconButton
            onClick={() => setShowFilterPanel((v) => !v)}
            aria-expanded={showFilterPanel}
            aria-label={t('boxes.filters')}
            title={t('boxes.filters')}
            sx={{ width: 52, height: 52, border: 1, borderColor: activeFilters > 0 ? 'primary.main' : outline, borderRadius: 2.5, flexShrink: 0, color: activeFilters > 0 ? 'primary.main' : 'text.primary' }}
          >
            <Badge color="primary" badgeContent={activeFilters} invisible={activeFilters === 0}>
              <FilterList />
            </Badge>
          </IconButton>
        </Stack>

        <Collapse in={showFilterPanel} unmountOnExit>
          <Paper variant="outlined" sx={{ p: 2, borderRadius: 3.5 }}>
            <Stack spacing={1}>
              <TextField
                fullWidth
                label={t('boxes.targetRoomFilter')}
                placeholder={t('boxes.targetRoomFilterPlaceholder')}
                value={roomQuery}
                onChange={(e) => setRoomQuery(e.target.value)}
              />
              <Stack direction={isMobile ? 'column' : 'row'} spacing={isMobile ? 0 : 2}>
                <FormControlLabel
                  control={<Checkbox checked={filterFragile} onChange={(e) => setFilterFragile(e.target.checked)} sx={{ p: 1.5 }} />}
                  label={t('boxes.onlyFragileBoxes')}
                  sx={{ m: 0, minHeight: 44 }}
                />
                <FormControlLabel
                  control={<Checkbox checked={filterNoStack} onChange={(e) => setFilterNoStack(e.target.checked)} sx={{ p: 1.5 }} />}
                  label={t('boxes.onlyNoStackBoxes')}
                  sx={{ m: 0, minHeight: 44 }}
                />
                <FormControlLabel
                  control={<Checkbox checked={filterLabelPrinted} onChange={(e) => setFilterLabelPrinted(e.target.checked)} sx={{ p: 1.5 }} />}
                  label={t('boxes.onlyLabelPrintedBoxes')}
                  sx={{ m: 0, minHeight: 44 }}
                />
              </Stack>
              <Button fullWidth variant="outlined" onClick={handleResetFilter}>
                {t('boxes.reset')}
              </Button>
            </Stack>
          </Paper>
        </Collapse>

        {/* Segmented status control, defaults to "not moved yet" */}
        <Box
          role="group"
          aria-label={t('boxes.statusFilter')}
          sx={{ display: 'flex', p: '3px', gap: '3px', border: 1, borderColor: outline, borderRadius: 2.5, bgcolor: 'background.paper' }}
        >
          {([['open', 'boxes.statusOpen'], ['all', 'boxes.statusAll'], ['moved', 'boxes.statusMoved']] as const).map(([value, key]) => {
            const active = statusFilter === value;
            return (
              <ButtonBase
                key={value}
                onClick={() => setStatusFilter(value)}
                aria-pressed={active}
                sx={{
                  flex: 1,
                  minHeight: 44,
                  borderRadius: 2,
                  fontWeight: 650,
                  fontSize: '0.95rem',
                  bgcolor: active ? 'action.selected' : 'transparent',
                  color: active ? 'text.primary' : 'text.secondary',
                  boxShadow: active ? `inset 0 -2px 0 ${theme.palette.primary.main}` : 'none',
                }}
              >
                {t(key)}
              </ButtonBase>
            );
          })}
        </Box>
      </Stack>

      {isLoading ? (
        <Paper role="status" sx={{ p: 3, textAlign: 'center' }}>{t('boxes.loadingBoxes')}</Paper>
      ) : loadError ? (
        <Alert
          severity="error"
          action={
            <Button color="inherit" size="small" onClick={() => loadData()}>
              {t('common.retry')}
            </Button>
          }
        >
          {t('boxes.loadFailed')}
        </Alert>
      ) : allBoxes.length === 0 ? (
        <Paper sx={{ p: 4, textAlign: 'center', borderRadius: 2 }}>
          <Typography variant="h6" component="h2" gutterBottom>{t('boxes.emptyTitle')}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{t('boxes.emptyHint')}</Typography>
          <Button variant="contained" startIcon={<Add />} onClick={handleOpenBoxDialog}>
            {t('boxes.createFirst')}
          </Button>
        </Paper>
      ) : filteredBoxes.length === 0 ? (
        <Alert
          severity="info"
          action={<Button color="inherit" size="small" onClick={handleResetFilter}>{t('boxes.reset')}</Button>}
        >
          {t('boxes.noBoxesFound')}
        </Alert>
      ) : (
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: 'repeat(auto-fill, minmax(min(100%, 340px), 1fr))', alignItems: 'start' }}>
          {filteredBoxes.map((box) => renderBoxCard(box))}
        </Box>
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
              <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1.5 }}>
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
            disabled={isSubmittingBox}
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
          <Typography>
            {deleteTarget
              ? t('boxes.deleteBoxMessageNamed', { number: deleteTarget.boxNumber ?? 0 })
              : t('boxes.deleteBoxMessage')}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteConfirmId(null)}>{t('boxes.cancel')}</Button>
          <Button color="error" variant="contained" onClick={handleDeleteBox} disabled={isDeletingBox}>
            {t('boxes.delete')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* PDF Generation Progress */}
      <LabelGenerationProgress
        open={pdfProgress.open}
        current={pdfProgress.current}
        total={pdfProgress.total}
        currentBoxNumber={pdfProgress.currentBoxNumber}
      />

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
          {t('boxes.imagePreview')}
          <IconButton
            onClick={() => setFullImageUrl(null)}
            aria-label={t('common.close')}
            sx={{ position: 'absolute', right: 8, top: 8 }}
          >
            <Close />
          </IconButton>
        </DialogTitle>
        <DialogContent sx={{ textAlign: 'center', p: 2 }}>
          {fullImageUrl && (
            <img
              src={fullImageUrl}
              alt={t('items.fullImageAlt')}
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
          <Button onClick={() => setFullImageUrl(null)}>{t('common.close')}</Button>
        </DialogActions>
      </Dialog>

      {/* Selection bar: rises above the thumb bar while boxes are selected */}
      {selectedBoxes.length > 0 && (
        <Paper
          role="toolbar"
          aria-label={t('boxes.bulkUpdate')}
          sx={{
            // fixed on phones (the list scrolls inside clipped wrappers, which breaks sticky); pinned in the list pane on desktop
            position: { xs: 'fixed', md: 'sticky' },
            left: { xs: 12, md: 'auto' },
            right: { xs: 12, md: 'auto' },
            bottom: { xs: 'calc(92px + env(safe-area-inset-bottom))', md: 12 },
            zIndex: 1050,
            mt: 2,
            p: 1,
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            gap: 1,
            borderRadius: 3.5,
            border: 1,
            borderColor: 'primary.main',
            bgcolor: 'background.paper',
            boxShadow: '0 10px 30px rgba(0,0,0,.35)',
          }}
        >
          <IconButton onClick={clearSelection} aria-label={t('boxes.clearSelection')} title={t('boxes.clearSelection')}>
            <Close />
          </IconButton>
          <Typography sx={{ fontFamily: FONT_MONO, fontWeight: 600, pr: 0.5 }}>{t('boxes.boxesSelectedCount', { count: selectedBoxes.length })}</Typography>
          <Button variant="outlined" startIcon={<LocalShipping />} onClick={handleBulkUpdateMoved} sx={barButtonSx}>
            {allSelectedMoved ? t('boxes.removeMoved') : t('boxes.setMoved')}
          </Button>
          <Button variant="outlined" startIcon={<Label />} onClick={handleBulkUpdateLabelPrinted} sx={barButtonSx}>
            {allSelectedPrinted ? t('boxes.removeLabelPrinted') : t('boxes.setLabelPrinted')}
          </Button>
          <Button variant="contained" startIcon={<Print />} onClick={handlePrintWithDialog} sx={barButtonSx}>
            {t('boxes.printLabels')}
          </Button>
        </Paper>
      )}

      <Snackbar
        open={undo.open}
        autoHideDuration={4000}
        onClose={(_, reason) => reason !== 'clickaway' && setUndo((u) => ({ ...u, open: false }))}
        message={undo.wasMoved ? t('boxes.boxReopenedToast', { number: undo.number }) : t('boxes.boxMovedToast', { number: undo.number })}
        action={<Button color="primary" size="small" onClick={handleUndo}>{t('boxes.undo')}</Button>}
        sx={{ bottom: { xs: 96, md: 24 } }}
      />

      {/* Print Dialog */}
      <Dialog open={showPrintDialog} onClose={() => setShowPrintDialog(false)}>
        <DialogTitle>{t('boxes.printLabelsForBoxes', { count: selectedBoxes.length })}</DialogTitle>
        <DialogContent>
          <FormControlLabel
            control={
              <Checkbox
                checked={setLabelPrintedAfterPrint}
                onChange={(e) => setSetLabelPrintedAfterPrint(e.target.checked)}
              />
            }
            label={t('boxes.markAllAsPrinted')}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowPrintDialog(false)}>{t('boxes.cancel')}</Button>
          <Button onClick={handleProceedWithPrint} variant="contained">{t('boxes.print')}</Button>
        </DialogActions>
      </Dialog>

      {/* Bottom Sheet for Box Actions */}
      <Drawer
        anchor="bottom"
        open={drawerOpen}
        onClose={handleCloseDrawer}
        slotProps={{
          paper: { sx: {
            borderTopLeftRadius: 16,
            borderTopRightRadius: 16,
            maxHeight: '70vh',
          } },
        }}
      >
        <Box sx={{ p: 2 }}>
          <Typography variant="h6" sx={{ mb: 2, textAlign: 'center' }}>
            {selectedBoxForActions ? t('boxes.boxNumber', { number: selectedBoxForActions.boxNumber ?? 0 }) : ''}
          </Typography>
          <List>
            <ListItemButton
              onClick={handleDrawerToggleMoved}
              sx={{ minHeight: 56, borderRadius: 1, mb: 1 }}
            >
              <ListItemIcon>
                <LocalShipping sx={{ color: 'text.secondary' }} />
              </ListItemIcon>
              <ListItemText primary={selectedBoxForActions?.isMovedToTarget ? t('boxes.unmarkMoved') : t('boxes.markMoved')} />
            </ListItemButton>
            <ListItemButton
              onClick={handleDrawerEdit}
              sx={{ minHeight: 56, borderRadius: 1, mb: 1 }}
            >
              <ListItemIcon>
                <Edit sx={{ color: "text.secondary" }} />
              </ListItemIcon>
              <ListItemText primary={t('boxes.edit')} />
            </ListItemButton>
            <ListItemButton
              onClick={handleDrawerCopyLink}
              sx={{ minHeight: 56, borderRadius: 1, mb: 1 }}
            >
              <ListItemIcon>
                <ContentCopy sx={{ color: "text.secondary" }} />
              </ListItemIcon>
              <ListItemText primary={t('boxes.copyPublicLink')} />
            </ListItemButton>
            <ListItemButton
              onClick={handleDrawerOpenLink}
              sx={{ minHeight: 56, borderRadius: 1, mb: 1 }}
            >
              <ListItemIcon>
                <OpenInNew sx={{ color: "text.secondary" }} />
              </ListItemIcon>
              <ListItemText primary={t('boxes.openPublicLink')} />
            </ListItemButton>
            <ListItemButton
              onClick={handleDrawerDelete}
              sx={{ minHeight: 56, borderRadius: 1, mb: 1 }}
            >
              <ListItemIcon>
                <Delete sx={{ color: "text.secondary" }} />
              </ListItemIcon>
              <ListItemText primary={t('boxes.delete')} />
            </ListItemButton>
          </List>
        </Box>
      </Drawer>
    </Box>
  );
}

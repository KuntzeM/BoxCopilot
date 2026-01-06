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
import { LabelGenerationProgress } from '../components/LabelGenerationProgress';
import { useBoxListLogic } from '../hooks/useBoxListLogic';

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

  // Use custom hook for all business logic
  const {
    filteredBoxes,
    matchedItemIds,
    isLoading,
    itemQuery,
    setItemQuery,
    roomQuery,
    setRoomQuery,
    filterFragile,
    setFilterFragile,
    filterNoStack,
    setFilterNoStack,
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
    fullImageUrl,
    setFullImageUrl,
    drawerOpen,
    selectedBoxForActions,
    resolveImageUrl,
    withApiBase,
    handleResetFilter,
    toggleSelect,
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
    handleDrawerDelete,
  } = useBoxListLogic();


  // Render a single box card (for both regular and virtualized rendering)
  const renderBoxCard = useCallback((box: BoxModel) => (
    <Paper key={box.id} sx={{ borderRadius: 2, boxShadow: 2 }}>
      <Box sx={{ p: 2 }}>
        <Stack direction="row" spacing={2} alignItems="center" justifyContent="space-between" flexWrap="wrap">
          <Stack direction="row" spacing={2} alignItems="center" sx={{ flex: 1, minWidth: 200 }}>
            <Checkbox
              checked={selectedIds.includes(box.id)}
              onChange={() => toggleSelect(box.id)}
              inputProps={{ 'aria-label': t('boxes.selectBox', { number: box.boxNumber }) }}
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
              <Typography variant="h6">{t('boxes.boxNumber', { number: box.boxNumber })}</Typography>
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
            {selectedBoxForActions ? t('boxes.boxNumber', { number: selectedBoxForActions.boxNumber }) : ''}
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

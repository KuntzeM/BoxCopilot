import React, { useState } from 'react';
import {
  Box,
  Paper,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  Checkbox,
  Typography,
  Avatar,
  Toolbar,
  Chip,
  Drawer,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  useMediaQuery,
  useTheme,
  Stack,
} from '@mui/material';
import { 
  Edit, 
  Delete, 
  DriveFileMove, 
  Image as ImageIcon,
  MoreVert,
} from '@mui/icons-material';
import { Item, UpdateItemPayload } from '../types/models';
import { useTranslation } from '../hooks/useTranslation';
import ItemImageUpload from './ItemImageUpload';
import ItemMoveDialog from './ItemMoveDialog';

interface EnhancedItemsTableProps {
  items: Item[];
  onUpdateItem: (itemId: number, data: UpdateItemPayload) => Promise<void>;
  onDeleteItem: (itemId: number) => Promise<void>;
  onMoveItems: (itemIds: number[], targetBoxUuid: string) => Promise<void>;
  onImageUpdated: () => void;
  onError: (message: string) => void;
  onSuccess: (message: string) => void;
}

const EnhancedItemsTable: React.FC<EnhancedItemsTableProps> = ({
  items,
  onUpdateItem,
  onDeleteItem,
  onMoveItems,
  onImageUpdated,
  onError,
  onSuccess,
}) => {
  const { t } = useTranslation();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  
  const [editingItem, setEditingItem] = useState<Item | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<number | null>(null);
  const [editData, setEditData] = useState<UpdateItemPayload>({ name: '' });
  const [isLoading, setIsLoading] = useState(false);
  const [selectedItems, setSelectedItems] = useState<Set<number>>(new Set());
  const [moveDialogOpen, setMoveDialogOpen] = useState(false);
  const [imageDialogOpen, setImageDialogOpen] = useState(false);
  const [fullImageUrl, setFullImageUrl] = useState<string | null>(null);
  const [imageItemId, setImageItemId] = useState<number | null>(null);
  
  // Bottom sheet state for mobile actions
  const [actionDrawerOpen, setActionDrawerOpen] = useState(false);
  const [selectedItemForActions, setSelectedItemForActions] = useState<Item | null>(null);

  // Ensure image URLs use API origin (works without nginx proxy)
  const apiBase = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');
  const withApiBase = (path: string) =>
    apiBase ? `${apiBase}${path.startsWith('/') ? path : `/${path}`}` : path;
  const resolveImageUrl = (url?: string) => {
    if (!url) return undefined;
    if (url.startsWith('http://') || url.startsWith('https://')) return url;
    return withApiBase(url.startsWith('/') ? url : `/${url}`);
  };

  const handleEditClick = (item: Item) => {
    setEditingItem(item);
    setEditData({ name: item.name });
  };

  const handleEditSave = async () => {
    if (!editingItem) return;
    setIsLoading(true);
    try {
      await onUpdateItem(editingItem.id, editData);
      setEditingItem(null);
    } finally {
      setIsLoading(false);
    }
  };

  const handleDeleteConfirm = async () => {
    if (deleteConfirm === null) return;
    setIsLoading(true);
    try {
      await onDeleteItem(deleteConfirm);
      setDeleteConfirm(null);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSelectAll = () => {
    if (selectedItems.size === items.length) {
      setSelectedItems(new Set());
    } else {
      setSelectedItems(new Set(items.map(item => item.id)));
    }
  };

  const handleSelectItem = (itemId: number) => {
    const newSelected = new Set(selectedItems);
    if (newSelected.has(itemId)) {
      newSelected.delete(itemId);
    } else {
      newSelected.add(itemId);
    }
    setSelectedItems(newSelected);
  };

  const handleMoveSelected = async (targetBoxUuid: string, boxDescription: string) => {
    try {
      await onMoveItems(Array.from(selectedItems), targetBoxUuid);
      onSuccess(`${selectedItems.size} Items wurden in Box '${boxDescription}' verschoben`);
      setSelectedItems(new Set());
    } catch (error) {
      onError('Fehler beim Verschieben');
    }
  };

  const handleMoveSingle = (itemId: number) => {
    setSelectedItems(new Set([itemId]));
    setMoveDialogOpen(true);
  };

  const handleImageClick = (itemId: number, imageUrl: string) => {
    // Convert thumbnail URL to large image URL
    const largeImageUrl = imageUrl.replace('/image', '/image/large');
    setFullImageUrl(withApiBase(largeImageUrl));
  };

  const handleManageImage = (itemId: number, currentImageUrl?: string) => {
    setImageItemId(itemId);
    setImageDialogOpen(true);
  };

  const handleOpenItemActions = (item: Item) => {
    setSelectedItemForActions(item);
    setActionDrawerOpen(true);
  };

  const handleCloseActionDrawer = () => {
    setActionDrawerOpen(false);
    setSelectedItemForActions(null);
  };

  const handleDrawerEdit = () => {
    if (selectedItemForActions) {
      handleEditClick(selectedItemForActions);
    }
    handleCloseActionDrawer();
  };

  const handleDrawerImage = () => {
    if (selectedItemForActions) {
      handleManageImage(selectedItemForActions.id, selectedItemForActions.imageUrl);
    }
    handleCloseActionDrawer();
  };

  const handleDrawerMove = () => {
    if (selectedItemForActions) {
      handleMoveSingle(selectedItemForActions.id);
    }
    handleCloseActionDrawer();
  };

  const handleDrawerDelete = () => {
    if (selectedItemForActions) {
      setDeleteConfirm(selectedItemForActions.id);
    }
    handleCloseActionDrawer();
  };

  if (items.length === 0) {
    return (
      <Box sx={{ py: 3, textAlign: 'center', color: 'text.secondary' }}>
        {t('items.noItems')}
      </Box>
    );
  }

  return (
    <>
      {selectedItems.size > 0 && (
        <Toolbar sx={{ bgcolor: 'primary.light', mb: 2, borderRadius: 1 }}>
          <Typography sx={{ flex: '1 1 100%' }} color="white" variant="subtitle1">
            {selectedItems.size} ausgewählt
          </Typography>
          <Button
            variant="contained"
            color="secondary"
            startIcon={<DriveFileMove />}
            onClick={() => setMoveDialogOpen(true)}
          >
            Verschieben
          </Button>
        </Toolbar>
      )}

      {/* Card-based layout for items */}
      <Stack spacing={1} sx={{ mt: 2 }}>
        {items.map((item) => (
          <Paper
            key={item.id}
            variant="outlined"
            onClick={() => handleSelectItem(item.id)}
            sx={{
              p: 1.5,
              borderRadius: 1.5,
              display: 'flex',
              alignItems: 'center',
              gap: 1.5,
              cursor: 'pointer',
              transition: 'all 0.2s ease',
              backgroundColor: selectedItems.has(item.id)
                ? 'primary.light'
                : 'background.paper',
              borderColor: selectedItems.has(item.id)
                ? 'primary.main'
                : 'divider',
              borderWidth: selectedItems.has(item.id) ? 2 : 1,
              '&:hover': {
                boxShadow: 1,
                backgroundColor: selectedItems.has(item.id)
                  ? 'primary.light'
                  : 'action.hover',
              },
              userSelect: 'none',
            }}
          >
            {/* Image */}
            <Box sx={{ flexShrink: 0 }}>
              {item.imageUrl ? (
                <Avatar
                  src={resolveImageUrl(item.imageUrl)}
                  alt={item.name}
                  loading="lazy"
                  sx={{ width: 48, height: 48, cursor: 'pointer' }}
                  onClick={(e) => {
                    e.stopPropagation();
                    handleImageClick(item.id, item.imageUrl!);
                  }}
                />
              ) : (
                <Avatar sx={{ width: 48, height: 48, bgcolor: 'grey.300' }}>
                  📦
                </Avatar>
              )}
            </Box>

            {/* Item Name */}
            <Typography
              variant="body2"
              sx={{
                flex: 1,
                fontWeight: 500,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                color: selectedItems.has(item.id)
                  ? 'primary.dark'
                  : 'text.primary',
              }}
            >
              {item.name}
            </Typography>

            {/* Checkmark indicator for selected */}
            {selectedItems.has(item.id) && (
              <Typography
                sx={{
                  flexShrink: 0,
                  fontSize: '1.25rem',
                  color: 'primary.main',
                }}
              >
                ✓
              </Typography>
            )}

            {/* Actions */}
            <Box
              sx={{ flexShrink: 0, display: 'flex', gap: 0.5 }}
              onClick={(e) => e.stopPropagation()}
            >
              {isMobile ? (
                <IconButton
                  size="small"
                  onClick={() => handleOpenItemActions(item)}
                  sx={{ minWidth: 40, minHeight: 40 }}
                >
                  <MoreVert fontSize="small" />
                </IconButton>
              ) : (
                <>
                  <IconButton
                    size="small"
                    color="primary"
                    onClick={() => handleManageImage(item.id, item.imageUrl)}
                    title="Bild verwalten"
                    sx={{ minWidth: 36, minHeight: 36 }}
                  >
                    <ImageIcon fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    color="info"
                    onClick={() => handleMoveSingle(item.id)}
                    title="Verschieben"
                    sx={{ minWidth: 36, minHeight: 36 }}
                  >
                    <DriveFileMove fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    color="primary"
                    onClick={() => handleEditClick(item)}
                    title={t('items.edit')}
                    sx={{ minWidth: 36, minHeight: 36 }}
                  >
                    <Edit fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    color="error"
                    onClick={() => setDeleteConfirm(item.id)}
                    title={t('items.delete')}
                    sx={{ minWidth: 36, minHeight: 36 }}
                  >
                    <Delete fontSize="small" />
                  </IconButton>
                </>
              )}
            </Box>
          </Paper>
        ))}
      </Stack>

      {/* Edit Dialog */}
      <Dialog open={editingItem !== null} onClose={() => !isLoading && setEditingItem(null)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('items.editItem')}</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 2 }}>
          <TextField
            label={t('items.name')}
            value={editData.name}
            onChange={(e) => setEditData({ ...editData, name: e.target.value })}
            fullWidth
          />
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button 
            onClick={() => setEditingItem(null)} 
            disabled={isLoading}
            fullWidth
            size="large"
            variant="outlined"
            sx={{ minHeight: 56 }}
          >
            {t('items.cancel')}
          </Button>
          <Button 
            onClick={handleEditSave} 
            variant="contained" 
            disabled={isLoading || !editData.name.trim()}
            fullWidth
            size="large"
            sx={{ minHeight: 56 }}
          >
            {t('items.save')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteConfirm !== null} onClose={() => !isLoading && setDeleteConfirm(null)}>
        <DialogTitle>{t('items.deleteItem')}</DialogTitle>
        <DialogContent>{t('items.deleteMessage')}</DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button 
            onClick={() => setDeleteConfirm(null)} 
            disabled={isLoading}
            fullWidth
            size="large"
            variant="outlined"
            sx={{ minHeight: 56 }}
          >
            {t('items.cancel')}
          </Button>
          <Button 
            onClick={handleDeleteConfirm} 
            color="error" 
            variant="contained" 
            disabled={isLoading}
            fullWidth
            size="large"
            sx={{ minHeight: 56 }}
          >
            {t('items.delete')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Move Dialog */}
      <ItemMoveDialog
        open={moveDialogOpen}
        onClose={() => {
          setMoveDialogOpen(false);
          if (selectedItems.size === 1) {
            setSelectedItems(new Set());
          }
        }}
        itemIds={Array.from(selectedItems)}
        onMove={handleMoveSelected}
      />

      {/* Image Management Dialog */}
      <Dialog open={imageDialogOpen} onClose={() => setImageDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Bild verwalten</DialogTitle>
        <DialogContent sx={{ pt: 2 }}>
          {imageItemId && (
            <ItemImageUpload
              itemId={imageItemId}
              currentImageUrl={items.find(i => i.id === imageItemId)?.imageUrl}
              onImageUpdated={() => {
                onImageUpdated();
                setImageDialogOpen(false);
              }}
              onError={onError}
              onSuccess={onSuccess}
            />
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setImageDialogOpen(false)}>Schließen</Button>
        </DialogActions>
      </Dialog>

      {/* Full Image Dialog */}
      <Dialog open={fullImageUrl !== null} onClose={() => setFullImageUrl(null)} maxWidth="md" fullWidth>
        <DialogContent sx={{ p: 0, textAlign: 'center' }}>
          {fullImageUrl && (
            <img
              src={fullImageUrl}
              alt="Item full"
              style={{ 
                width: '100%', 
                height: 'auto', 
                display: 'block',
                maxHeight: '80vh',
                objectFit: 'contain'
              }}
              onError={(e) => {
                // If large image fails, fall back to thumbnail
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

      {/* Bottom Sheet for Item Actions (Mobile) */}
      <Drawer
        anchor="bottom"
        open={actionDrawerOpen}
        onClose={handleCloseActionDrawer}
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
            {selectedItemForActions?.name}
          </Typography>
          <List>
            <ListItemButton
              onClick={handleDrawerEdit}
              sx={{ minHeight: 56, borderRadius: 1, mb: 1 }}
            >
              <ListItemIcon>
                <Edit color="primary" />
              </ListItemIcon>
              <ListItemText primary={t('items.edit')} />
            </ListItemButton>
            <ListItemButton
              onClick={handleDrawerImage}
              sx={{ minHeight: 56, borderRadius: 1, mb: 1 }}
            >
              <ListItemIcon>
                <ImageIcon color="primary" />
              </ListItemIcon>
              <ListItemText primary="Bild verwalten" />
            </ListItemButton>
            <ListItemButton
              onClick={handleDrawerMove}
              sx={{ minHeight: 56, borderRadius: 1, mb: 1 }}
            >
              <ListItemIcon>
                <DriveFileMove color="info" />
              </ListItemIcon>
              <ListItemText primary="Verschieben" />
            </ListItemButton>
            <ListItemButton
              onClick={handleDrawerDelete}
              sx={{ minHeight: 56, borderRadius: 1, mb: 1 }}
            >
              <ListItemIcon>
                <Delete color="error" />
              </ListItemIcon>
              <ListItemText primary={t('items.delete')} />
            </ListItemButton>
          </List>
        </Box>
      </Drawer>
    </>
  );
};

export default EnhancedItemsTable;

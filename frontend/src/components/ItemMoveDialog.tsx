import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  Typography,
  Box,
  CircularProgress,
} from '@mui/material';
import { Box as BoxType } from '../types/models';
import { listBoxes } from '../services/boxService';
import { useTranslation } from '../hooks/useTranslation';

interface ItemMoveDialogProps {
  open: boolean;
  onClose: () => void;
  itemIds: number[];
  onMove: (targetBoxId: number, boxDescription: string) => void;
}

export default function ItemMoveDialog({
  open,
  onClose,
  itemIds,
  onMove,
}: ItemMoveDialogProps) {
  const { t } = useTranslation();
  const [boxes, setBoxes] = useState<BoxType[]>([]);
  const [filteredBoxes, setFilteredBoxes] = useState<BoxType[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedBox, setSelectedBox] = useState<BoxType | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);

  useEffect(() => {
    if (open) {
      loadBoxes();
      setSearchQuery('');
      setSelectedBox(null);
    }
  }, [open]);

  useEffect(() => {
    if (!searchQuery) {
      setFilteredBoxes(boxes);
    } else {
      const query = searchQuery.toLowerCase();
      const filtered = boxes.filter(box =>
        (box.boxNumber ?? box.id).toString().includes(query) ||
        (box.currentRoom?.toLowerCase().includes(query) || false) ||
        (box.targetRoom?.toLowerCase().includes(query) || false) ||
        (box.description?.toLowerCase().includes(query) || false)
      );
      setFilteredBoxes(filtered);
    }
  }, [searchQuery, boxes]);

  const loadBoxes = async () => {
    setLoading(true);
    setLoadError(false);
    try {
      const data = await listBoxes();
      setBoxes(data);
      setFilteredBoxes(data);
    } catch (error) {
      console.error('Failed to load boxes:', error);
      setLoadError(true);
    } finally {
      setLoading(false);
    }
  };

  const handleBoxSelect = (box: BoxType) => {
    setSelectedBox(box);
    setConfirmOpen(true);
  };

  const handleConfirmMove = () => {
    if (selectedBox) {
      const boxDesc = selectedBox.description || selectedBox.currentRoom || t('boxes.boxNumber', { number: selectedBox.boxNumber ?? selectedBox.id });
      onMove(selectedBox.id, boxDesc);
      setConfirmOpen(false);
      onClose();
    }
  };

  const getBoxDisplayText = (box: BoxType) => {
    const parts = [t('boxes.boxNumber', { number: box.boxNumber ?? box.id })];
    if (box.currentRoom) parts.push(`${t('boxes.from')}: ${box.currentRoom}`);
    if (box.targetRoom) parts.push(`${t('boxes.to')}: ${box.targetRoom}`);
    if (box.description) parts.push(box.description);
    return parts.join(' | ');
  };

  return (
    <>
      <Dialog open={open && !confirmOpen} onClose={onClose} maxWidth="sm" fullWidth>
        <DialogTitle>
          {t('items.moveTitle')}
          <Typography variant="caption" sx={{ display: 'block', mt: 0.5 }}>
            {t('items.moveCount', { count: itemIds.length })}
          </Typography>
        </DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            label={t('items.moveSearch')}
            variant="outlined"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder={t('items.moveSearchPlaceholder')}
            sx={{ mb: 2 }}
            autoFocus
          />

          {loading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}>
              <CircularProgress />
            </Box>
          ) : loadError ? (
            <Typography color="error" align="center" sx={{ py: 3 }}>
              {t('errors.boxesFetchFailed')}
            </Typography>
          ) : filteredBoxes.length === 0 ? (
            <Typography color="text.secondary" align="center" sx={{ py: 3 }}>
              {t('boxes.noBoxesFound')}
            </Typography>
          ) : (
            <List sx={{ maxHeight: 400, overflow: 'auto' }}>
              {filteredBoxes.map((box) => (
                <ListItem key={box.id} disablePadding>
                  <ListItemButton onClick={() => handleBoxSelect(box)}>
                    <ListItemText
                      primary={getBoxDisplayText(box)}
                    />
                  </ListItemButton>
                </ListItem>
              ))}
            </List>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>{t('common.cancel')}</Button>
        </DialogActions>
      </Dialog>

      {/* Confirmation Dialog */}
      <Dialog open={confirmOpen} onClose={() => setConfirmOpen(false)}>
        <DialogTitle>{t('items.moveConfirmTitle')}</DialogTitle>
        <DialogContent>
          <Typography>
            {t('items.moveConfirmMessage', {
              count: itemIds.length,
              box: selectedBox ? getBoxDisplayText(selectedBox) : '',
            })}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmOpen(false)}>{t('common.cancel')}</Button>
          <Button variant="contained" onClick={handleConfirmMove}>
            {t('items.move')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

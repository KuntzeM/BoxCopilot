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
  const [boxes, setBoxes] = useState<BoxType[]>([]);
  const [filteredBoxes, setFilteredBoxes] = useState<BoxType[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedBox, setSelectedBox] = useState<BoxType | null>(null);
  const [loading, setLoading] = useState(false);
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
        (box.id.toString().includes(query)) ||
        (box.currentRoom?.toLowerCase().includes(query) || false) ||
        (box.targetRoom?.toLowerCase().includes(query) || false) ||
        (box.description?.toLowerCase().includes(query) || false)
      );
      setFilteredBoxes(filtered);
    }
  }, [searchQuery, boxes]);

  const loadBoxes = async () => {
    setLoading(true);
    try {
      const data = await listBoxes();
      setBoxes(data);
      setFilteredBoxes(data);
    } catch (error) {
      console.error('Failed to load boxes:', error);
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
      const boxDesc = selectedBox.description || selectedBox.currentRoom || `Box ${selectedBox.id}`;
      onMove(selectedBox.id, boxDesc);
      setConfirmOpen(false);
      onClose();
    }
  };

  const getBoxDisplayText = (box: BoxType) => {
    const parts = [`ID: ${box.id}`];
    if (box.currentRoom) parts.push(`Von: ${box.currentRoom}`);
    if (box.targetRoom) parts.push(`Nach: ${box.targetRoom}`);
    if (box.description) parts.push(box.description);
    return parts.join(' | ');
  };

  return (
    <>
      <Dialog open={open && !confirmOpen} onClose={onClose} maxWidth="sm" fullWidth>
        <DialogTitle>
          Ziel-Box auswählen
          <Typography variant="caption" display="block" sx={{ mt: 0.5 }}>
            {itemIds.length} Item{itemIds.length !== 1 ? 's' : ''} verschieben
          </Typography>
        </DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            label="Suchen"
            variant="outlined"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Nach ID, Von, Nach oder Beschreibung suchen..."
            sx={{ mb: 2 }}
            autoFocus
          />

          {loading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}>
              <CircularProgress />
            </Box>
          ) : filteredBoxes.length === 0 ? (
            <Typography color="text.secondary" align="center" sx={{ py: 3 }}>
              Keine Boxen gefunden
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
          <Button onClick={onClose}>Abbrechen</Button>
        </DialogActions>
      </Dialog>

      {/* Confirmation Dialog */}
      <Dialog open={confirmOpen} onClose={() => setConfirmOpen(false)}>
        <DialogTitle>Verschieben bestätigen</DialogTitle>
        <DialogContent>
          <Typography>
            {itemIds.length} Item{itemIds.length !== 1 ? 's' : ''} in Box '
            {selectedBox ? getBoxDisplayText(selectedBox) : ''}' verschieben?
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmOpen(false)}>Abbrechen</Button>
          <Button variant="contained" onClick={handleConfirmMove}>
            Verschieben
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

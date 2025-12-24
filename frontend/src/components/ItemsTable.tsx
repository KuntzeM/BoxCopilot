import React, { useState } from 'react';
import {
  Box,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
} from '@mui/material';
import { Edit, Delete } from '@mui/icons-material';
import { Item, UpdateItemPayload } from '../types/models';

interface ItemsTableProps {
  items: Item[];
  onUpdateItem: (itemId: number, data: UpdateItemPayload) => Promise<void>;
  onDeleteItem: (itemId: number) => Promise<void>;
}

const ItemsTable: React.FC<ItemsTableProps> = ({ items, onUpdateItem, onDeleteItem }) => {
  const [editingItem, setEditingItem] = useState<Item | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<number | null>(null);
  const [editData, setEditData] = useState<UpdateItemPayload>({ name: '' });
  const [isLoading, setIsLoading] = useState(false);

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

  if (items.length === 0) {
    return (
      <Box sx={{ py: 3, textAlign: 'center', color: 'text.secondary' }}>
        Keine Items vorhanden
      </Box>
    );
  }

  return (
    <>
      <TableContainer component={Paper} sx={{ mt: 3 }}>
        <Table>
          <TableHead>
            <TableRow sx={{ backgroundColor: 'primary.main' }}>
              <TableCell sx={{ color: 'white' }}>Name</TableCell>
              <TableCell sx={{ color: 'white' }}>Aktionen</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {items.map((item) => (
              <TableRow key={item.id} hover>
                <TableCell>{item.name}</TableCell>
                <TableCell>
                  <IconButton
                    size="small"
                    color="primary"
                    onClick={() => handleEditClick(item)}
                    title="Bearbeiten"
                  >
                    <Edit fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    color="error"
                    onClick={() => setDeleteConfirm(item.id)}
                    title="Löschen"
                  >
                    <Delete fontSize="small" />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Edit Dialog */}
      <Dialog open={editingItem !== null} onClose={() => !isLoading && setEditingItem(null)} maxWidth="sm" fullWidth>
        <DialogTitle>Item bearbeiten</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 2 }}>
          <TextField
            label="Name"
            value={editData.name}
            onChange={(e) => setEditData({ ...editData, name: e.target.value })}
            fullWidth
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditingItem(null)} disabled={isLoading}>
            Abbrechen
          </Button>
          <Button onClick={handleEditSave} variant="contained" disabled={isLoading || !editData.name.trim()}>
            Speichern
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteConfirm !== null} onClose={() => !isLoading && setDeleteConfirm(null)}>
        <DialogTitle>Item löschen?</DialogTitle>
        <DialogContent>Dieses Item wird dauerhaft gelöscht.</DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteConfirm(null)} disabled={isLoading}>
            Abbrechen
          </Button>
          <Button onClick={handleDeleteConfirm} color="error" variant="contained" disabled={isLoading}>
            Löschen
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default ItemsTable;

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
import { useTranslation } from '../hooks/useTranslation';

interface ItemsTableProps {
  items: Item[];
  onUpdateItem: (itemId: number, data: UpdateItemPayload) => Promise<void>;
  onDeleteItem: (itemId: number) => Promise<void>;
}

const ItemsTable: React.FC<ItemsTableProps> = ({ items, onUpdateItem, onDeleteItem }) => {
  const { t } = useTranslation();
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
        {t('items.noItems')}
      </Box>
    );
  }

  return (
    <>
      <TableContainer component={Paper} sx={{ mt: 3 }}>
        <Table>
          <TableHead>
            <TableRow sx={{ backgroundColor: 'primary.main' }}>
              <TableCell sx={{ color: 'white' }}>{t('items.name')}</TableCell>
              <TableCell sx={{ color: 'white' }}>{t('items.actions')}</TableCell>
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
                    title={t('items.edit')}
                  >
                    <Edit fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    color="error"
                    onClick={() => setDeleteConfirm(item.id)}
                    title={t('items.delete')}
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
        <DialogTitle>{t('items.editItem')}</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 2 }}>
          <TextField
            label={t('items.name')}
            value={editData.name}
            onChange={(e) => setEditData({ ...editData, name: e.target.value })}
            fullWidth
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditingItem(null)} disabled={isLoading}>
            {t('items.cancel')}
          </Button>
          <Button onClick={handleEditSave} variant="contained" disabled={isLoading || !editData.name.trim()}>
            {t('items.save')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteConfirm !== null} onClose={() => !isLoading && setDeleteConfirm(null)}>
        <DialogTitle>{t('items.deleteItem')}</DialogTitle>
        <DialogContent>{t('items.deleteMessage')}</DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteConfirm(null)} disabled={isLoading}>
            {t('items.cancel')}
          </Button>
          <Button onClick={handleDeleteConfirm} color="error" variant="contained" disabled={isLoading}>
            {t('items.delete')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default ItemsTable;

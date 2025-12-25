import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Box, Button, Paper, Typography, Alert, CircularProgress, Stack, Snackbar } from '@mui/material';
import { ArrowBack } from '@mui/icons-material';
import * as boxService from '../services/boxService';
import * as itemService from '../services/itemService';
import { Box as BoxType, Item, CreateItemPayload, UpdateItemPayload } from '../types/models';
import BoxForm, { BoxFormData } from '../components/BoxForm';
import ItemsTable from '../components/ItemsTable';
import ItemForm from '../components/ItemForm';
import { useTranslation } from '../hooks/useTranslation';

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

  const boxId = id ? parseInt(id) : null;

  useEffect(() => {
    if (!boxId) {
      setError(t('errors.invalidBoxId'));
      setIsLoading(false);
      return;
    }

    const loadBoxAndItems = async () => {
      try {
        // Fetch all boxes and find the one with matching ID
        const allBoxes = await boxService.fetchBoxes();
        const boxData = allBoxes.find((b) => b.id === boxId);
        
        if (!boxData) {
          setError(t('errors.boxNotFound'));
          setIsLoading(false);
          return;
        }

        setBox(boxData);

        // Filtere Items für diese Box
        const allItems = boxData.items || [];
        setItems(allItems);
        setError(null);
      } catch (err) {
        setError(t('errors.boxLoadFailed'));
        console.error(err);
      } finally {
        setIsLoading(false);
      }
    };

    loadBoxAndItems();
  }, [boxId, t]);

  const [formData, setFormData] = useState<BoxFormData>({
    currentRoom: '',
    targetRoom: '',
    description: '',
    isFragile: false,
    noStack: false,
  });

  useEffect(() => {
    if (box) {
      setFormData({
        currentRoom: box.currentRoom || '',
        targetRoom: box.targetRoom || '',
        description: box.description || '',
        isFragile: box.isFragile || false,
        noStack: box.noStack || false,
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
      });

      setSnackbar({ message: t('success.boxSaved'), severity: 'success' });
      setTimeout(() => {
        navigate('/app/boxes');
      }, 1500);
    } catch (err) {
      setSnackbar({ message: t('errors.boxSaveFailed'), severity: 'error' });
      console.error(err);
    } finally {
      setIsSaving(false);
    }
  };

  const handleAddItem = async (payload: CreateItemPayload) => {
    try {
      const newItem = await itemService.createItem(payload);
      setItems([...items, newItem]);
      setSnackbar({ message: t('success.itemAdded'), severity: 'success' });
    } catch (err) {
      setSnackbar({ message: t('errors.itemAddFailed'), severity: 'error' });
      console.error(err);
    }
  };

  const handleUpdateItem = async (itemId: number, data: UpdateItemPayload) => {
    try {
      const updatedItem = await itemService.updateItem(itemId, data);
      setItems(items.map((item) => (item.id === itemId ? updatedItem : item)));
      setSnackbar({ message: t('success.itemUpdated'), severity: 'success' });
    } catch (err) {
      setSnackbar({ message: t('errors.itemUpdateFailed'), severity: 'error' });
      console.error(err);
    }
  };

  const handleDeleteItem = async (itemId: number) => {
    try {
      await itemService.deleteItem(itemId);
      setItems(items.filter((item) => item.id !== itemId));
      setSnackbar({ message: t('success.itemDeleted'), severity: 'success' });
    } catch (err) {
      setSnackbar({ message: t('errors.itemDeleteFailed'), severity: 'error' });
      console.error(err);
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
        <Alert severity="error">{error || t('errors.boxLoadFailed')}</Alert>
      </Box>
    );
  }

  return (
    <Box>
      <Button
        startIcon={<ArrowBack />}
        onClick={() => navigate('/app/boxes')}
        sx={{ mb: 3 }}
      >
        {t('boxes.back')}
      </Button>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h5" sx={{ mb: 3 }}>
          {t('boxes.editBox', { number: box.id })}
        </Typography>
        <BoxForm data={formData} onChange={setFormData} />
      </Paper>

      <Stack direction="row" gap={2} sx={{ mb: 3 }}>
        <Button
          variant="contained"
          onClick={handleSave}
          disabled={isSaving}
          size="large"
        >
          {isSaving ? t('success.saving') : t('boxes.saveAndReturn')}
        </Button>
        <Button
          variant="outlined"
          onClick={() => navigate('/app/boxes')}
          disabled={isSaving}
          size="large"
        >
          {t('boxes.cancel')}
        </Button>
      </Stack>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" sx={{ mb: 2 }}>
          {t('boxes.items')}
        </Typography>
        <ItemsTable items={items} onUpdateItem={handleUpdateItem} onDeleteItem={handleDeleteItem} />
        <ItemForm onAddItem={handleAddItem} boxUuid={box.uuid} isLoading={isSaving} />
      </Paper>

      <Snackbar
        open={snackbar !== null}
        autoHideDuration={4000}
        onClose={() => setSnackbar(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity={snackbar?.severity} onClose={() => setSnackbar(null)}>
          {snackbar?.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default BoxEditPage;

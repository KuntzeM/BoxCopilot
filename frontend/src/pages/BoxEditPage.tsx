import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Box, Button, Paper, Typography, Alert, CircularProgress, Stack, Snackbar, Collapse, useMediaQuery, useTheme } from '@mui/material';
import { ArrowBack, Save, ExpandMore, ExpandLess } from '@mui/icons-material';
import * as boxService from '../services/boxService';
import * as itemService from '../services/itemService';
import { Box as BoxType, Item, CreateItemPayload, UpdateItemPayload, UserPrincipal } from '../types/models';
import BoxForm, { BoxFormData } from '../components/BoxForm';
import EnhancedItemsTable from '../components/EnhancedItemsTable';
import { ItemForm } from '../components/ItemForm';
import { useTranslation } from '../hooks/useTranslation';
import axios from '../services/axiosConfig';

const BoxEditPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));

  const [box, setBox] = useState<BoxType | null>(null);
  const [items, setItems] = useState<Item[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [snackbar, setSnackbar] = useState<{ message: string; severity: 'success' | 'error' } | null>(null);
  const [expandBoxAttributes, setExpandBoxAttributes] = useState(false);
  const [user, setUser] = useState<UserPrincipal | null>(null);

  const boxId = id ? parseInt(id) : null;

  useEffect(() => {
    // Load current user
    const loadUser = async () => {
      try {
        const response = await axios.get('/api/me');
        setUser(response.data);
      } catch (error) {
        console.error('Failed to load user:', error);
      }
    };
    loadUser();
  }, []);

  const loadBoxAndItems = async () => {
    if (!boxId) return;
    
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

  useEffect(() => {
    if (!boxId) {
      setError(t('errors.invalidBoxId'));
      setIsLoading(false);
      return;
    }

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

  const handleAddItem = async () => {
    // Just reload the box and items - ItemForm now handles creation
    await loadBoxAndItems();
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

  const handleMoveItems = async (itemIds: number[], targetBoxUuid: string) => {
    try {
      await itemService.moveItems(itemIds, targetBoxUuid);
      // Reload items after moving
      await loadBoxAndItems();
    } catch (err) {
      console.error(err);
      throw err;
    }
  };

  const handleImageUpdated = async () => {
    // Reload items to get updated image URLs
    await loadBoxAndItems();
  };

  const handleError = (message: string) => {
    setSnackbar({ message, severity: 'error' });
  };

  const handleSuccess = (message: string) => {
    setSnackbar({ message, severity: 'success' });
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
      {/* Mobile: Show Username */}
      {isMobile && user && (
        <Box sx={{ mb: 2, p: 2, bgcolor: 'primary.light', borderRadius: 1 }}>
          <Typography variant="subtitle2" color="textSecondary">
            {t('auth.username')}
          </Typography>
          <Typography variant="h6" fontWeight={600}>
            {user.name || user.username}
          </Typography>
        </Box>
      )}

      <Button
        startIcon={<ArrowBack />}
        onClick={() => navigate('/app/boxes')}
        sx={{ mb: 3 }}
      >
        {t('boxes.back')}
      </Button>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Stack
          direction="row"
          spacing={1}
          alignItems="center"
          onClick={() => setExpandBoxAttributes(!expandBoxAttributes)}
          sx={{ cursor: 'pointer', userSelect: 'none', mb: expandBoxAttributes ? 2 : 0 }}
        >
          {expandBoxAttributes ? <ExpandLess /> : <ExpandMore />}
          <Typography variant="h5">
            {t('boxes.editBox', { number: box.id })}
          </Typography>
        </Stack>

        <Collapse in={expandBoxAttributes} timeout="auto" unmountOnExit>
          <BoxForm data={formData} onChange={setFormData} />
          
          <Button
            variant="contained"
            startIcon={<Save />}
            onClick={handleSave}
            disabled={isSaving}
            fullWidth
            sx={{ mt: 3 }}
          >
            {isSaving ? t('common.saving') : t('boxes.saveAndReturn')}
          </Button>
        </Collapse>
      </Paper>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
          <Typography variant="h6">
            {t('boxes.items')}
          </Typography>
          <ItemForm 
            onAddItem={handleAddItem} 
            boxUuid={box.uuid} 
            isLoading={isSaving}
            onSuccess={handleSuccess}
            onError={handleError}
          />
        </Box>
        <EnhancedItemsTable
          items={items}
          onUpdateItem={handleUpdateItem}
          onDeleteItem={handleDeleteItem}
          onMoveItems={handleMoveItems}
          onImageUpdated={handleImageUpdated}
          onError={handleError}
          onSuccess={handleSuccess}
        />
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

import React, { useState, useRef, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  Box,
  CircularProgress,
  Alert,
  IconButton,
  Card,
  Paper,
  Stack,
  Fab,
  Tooltip,
} from '@mui/material';
import { Add, PhotoCamera, FolderOpen, Close } from '@mui/icons-material';
import { useTranslation } from '../hooks/useTranslation';
import * as itemService from '../services/itemService';

interface ItemFormProps {
  boxId?: number;
  isLoading?: boolean;
  onSuccess?: (message: string) => void;
  onError?: (message: string) => void;
  onAddItem?: () => void;
  onUpdateItem?: () => void;
}

interface EditItem {
  id: number;
  name: string;
  imageUrl?: string;
}

let globalItemFormRef: { openEdit: (item: EditItem) => void } | null = null;

export const getItemFormRef = () => globalItemFormRef;

const FORM_STATE_KEY = 'itemFormState';

export const ItemForm: React.FC<ItemFormProps> = ({
  boxId,
  isLoading = false,
  onSuccess,
  onError,
  onAddItem,
  onUpdateItem,
}) => {
  const { t } = useTranslation();
  
  // Store boxId in local state to avoid closure issues during re-renders
  const [currentBoxId, setCurrentBoxId] = useState<number | undefined>(boxId);
  
  const [open, setOpen] = useState(false);
  const [isEditMode, setIsEditMode] = useState(false);
  const [editItemId, setEditItemId] = useState<number | null>(null);
  const [name, setName] = useState('');
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isSelectingFile, setIsSelectingFile] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const cameraInputRef = useRef<HTMLInputElement>(null);
  
  // Update local boxId when prop changes and is defined
  useEffect(() => {
    if (boxId && boxId !== currentBoxId) {
      setCurrentBoxId(boxId);
    }
  }, [boxId]);

  // Restore form state after page reload (e.g., when returning from camera on mobile)
  useEffect(() => {
    const savedState = sessionStorage.getItem(FORM_STATE_KEY);
    if (savedState) {
      try {
        const state = JSON.parse(savedState);
        if (state.boxId === boxId && state.timestamp && Date.now() - state.timestamp < 60000) {
          // Only restore if less than 60 seconds old and for same box
          setOpen(true);
          setIsEditMode(state.isEditMode || false);
          setEditItemId(state.editItemId || null);
          setName(state.name || '');
          setImagePreview(state.imagePreview || null);
          setIsSelectingFile(false);
          sessionStorage.removeItem(FORM_STATE_KEY);
        } else {
          sessionStorage.removeItem(FORM_STATE_KEY);
        }
      } catch (e) {
        console.error('Failed to restore form state:', e);
        sessionStorage.removeItem(FORM_STATE_KEY);
      }
    }
  }, [boxId]);

  // Register global ref
  useEffect(() => {
    globalItemFormRef = {
      openEdit: (item: EditItem) => {
        setOpen(true);
        setIsEditMode(true);
        setEditItemId(item.id);
        setName(item.name);
        if (item.imageUrl) {
          setImagePreview(item.imageUrl);
        }
        setError(null);
      },
    };
    return () => {
      globalItemFormRef = null;
    };
  }, []);

  const handleOpen = () => {
    setOpen(true);
    setIsEditMode(false);
    setEditItemId(null);
    setName('');
    setImageFile(null);
    setImagePreview(null);
    setError(null);
  };

  const handleClose = () => {
    // Prevent closing while file selection is in progress
    if (isSelectingFile) {
      return;
    }
    setOpen(false);
    setName('');
    setImageFile(null);
    setImagePreview(null);
    setError(null);
    setIsEditMode(false);
    setEditItemId(null);
    // Clear any saved state
    sessionStorage.removeItem(FORM_STATE_KEY);
  };

  const handleImageSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    // Prevent any default behavior and event propagation
    e.preventDefault();
    e.stopPropagation();
    
    const file = e.target.files?.[0];
    
    // Always reset the selecting state
    setIsSelectingFile(false);
    
    if (!file) {
      // Reset input value to allow selecting the same file again
      if (e.target) {
        e.target.value = '';
      }
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      setError(t('errors.imageTooLarge') || 'Image too large');
      if (e.target) {
        e.target.value = '';
      }
      return;
    }

    if (!file.type.startsWith('image/')) {
      setError(t('errors.invalidImageType') || 'Invalid image type');
      if (e.target) {
        e.target.value = '';
      }
      return;
    }

    setImageFile(file);
    const reader = new FileReader();
    reader.onloadend = () => {
      setImagePreview(reader.result as string);
    };
    reader.onerror = () => {
      setError(t('errors.imageReadFailed') || 'Failed to read image');
      if (e.target) {
        e.target.value = '';
      }
    };
    reader.readAsDataURL(file);
    
    // Reset input value to allow selecting the same file again
    if (e.target) {
      e.target.value = '';
    }
  };

  const handleRemoveImage = async () => {
    // If editing and image exists on server, delete it
    if (isEditMode && editItemId && imagePreview && !imagePreview.startsWith('data:')) {
      try {
        await itemService.deleteItemImage(editItemId);
        onSuccess?.(t('success.imageDeleted') || 'Image deleted successfully');
        onUpdateItem?.(); // Trigger refresh
      } catch (error) {
        onError?.(t('errors.imageDeleteFailed') || 'Failed to delete image');
        return;
      }
    }
    
    // Clear local state
    setImageFile(null);
    setImagePreview(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
    if (cameraInputRef.current) {
      cameraInputRef.current.value = '';
    }
  };

  const handleSubmit = async () => {
    if (!name.trim()) {
      setError(t('validation.itemNameRequired') || 'Item name is required');
      return;
    }

    setSubmitting(true);
    setError(null);

    try {
      if (isEditMode && editItemId) {
        // Edit mode
        await itemService.updateItem(editItemId, {
          name: name.trim(),
        });

        if (imageFile) {
          try {
            await itemService.uploadItemImage(editItemId, imageFile);
          } catch (imageError) {
            onSuccess?.(t('success.itemUpdatedButImageFailed') || 'Item updated but photo upload failed');
            setSubmitting(false);
            handleClose();
            onUpdateItem?.();
            return;
          }
        }

        sessionStorage.removeItem(FORM_STATE_KEY);
        onSuccess?.(t('success.itemUpdated') || 'Item updated successfully');
        handleClose();
        onUpdateItem?.();
      } else {
        // Create mode
        if (!currentBoxId) {
          setError('Box ID is required');
          setSubmitting(false);
          return;
        }

        const createdItem = await itemService.createItem({
          name: name.trim(),
          boxId: currentBoxId,
        });

        if (imageFile) {
          try {
            await itemService.uploadItemImage(createdItem.id, imageFile);
          } catch (imageError) {
            onSuccess?.(t('success.itemCreatedButImageFailed') || 'Item created but photo upload failed');
            setSubmitting(false);
            handleClose();
            onAddItem?.();
            return;
          }
        }

        sessionStorage.removeItem(FORM_STATE_KEY);
        onSuccess?.(t('success.itemCreated') || 'Item added successfully');
        handleClose();
        onAddItem?.();
      }
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : t('errors.itemCreationFailed') || 'Failed to create item';
      setError(errorMsg);
      onError?.(errorMsg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      {/* FAB Button - only for create mode */}
      <Fab
        color="primary"
        aria-label={t('items.addNew') || 'Add Item'}
        onClick={handleOpen}
        disabled={isLoading || submitting || !currentBoxId}
        size="large"
        sx={{
          position: 'fixed',
          bottom: 16,
          right: 16,
          zIndex: 50,
        }}
      >
        <Add />
      </Fab>

      {/* Dialog */}
      <Dialog 
        open={open} 
        onClose={handleClose}
        disableEscapeKeyDown={isSelectingFile}
        maxWidth="sm" 
        fullWidth
      >
        <DialogTitle>
          {isEditMode ? t('items.editItem') : t('items.addNew') || 'Add Item'}
        </DialogTitle>

        <DialogContent sx={{ pt: 2 }}>
          {error && (
            <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
              {error}
            </Alert>
          )}

          <Stack spacing={2}>
            {/* Item Name */}
            <TextField
              label={t('items.itemName') || 'Item Name'}
              value={name}
              onChange={(e) => setName(e.target.value)}
              fullWidth
              placeholder={t('items.itemNamePlaceholder') || 'e.g. Hammer'}
              disabled={submitting}
              autoFocus
              variant="outlined"
            />

            {/* Image Preview */}
            {imagePreview && (
              <Card
                sx={{
                  position: 'relative',
                  overflow: 'hidden',
                  borderRadius: 1,
                  maxHeight: '250px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  bgcolor: 'action.hover',
                }}
              >
                <img
                  src={imagePreview}
                  alt="Preview"
                  style={{
                    maxWidth: '100%',
                    maxHeight: '250px',
                    objectFit: 'contain',
                  }}
                />
                {!submitting && (
                  <IconButton
                    onClick={handleRemoveImage}
                    sx={{
                      position: 'absolute',
                      top: 8,
                      right: 8,
                      bgcolor: 'background.paper',
                      '&:hover': { bgcolor: 'action.hover' },
                    }}
                    size="small"
                  >
                    <Close fontSize="small" />
                  </IconButton>
                )}
              </Card>
            )}

            {/* Image Upload Icons */}
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              onChange={handleImageSelect}
              onCancel={() => setIsSelectingFile(false)}
              onBlur={() => {
                // Reset selecting state after a delay to ensure onChange fires first
                setTimeout(() => setIsSelectingFile(false), 300);
              }}
              style={{ display: 'none' }}
              disabled={submitting}
            />
            <input
              ref={cameraInputRef}
              type="file"
              accept="image/*"
              capture="environment"
              onChange={handleImageSelect}
              onCancel={() => setIsSelectingFile(false)}
              onBlur={() => {
                // Reset selecting state after a delay to ensure onChange fires first
                setTimeout(() => setIsSelectingFile(false), 300);
              }}
              style={{ display: 'none' }}
              disabled={submitting}
            />
            <Stack direction="row" spacing={1}>
              <Tooltip title={t('items.takePhoto') || 'Take Photo'}>
                <Box sx={{ flex: 1 }}>
                  <IconButton
                    color="primary"
                    onClick={(e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      setIsSelectingFile(true);
                      // Save state before opening camera (mobile may reload page)
                      sessionStorage.setItem(FORM_STATE_KEY, JSON.stringify({
                        boxId: currentBoxId,
                        name,
                        isEditMode,
                        editItemId,
                        imagePreview,
                        timestamp: Date.now(),
                      }));
                      cameraInputRef.current?.click();
                    }}
                    disabled={submitting || isSelectingFile}
                    size="large"
                    sx={{ width: '100%', justifyContent: 'center' }}
                  >
                    <PhotoCamera sx={{ fontSize: 40 }} />
                  </IconButton>
                </Box>
              </Tooltip>
              <Tooltip title={t('items.selectFromGallery') || 'Select Gallery'}>
                <Box sx={{ flex: 1 }}>
                  <IconButton
                    color="primary"
                    onClick={(e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      setIsSelectingFile(true);
                      fileInputRef.current?.click();
                    }}
                    disabled={submitting || isSelectingFile}
                    size="large"
                    sx={{ width: '100%', justifyContent: 'center' }}
                  >
                    <FolderOpen sx={{ fontSize: 40 }} />
                  </IconButton>
                </Box>
              </Tooltip>
            </Stack>
          </Stack>
        </DialogContent>

        <DialogActions sx={{ p: 2, gap: 1 }}>
          <Button onClick={handleClose} disabled={submitting}>
            {t('common.cancel') || 'Cancel'}
          </Button>
          <Button
            onClick={handleSubmit}
            variant="contained"
            disabled={submitting || !name.trim()}
          >
            {submitting ? (
              <>
                <CircularProgress size={20} sx={{ mr: 1 }} />
                {t('common.saving') || 'Saving...'}
              </>
            ) : isEditMode ? (
              t('common.save') || 'Save'
            ) : (
              t('common.add') || 'Add'
            )}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};
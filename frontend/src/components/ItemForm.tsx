import React, { useState, useRef } from 'react';
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
} from '@mui/material';
import { Add, PhotoCamera, Close } from '@mui/icons-material';
import { useTranslation } from '../hooks/useTranslation';
import * as itemService from '../services/itemService';

interface ItemFormProps {
  boxUuid: string;
  onAddItem?: () => void;
  isLoading?: boolean;
  onSuccess?: (message: string) => void;
  onError?: (message: string) => void;
}

export const ItemForm: React.FC<ItemFormProps> = ({
  boxUuid,
  onAddItem,
  isLoading = false,
  onSuccess,
  onError,
}) => {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleOpen = () => {
    setOpen(true);
    setError(null);
  };

  const handleClose = () => {
    setOpen(false);
    setName('');
    setImageFile(null);
    setImagePreview(null);
    setError(null);
  };

  const handleImageSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > 5 * 1024 * 1024) {
      setError(t('errors.imageTooLarge') || 'Image too large');
      return;
    }

    if (!file.type.startsWith('image/')) {
      setError(t('errors.invalidImageType') || 'Invalid image type');
      return;
    }

    setImageFile(file);
    const reader = new FileReader();
    reader.onloadend = () => {
      setImagePreview(reader.result as string);
    };
    reader.readAsDataURL(file);
  };

  const handleRemoveImage = () => {
    setImageFile(null);
    setImagePreview(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
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
      // Create item first
      const createdItem = await itemService.createItem({
        name: name.trim(),
        boxUuid,
      });

      // Upload image if provided
      if (imageFile) {
        try {
          await itemService.uploadItemImage(createdItem.id, imageFile);
        } catch (imageError) {
          // Item created but image upload failed - show warning
          onSuccess?.(t('success.itemCreatedButImageFailed') || 'Item created but photo upload failed');
          setSubmitting(false);
          handleClose();
          onAddItem?.();
          return;
        }
      }

      onSuccess?.(t('success.itemCreated') || 'Item added successfully');
      handleClose();
      onAddItem?.();
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
      {/* FAB Button */}
      <Fab
        color="primary"
        aria-label={t('items.addNew') || 'Add Item'}
        onClick={handleOpen}
        disabled={isLoading || submitting}
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
        maxWidth="sm" 
        fullWidth
        container={() => document.getElementById('modal-root')}
      >
        <DialogTitle>
          {t('items.addNew') || 'Add Item'}
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

            {/* Image Upload Button */}
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              onChange={handleImageSelect}
              style={{ display: 'none' }}
              disabled={submitting}
            />
            <Button
              variant="outlined"
              startIcon={<PhotoCamera />}
              onClick={() => fileInputRef.current?.click()}
              disabled={submitting}
              fullWidth
              sx={{
                textTransform: 'none',
              }}
            >
              {imagePreview
                ? t('items.changePhoto') || 'Change Photo'
                : t('items.addPhoto') || 'Add Photo (Optional)'}
            </Button>
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
            ) : (
              t('common.add') || 'Add'
            )}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};
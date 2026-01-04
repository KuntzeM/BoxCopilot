import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  Box,
  IconButton,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
  Alert,
} from '@mui/material';
import { PhotoCamera, CameraAlt, Upload, Delete } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { Item } from '../types';

interface ItemFormProps {
  open: boolean;
  onClose: () => void;
  onSubmit: (item: Omit<Item, 'id' | 'box_id'>, image?: File) => Promise<void>;
  initialData?: Item;
  mode: 'add' | 'edit';
}

const ItemForm: React.FC<ItemFormProps> = ({
  open,
  onClose,
  onSubmit,
  initialData,
  mode,
}) => {
  const { t } = useTranslation();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [quantity, setQuantity] = useState(1);
  const [selectedImage, setSelectedImage] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string>('');
  const [showPhotoOptions, setShowPhotoOptions] = useState(false);
  const [photoAnchorEl, setPhotoAnchorEl] = useState<null | HTMLElement>(null);

  useEffect(() => {
    if (initialData) {
      setName(initialData.name);
      setDescription(initialData.description || '');
      setQuantity(initialData.quantity);
      if (initialData.image_url) {
        setImagePreview(initialData.image_url);
      }
    } else {
      resetForm();
    }
  }, [initialData, open]);

  const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      setSelectedImage(file);
      const preview = URL.createObjectURL(file);
      // Validate that the URL is a blob URL to prevent XSS
      if (preview.startsWith('blob:')) {
        setImagePreview(preview);
      }
      setShowPhotoOptions(false);
    }
  };

  const handleCameraCapture = (event: React.ChangeEvent<HTMLInputElement>) => {
    handleFileSelect(event);
  };

  const removePhoto = () => {
    setSelectedImage(null);
    setImagePreview('');
    if (initialData) {
      initialData.image_url = '';
    }
  };

  const resetForm = () => {
    setName('');
    setDescription('');
    setQuantity(1);
    setSelectedImage(null);
    setImagePreview('');
    setError('');
  };

  const handleSubmit = async () => {
    if (!name.trim()) {
      setError(t('items.nameRequired'));
      return;
    }

    setIsSubmitting(true);
    setError('');

    try {
      await onSubmit(
        {
          name: name.trim(),
          description: description.trim(),
          quantity,
          image_url: initialData?.image_url || '',
        },
        selectedImage || undefined
      );
      resetForm();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : t('items.addError'));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleClose = () => {
    if (!isSubmitting) {
      resetForm();
      onClose();
    }
  };

  const handlePhotoMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setPhotoAnchorEl(event.currentTarget);
    setShowPhotoOptions(true);
  };

  const handlePhotoMenuClose = () => {
    setPhotoAnchorEl(null);
    setShowPhotoOptions(false);
  };

  return (
    <Dialog 
      open={open} 
      onClose={handleClose} 
      maxWidth="sm" 
      fullWidth
      PaperProps={{
        sx: {
          borderRadius: 2,
          mx: 2,
        }
      }}
    >
      <DialogTitle>
        {mode === 'add' ? t('items.addItem') : t('items.editItem')}
      </DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
          <TextField
            autoFocus
            label={t('items.name')}
            value={name}
            onChange={(e) => setName(e.target.value)}
            fullWidth
            required
            disabled={isSubmitting}
            error={!name.trim() && error !== ''}
          />

          <TextField
            label={t('items.description')}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            fullWidth
            multiline
            rows={3}
            disabled={isSubmitting}
          />

          <TextField
            label={t('items.quantity')}
            type="number"
            value={quantity}
            onChange={(e) => setQuantity(Math.max(1, parseInt(e.target.value) || 1))}
            fullWidth
            disabled={isSubmitting}
            inputProps={{ min: 1 }}
          />

          {/* Photo options button */}
          <Button
            variant="outlined"
            startIcon={<PhotoCamera />}
            onClick={handlePhotoMenuOpen}
            disabled={isSubmitting}
            fullWidth
          >
            {imagePreview ? t('items.changePhoto') : t('items.addPhoto')}
          </Button>

          {/* Photo options menu */}
          <Menu
            anchorEl={photoAnchorEl}
            open={showPhotoOptions}
            onClose={handlePhotoMenuClose}
          >
            <MenuItem component="label">
              <ListItemIcon>
                <CameraAlt />
              </ListItemIcon>
              <ListItemText>{t('items.takePhoto')}</ListItemText>
              <input
                type="file"
                hidden
                accept="image/*"
                capture="environment"
                onChange={handleCameraCapture}
              />
            </MenuItem>
            <MenuItem component="label">
              <ListItemIcon>
                <Upload />
              </ListItemIcon>
              <ListItemText>{t('items.uploadPhoto')}</ListItemText>
              <input
                type="file"
                hidden
                accept="image/*"
                onChange={handleFileSelect}
              />
            </MenuItem>
          </Menu>

          {/* Image preview */}
          {imagePreview && imagePreview.startsWith('blob:') && (
            <Box sx={{ position: 'relative', textAlign: 'center', mt: 1 }}>
              <img
                src={imagePreview}
                alt="Preview"
                style={{ 
                  maxWidth: '100%', 
                  maxHeight: '200px', 
                  borderRadius: 8,
                  objectFit: 'contain'
                }}
              />
              <IconButton
                onClick={removePhoto}
                disabled={isSubmitting}
                sx={{
                  position: 'absolute',
                  top: 0,
                  right: 0,
                  bgcolor: 'background.paper',
                  '&:hover': { bgcolor: 'error.main', color: 'white' },
                }}
                aria-label={t('items.removePhoto')}
              >
                <Delete />
              </IconButton>
            </Box>
          )}
        </Box>
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={handleClose} disabled={isSubmitting}>
          {t('common.cancel')}
        </Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          disabled={isSubmitting || !name.trim()}
        >
          {isSubmitting
            ? t('common.saving')
            : mode === 'add'
            ? t('items.add')
            : t('items.save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ItemForm;
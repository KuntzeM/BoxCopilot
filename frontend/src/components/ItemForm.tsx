import React, { useState, useRef, useEffect } from 'react';
import {
  Box,
  TextField,
  Button,
  Card,
  CardContent,
  Typography,
  Stack,
  IconButton,
  Collapse,
  LinearProgress,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@mui/material';
import {
  PhotoCamera,
  Upload,
  Close,
  Delete,
} from '@mui/icons-material';
import { CreateItemPayload } from '../types/models';
import { useTranslation } from '../hooks/useTranslation';
import { createItem, uploadItemImage, deleteItem } from '../services/itemService';

interface ItemFormProps {
  onAddItem: (data: CreateItemPayload) => Promise<void>;
  boxUuid: string;
  isLoading?: boolean;
  onSuccess?: (message: string) => void;
  onError?: (message: string) => void;
}

const ItemForm: React.FC<ItemFormProps> = ({ onAddItem, boxUuid, isLoading = false, onSuccess, onError }) => {
  const { t } = useTranslation();
  const [formData, setFormData] = useState<CreateItemPayload>({
    boxUuid,
    name: '',
  });

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [selectedImage, setSelectedImage] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [showPhotoOptions, setShowPhotoOptions] = useState(false);
  const [isCameraOpen, setIsCameraOpen] = useState(false);
  const [stream, setStream] = useState<MediaStream | null>(null);
  const [failureDialogOpen, setFailureDialogOpen] = useState(false);
  const [failedItemId, setFailedItemId] = useState<number | null>(null);

  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const cameraInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    return () => {
      if (stream) {
        stream.getTracks().forEach(track => track.stop());
      }
      if (imagePreview) {
        URL.revokeObjectURL(imagePreview);
      }
    };
  }, [stream, imagePreview]);

  const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      setSelectedImage(file);
      const preview = URL.createObjectURL(file);
      setImagePreview(preview);
      setShowPhotoOptions(false);
    }
  };

  const startCamera = async () => {
    try {
      const mediaStream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' },
      });
      setStream(mediaStream);
      setIsCameraOpen(true);
      
      setTimeout(() => {
        if (videoRef.current) {
          videoRef.current.srcObject = mediaStream;
          videoRef.current.play().catch(err => {
            console.error('Error playing video:', err);
          });
        }
      }, 100);
    } catch (error) {
      console.error('Camera access error:', error);
    }
  };

  const stopCamera = () => {
    if (stream) {
      stream.getTracks().forEach(track => track.stop());
      setStream(null);
    }
    setIsCameraOpen(false);
  };

  const capturePhoto = () => {
    if (!videoRef.current || !canvasRef.current) return;

    const video = videoRef.current;
    const canvas = canvasRef.current;
    const context = canvas.getContext('2d');

    if (!context) return;

    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    context.drawImage(video, 0, 0);

    canvas.toBlob((blob) => {
      if (!blob) return;

      const file = new File([blob], 'photo.jpg', { type: 'image/jpeg' });
      setSelectedImage(file);
      const preview = URL.createObjectURL(file);
      setImagePreview(preview);
      stopCamera();
      setShowPhotoOptions(false);
    }, 'image/jpeg', 0.9);
  };

  const removePhoto = () => {
    if (imagePreview) {
      URL.revokeObjectURL(imagePreview);
    }
    setSelectedImage(null);
    setImagePreview(null);
  };

  const handleKeepItem = async () => {
    setFailureDialogOpen(false);
    setFailedItemId(null);
    setFormData({ boxUuid, name: '' });
    removePhoto();
    setUploadProgress(0);
    // Trigger reload to show the item without photo
    await onAddItem(formData);
    onSuccess?.(t('success.itemAdded'));
  };

  const handleDeleteFailedItem = async () => {
    if (failedItemId) {
      try {
        await deleteItem(failedItemId);
      } catch (error) {
        console.error('Error deleting failed item:', error);
        onError?.(t('errors.itemDeleteFailed'));
      }
    }
    setFailureDialogOpen(false);
    setFailedItemId(null);
    setFormData({ boxUuid, name: '' });
    removePhoto();
    setUploadProgress(0);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.name.trim()) return;

    setIsSubmitting(true);
    setUploadProgress(0);

    try {
      // Step 1: Create item
      const newItem = await createItem(formData);
      setUploadProgress(50);

      // Step 2: Upload photo if selected
      if (selectedImage) {
        try {
          await uploadItemImage(newItem.id, selectedImage);
          setUploadProgress(100);
        } catch (uploadError) {
          console.error('Photo upload failed:', uploadError);
          // Show failure dialog
          setFailedItemId(newItem.id);
          setFailureDialogOpen(true);
          setIsSubmitting(false);
          return;
        }
      }

      // Success: trigger reload to show new item and reset form
      await onAddItem(formData);
      onSuccess?.(t('success.itemAdded'));
      setFormData({ boxUuid, name: '' });
      removePhoto();
      setUploadProgress(0);
    } catch (err) {
      console.error('Error creating item:', err);
      onError?.(t('errors.itemAddFailed'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Card sx={{ mt: 3 }}>
      <CardContent>
        <Typography variant="h6" sx={{ mb: 2 }}>
          {t('items.addNew')}
        </Typography>
        <Box component="form" onSubmit={handleSubmit} sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField
            label={t('items.name')}
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            fullWidth
            required
            disabled={isSubmitting || isLoading}
          />

          {/* Photo upload button */}
          <Button
            variant="outlined"
            size="large"
            fullWidth
            onClick={() => setShowPhotoOptions(!showPhotoOptions)}
            disabled={isSubmitting || isLoading}
            sx={{ minHeight: 56 }}
          >
            {t('items.addPhoto')} {selectedImage ? '✓' : ''}
          </Button>

          {/* Photo options (collapsed) */}
          <Collapse in={showPhotoOptions}>
            <Stack direction="row" spacing={1} sx={{ mb: 1 }}>
              <Button
                variant="outlined"
                startIcon={<PhotoCamera />}
                onClick={() => cameraInputRef.current?.click()}
                disabled={isSubmitting || isLoading}
                fullWidth
                sx={{ minHeight: 56 }}
              >
                {t('items.takePhoto')}
              </Button>
              <Button
                variant="outlined"
                startIcon={<Upload />}
                onClick={() => fileInputRef.current?.click()}
                disabled={isSubmitting || isLoading}
                fullWidth
                sx={{ minHeight: 56 }}
              >
                {t('items.uploadPhoto')}
              </Button>
            </Stack>
          </Collapse>

          {/* Image preview */}
          {imagePreview && (
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
              >
                <Delete />
              </IconButton>
              <Typography variant="caption" display="block" color="text.secondary" sx={{ mt: 1 }}>
                {t('items.removePhoto')}
              </Typography>
            </Box>
          )}

          {/* Upload progress */}
          {isSubmitting && uploadProgress > 0 && (
            <Box>
              <LinearProgress variant="determinate" value={uploadProgress} />
              <Typography variant="caption" color="text.secondary" textAlign="center" display="block" sx={{ mt: 0.5 }}>
                {t('items.uploadProgress')} {uploadProgress}%
              </Typography>
            </Box>
          )}

          {/* Submit button */}
          <Button
            type="submit"
            variant="contained"
            size="large"
            fullWidth
            sx={{ minHeight: 56 }}
            disabled={!formData.name.trim() || isSubmitting || isLoading}
          >
            {isSubmitting ? t('items.uploadProgress') : t('items.add')}
          </Button>
        </Box>

        {/* Hidden file inputs */}
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          style={{ display: 'none' }}
          onChange={handleFileSelect}
        />
        <input
          ref={cameraInputRef}
          type="file"
          accept="image/*"
          capture="environment"
          style={{ display: 'none' }}
          onChange={handleFileSelect}
        />

        {/* Camera Dialog */}
        <Dialog open={isCameraOpen} onClose={stopCamera} maxWidth="md" fullWidth>
          <DialogTitle>
            {t('items.takePhoto')}
            <IconButton
              onClick={stopCamera}
              sx={{ position: 'absolute', right: 8, top: 8 }}
            >
              <Close />
            </IconButton>
          </DialogTitle>
          <DialogContent>
            <Box sx={{ position: 'relative', width: '100%', paddingTop: '75%' }}>
              <video
                ref={videoRef}
                autoPlay
                playsInline
                muted
                onLoadedMetadata={(e) => {
                  const video = e.target as HTMLVideoElement;
                  video.play().catch(err => console.error('Play error:', err));
                }}
                style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  width: '100%',
                  height: '100%',
                  objectFit: 'cover',
                  backgroundColor: '#000',
                }}
              />
            </Box>
            <canvas ref={canvasRef} style={{ display: 'none' }} />
          </DialogContent>
          <DialogActions>
            <Button onClick={stopCamera}>{t('items.cancel')}</Button>
            <Button variant="contained" onClick={capturePhoto}>
              {t('items.takePhoto')}
            </Button>
          </DialogActions>
        </Dialog>

        {/* Failure Dialog */}
        <Dialog 
          open={failureDialogOpen} 
          onClose={handleKeepItem}
          disableEscapeKeyDown={false}
        >
          <DialogTitle>{t('items.photoUploadFailedTitle')}</DialogTitle>
          <DialogContent>
            <Typography>{t('items.photoUploadFailedMessage')}</Typography>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleDeleteFailedItem} color="error">
              {t('items.deleteItemQuestion')}
            </Button>
            <Button variant="contained" onClick={handleKeepItem}>
              {t('items.keepItem')}
            </Button>
          </DialogActions>
        </Dialog>
      </CardContent>
    </Card>
  );
};

export default ItemForm;

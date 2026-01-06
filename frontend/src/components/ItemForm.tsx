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
  Stack,
  Fab,
  Tooltip,
  Slider,
  Typography,
} from '@mui/material';
import { 
  Add, 
  PhotoCamera, 
  FolderOpen, 
  Close, 
  FlashOn, 
  FlashOff, 
  FlashAuto,
  CameraAlt,
  CheckCircle,
  Replay,
  ZoomIn,
  Cameraswitch,
} from '@mui/icons-material';
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

type FlashMode = 'off' | 'on' | 'auto';
type CameraMode = 'none' | 'preview' | 'captured';

export const ItemForm: React.FC<ItemFormProps> = ({
  boxId,
  isLoading = false,
  onSuccess,
  onError,
  onAddItem,
  onUpdateItem,
}) => {
  const { t } = useTranslation();
  
  const [currentBoxId, setCurrentBoxId] = useState<number | undefined>(boxId);
  const [open, setOpen] = useState(false);
  const [isEditMode, setIsEditMode] = useState(false);
  const [editItemId, setEditItemId] = useState<number | null>(null);
  const [name, setName] = useState('');
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  // Camera states
  const [cameraMode, setCameraMode] = useState<CameraMode>('none');
  const [stream, setStream] = useState<MediaStream | null>(null);
  const [flashMode, setFlashMode] = useState<FlashMode>('off');
  const [zoom, setZoom] = useState(1);
  const [zoomRange, setZoomRange] = useState({ min: 1, max: 1 });
  const [facingMode, setFacingMode] = useState<'user' | 'environment'>('environment');
  const [capturedImage, setCapturedImage] = useState<string | null>(null);
  const [hasFlash, setHasFlash] = useState(false);
  
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const lastTouchDistanceRef = useRef<number | null>(null);

  useEffect(() => {
    if (boxId && boxId !== currentBoxId) {
      setCurrentBoxId(boxId);
    }
  }, [boxId, currentBoxId]);

  // Cleanup camera stream on unmount or when camera closes
  useEffect(() => {
    return () => {
      if (stream) {
        stream.getTracks().forEach(track => track.stop());
      }
    };
  }, [stream]);

  // Apply zoom to video track
  useEffect(() => {
    if (stream && videoRef.current) {
      const videoTrack = stream.getVideoTracks()[0];
      const capabilities = videoTrack.getCapabilities() as any;
      
      if (capabilities.zoom) {
        videoTrack.applyConstraints({
          advanced: [{ zoom } as any]
        }).catch(err => console.error('Zoom error:', err));
      }
    }
  }, [zoom, stream]);

  // Attach stream to video element when camera mode changes to preview
  useEffect(() => {
    console.log('useEffect triggered:', { cameraMode, streamExists: !!stream, videoRefExists: !!videoRef.current });
    
    if (cameraMode !== 'preview' || !stream) {
      console.log('✗ Conditions not met early return:', {
        'cameraMode === preview': cameraMode === 'preview',
        'stream exists': !!stream
      });
      return;
    }

    // Wait for video element to be in DOM
    let attempts = 0;
    const checkAndAttach = () => {
      attempts++;
      console.log(`Attempt ${attempts}: videoRef.current exists:`, !!videoRef.current);
      
      if (videoRef.current) {
        console.log('✓ Video element found, attaching stream');
        const video = videoRef.current;
        video.srcObject = stream;
        console.log('Stream srcObject set');
        
        // Wait for stream to be ready
        const attachStream = async () => {
          await new Promise(resolve => setTimeout(resolve, 300));
          
          console.log('Video element state:', {
            width: video.videoWidth,
            height: video.videoHeight,
            readyState: video.readyState,
            networkState: video.networkState
          });
          
          console.log('Attempting to play video...');
          try {
            await video.play();
            console.log('Video playing successfully');
          } catch (playError) {
            console.error('Video play error:', playError);
            await new Promise(resolve => setTimeout(resolve, 100));
            try {
              await video.play();
              console.log('Video playing successfully on retry');
            } catch (retryError) {
              console.error('Retry play failed:', retryError);
            }
          }
        };
        
        attachStream();
      } else if (attempts < 10) {
        // Video element not yet in DOM, retry after 50ms
        setTimeout(checkAndAttach, 50);
      } else {
        console.error('Failed to find video element after 10 attempts');
      }
    };

    checkAndAttach();
  }, [cameraMode, stream]);

  // Apply flash/torch mode
  useEffect(() => {
    if (stream && flashMode !== 'auto') {
      const videoTrack = stream.getVideoTracks()[0];
      const capabilities = videoTrack.getCapabilities() as any;
      
      if (capabilities.torch) {
        videoTrack.applyConstraints({
          advanced: [{ torch: flashMode === 'on' } as any]
        }).catch(err => console.error('Flash error:', err));
      }
    }
  }, [flashMode, stream]);

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
    stopCamera();
    setOpen(false);
    setName('');
    setImageFile(null);
    setImagePreview(null);
    setCapturedImage(null);
    setError(null);
    setIsEditMode(false);
    setEditItemId(null);
    setCameraMode('none');
  };

  const startCamera = async (mode?: 'user' | 'environment') => {
    try {
      const cameraMode = mode || facingMode;
      console.log('Starting camera with facingMode:', cameraMode);
      
      const constraints: MediaStreamConstraints = {
        video: {
          facingMode: { ideal: cameraMode },
          width: { ideal: 1280 },
          height: { ideal: 720 },
        },
        audio: false,
      };

      console.log('Requesting camera access with constraints:', constraints);
      let mediaStream: MediaStream;
      
      try {
        mediaStream = await navigator.mediaDevices.getUserMedia(constraints);
      } catch (primaryError) {
        console.warn('Primary camera request failed, trying without facingMode constraint:', primaryError);
        // Fallback: try without facingMode constraint
        const fallbackConstraints: MediaStreamConstraints = {
          video: {
            width: { ideal: 1280 },
            height: { ideal: 720 },
          },
          audio: false,
        };
        mediaStream = await navigator.mediaDevices.getUserMedia(fallbackConstraints);
      }
      
      console.log('Camera access granted, stream:', mediaStream);
      
      // Get the actual facing mode from the track
      const videoTrack = mediaStream.getVideoTracks()[0];
      const settings = videoTrack.getSettings() as any;
      console.log('Actual camera settings:', settings);
      
      const capabilities = videoTrack.getCapabilities() as any;

      // Check for flash/torch
      if (capabilities.torch) {
        setHasFlash(true);
      }

      // Check for zoom
      if (capabilities.zoom) {
        setZoomRange({
          min: capabilities.zoom.min || 1,
          max: capabilities.zoom.max || 1,
        });
        setZoom(capabilities.zoom.min || 1);
      }

      // Set state with the camera mode
      setFacingMode(cameraMode);
      setStream(mediaStream);
      setCameraMode('preview');
    } catch (err) {
      console.error('Camera error:', err);
      setError(t('errors.cameraAccessFailed') || 'Camera access failed');
      setCameraMode('none');
    }
  };

  const stopCamera = () => {
    if (stream) {
      stream.getTracks().forEach(track => track.stop());
      setStream(null);
    }
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
    setCameraMode('none');
    setCapturedImage(null);
    setZoom(1);
    setFlashMode('off');
  };

  const switchCamera = async () => {
    console.log('Switching camera from:', facingMode);
    
    // Stop current camera
    if (stream) {
      stream.getTracks().forEach(track => track.stop());
      setStream(null);
    }
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
    
    // Switch to the other camera
    const newMode = facingMode === 'user' ? 'environment' : 'user';
    console.log('Switching camera to:', newMode);
    
    // Wait for cleanup to complete
    await new Promise(resolve => setTimeout(resolve, 200));
    
    // Start with new mode
    setFacingMode(newMode);
    await startCamera(newMode);
  };

  // Touch-based zoom handling (pinch-zoom)
  const handleTouchStart = (e: React.TouchEvent<HTMLVideoElement>) => {
    if (e.touches.length === 2) {
      const touch1 = e.touches[0];
      const touch2 = e.touches[1];
      const distance = Math.hypot(
        touch1.clientX - touch2.clientX,
        touch1.clientY - touch2.clientY
      );
      lastTouchDistanceRef.current = distance;
    }
  };

  const handleTouchMove = (e: React.TouchEvent<HTMLVideoElement>) => {
    if (e.touches.length === 2 && lastTouchDistanceRef.current !== null) {
      const touch1 = e.touches[0];
      const touch2 = e.touches[1];
      const newDistance = Math.hypot(
        touch1.clientX - touch2.clientX,
        touch1.clientY - touch2.clientY
      );

      const ratio = newDistance / lastTouchDistanceRef.current;
      const newZoom = Math.max(
        zoomRange.min,
        Math.min(zoomRange.max, zoom * ratio)
      );
      setZoom(newZoom);
      lastTouchDistanceRef.current = newDistance;
    }
  };

  const handleTouchEnd = () => {
    lastTouchDistanceRef.current = null;
  };

  const capturePhoto = () => {
    if (!videoRef.current || !canvasRef.current) {
      console.error('Video or canvas ref missing');
      setError(t('errors.captureImageFailed') || 'Failed to capture image');
      return;
    }

    const video = videoRef.current;
    const canvas = canvasRef.current;

    // Check if video has valid dimensions
    if (video.videoWidth === 0 || video.videoHeight === 0) {
      console.error('Video dimensions are 0:', { width: video.videoWidth, height: video.videoHeight });
      setError(t('errors.captureImageFailed') || 'Camera not ready');
      return;
    }

    console.log('Capturing photo:', { width: video.videoWidth, height: video.videoHeight });

    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;

    const ctx = canvas.getContext('2d');
    if (!ctx) {
      console.error('Could not get canvas context');
      setError(t('errors.captureImageFailed') || 'Failed to capture image');
      return;
    }

    // Draw current video frame to canvas
    ctx.drawImage(video, 0, 0);

    // Convert to blob
    canvas.toBlob((blob) => {
      if (!blob) {
        setError(t('errors.captureImageFailed') || 'Failed to capture image');
        return;
      }

      const imageUrl = canvas.toDataURL('image/jpeg', 0.9);
      setCapturedImage(imageUrl);
      setCameraMode('captured');

      // Create file from blob
      const file = new File([blob], 'photo.jpg', { type: 'image/jpeg' });
      setImageFile(file);
      setImagePreview(imageUrl);

      // Stop camera stream
      if (stream) {
        stream.getTracks().forEach(track => track.stop());
        setStream(null);
      }
    }, 'image/jpeg', 0.9);
  };

  const retakePhoto = () => {
    setCapturedImage(null);
    setImageFile(null);
    setImagePreview(null);
    setCameraMode('none');
    startCamera();
  };

  const acceptPhoto = () => {
    setCameraMode('none');
    // Image is already in imageFile and imagePreview
  };

  const cycleFlash = () => {
    setFlashMode(prev => {
      if (prev === 'off') return 'on';
      if (prev === 'on') return 'auto';
      return 'off';
    });
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
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

  const handleRemoveImage = async () => {
    if (isEditMode && editItemId && imagePreview && !imagePreview.startsWith('data:')) {
      try {
        await itemService.deleteItemImage(editItemId);
        onSuccess?.(t('success.imageDeleted') || 'Image deleted successfully');
        onUpdateItem?.();
      } catch (error) {
        onError?.(t('errors.imageDeleteFailed') || 'Failed to delete image');
        return;
      }
    }
    
    setImageFile(null);
    setImagePreview(null);
    setCapturedImage(null);
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

      {/* Main Dialog */}
      <Dialog 
        open={open && cameraMode === 'none'} 
        onClose={handleClose}
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

            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              onChange={handleFileSelect}
              style={{ display: 'none' }}
              disabled={submitting}
            />
            
            <Stack direction="row" spacing={1}>
              <Tooltip title={t('items.takePhoto') || 'Take Photo'}>
                <Box sx={{ flex: 1 }}>
                  <IconButton
                    color="primary"
                    onClick={startCamera}
                    disabled={submitting}
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
                    onClick={() => fileInputRef.current?.click()}
                    disabled={submitting}
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

      {/* Camera Dialog */}
      <Dialog
        open={open && cameraMode !== 'none'}
        onClose={() => {
          stopCamera();
          setCameraMode('none');
        }}
        maxWidth="md"
        fullWidth
        fullScreen
        sx={{
          '& .MuiDialog-paper': {
            bgcolor: 'black',
            m: 0,
          }
        }}
      >
        <Box sx={{ position: 'relative', width: '100%', height: '100vh', bgcolor: 'black' }}>
          {/* Camera Preview */}
          {cameraMode === 'preview' && (
            <>
              <video
                ref={videoRef}
                autoPlay
                playsInline
                muted
                onTouchStart={handleTouchStart}
                onTouchMove={handleTouchMove}
                onTouchEnd={handleTouchEnd}
                style={{
                  width: '100%',
                  height: '100%',
                  objectFit: 'cover',
                  backgroundColor: 'black',
                  touchAction: 'manipulation',
                }}
              />
              
              {/* Top Controls */}
              <Box sx={{ 
                position: 'absolute', 
                top: 0, 
                left: 0, 
                right: 0, 
                p: 2,
                background: 'linear-gradient(to bottom, rgba(0,0,0,0.6), transparent)',
              }}>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <IconButton onClick={() => { stopCamera(); setCameraMode('none'); }} sx={{ color: 'white' }}>
                    <Close />
                  </IconButton>
                  
                  {hasFlash && (
                    <IconButton onClick={cycleFlash} sx={{ color: 'white' }}>
                      {flashMode === 'off' && <FlashOff />}
                      {flashMode === 'on' && <FlashOn />}
                      {flashMode === 'auto' && <FlashAuto />}
                    </IconButton>
                  )}
                </Stack>
              </Box>

              {/* Zoom Control */}
              {zoomRange.max > zoomRange.min && (
                <Box sx={{ 
                  position: 'absolute', 
                  bottom: 120, 
                  left: '50%',
                  transform: 'translateX(-50%)',
                  width: '80%',
                  maxWidth: 300,
                }}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <ZoomIn sx={{ color: 'white' }} />
                    <Slider
                      value={zoom}
                      onChange={(_, value) => setZoom(value as number)}
                      min={zoomRange.min}
                      max={zoomRange.max}
                      step={0.1}
                      sx={{
                        color: 'white',
                        '& .MuiSlider-thumb': {
                          bgcolor: 'white',
                        }
                      }}
                    />
                    <Typography sx={{ color: 'white', minWidth: 40 }}>
                      {zoom.toFixed(1)}x
                    </Typography>
                  </Stack>
                </Box>
              )}

              {/* Bottom Controls */}
              <Box sx={{ 
                position: 'absolute', 
                bottom: 0, 
                left: 0, 
                right: 0, 
                p: 3,
                background: 'linear-gradient(to top, rgba(0,0,0,0.6), transparent)',
              }}>
                <Stack direction="row" justifyContent="space-around" alignItems="center">
                  <IconButton onClick={switchCamera} sx={{ color: 'white' }}>
                    <Cameraswitch sx={{ fontSize: 32 }} />
                  </IconButton>
                  
                  <IconButton 
                    onClick={capturePhoto}
                    sx={{ 
                      width: 70, 
                      height: 70,
                      border: '4px solid white',
                      bgcolor: 'rgba(255,255,255,0.3)',
                      '&:hover': {
                        bgcolor: 'rgba(255,255,255,0.5)',
                      }
                    }}
                  >
                    <CameraAlt sx={{ fontSize: 36, color: 'white' }} />
                  </IconButton>
                  
                  <Box sx={{ width: 48 }} /> {/* Spacer for alignment */}
                </Stack>
              </Box>
            </>
          )}

          {/* Captured Image Preview */}
          {cameraMode === 'captured' && capturedImage && (
            <>
              <img
                src={capturedImage}
                alt="Captured"
                style={{
                  width: '100%',
                  height: '100%',
                  objectFit: 'contain',
                }}
              />
              
              <Box sx={{ 
                position: 'absolute', 
                bottom: 0, 
                left: 0, 
                right: 0, 
                p: 3,
                background: 'linear-gradient(to top, rgba(0,0,0,0.8), transparent)',
              }}>
                <Stack direction="row" justifyContent="space-around" alignItems="center">
                  <Button
                    startIcon={<Replay />}
                    onClick={retakePhoto}
                    variant="outlined"
                    sx={{ 
                      color: 'white',
                      borderColor: 'white',
                      '&:hover': {
                        borderColor: 'white',
                        bgcolor: 'rgba(255,255,255,0.1)',
                      }
                    }}
                  >
                    {t('common.retake') || 'Retake'}
                  </Button>
                  
                  <Button
                    startIcon={<CheckCircle />}
                    onClick={acceptPhoto}
                    variant="contained"
                    sx={{ 
                      bgcolor: 'white',
                      color: 'black',
                      '&:hover': {
                        bgcolor: 'rgba(255,255,255,0.9)',
                      }
                    }}
                  >
                    {t('common.accept') || 'Use Photo'}
                  </Button>
                </Stack>
              </Box>
            </>
          )}

          <canvas ref={canvasRef} style={{ display: 'none' }} />
        </Box>
      </Dialog>
    </>
  );
};

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
  useMediaQuery,
  useTheme,
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
import { extractApiError } from '../services/apiError';

interface ItemFormProps {
  boxId?: number;
  isLoading?: boolean;
  onSuccess?: (message: string) => void;
  onError?: (message: string) => void;
  onAddItem?: () => void;
  onUpdateItem?: () => void;
  hideFab?: boolean;
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
  hideFab = false,
}) => {
  const { t } = useTranslation();
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'));

  const [currentBoxId, setCurrentBoxId] = useState<number | undefined>(boxId);
  const [open, setOpen] = useState(false);
  const [isEditMode, setIsEditMode] = useState(false);
  const [editItemId, setEditItemId] = useState<number | null>(null);
  const [name, setName] = useState('');
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Removing the server photo is deferred until save (nothing is deleted while the dialog is open).
  const [removeServerImage, setRemoveServerImage] = useState(false);
  // Item is saved but its photo step (upload/delete) failed: shown as a warning with retry.
  const [photoFailure, setPhotoFailure] = useState<{ itemId: number; next: boolean; created: boolean } | null>(null);
  const [focusTick, setFocusTick] = useState(0);
  const nameInputRef = useRef<HTMLInputElement>(null);

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

  // Refocus the name field (e.g. after "save and next") once the form is enabled again
  useEffect(() => {
    if (focusTick > 0 && !submitting) nameInputRef.current?.focus();
  }, [focusTick, submitting]);

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
        setImageFile(null);
        setImagePreview(item.imageUrl ?? null);
        setRemoveServerImage(false);
        setPhotoFailure(null);
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
    setRemoveServerImage(false);
    setPhotoFailure(null);
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
    setRemoveServerImage(false);
    setPhotoFailure(null);
    setIsEditMode(false);
    setEditItemId(null);
    setCameraMode('none');
  };

  const startCamera = async (requestedMode?: 'user' | 'environment') => {
    try {
      // Determine which camera to use - explicit parameter takes precedence
      const targetMode = requestedMode !== undefined ? requestedMode : facingMode;
      console.log('[Camera] Starting camera - requested:', requestedMode, ', current state:', facingMode, ', target:', targetMode);

      // Use exact constraint for better control on mobile devices
      const constraints: MediaStreamConstraints = {
        video: {
          facingMode: { exact: targetMode },
          width: { ideal: 1280 },
          height: { ideal: 720 },
        },
        audio: false,
      };

      console.log('[Camera] Requesting with exact facingMode:', targetMode);
      let mediaStream: MediaStream;

      try {
        mediaStream = await navigator.mediaDevices.getUserMedia(constraints);
        console.log('[Camera] Success with exact constraint');
      } catch (exactError) {
        console.warn('[Camera] Exact constraint failed, trying ideal:', exactError);
        // Fallback to ideal if exact fails
        const idealConstraints: MediaStreamConstraints = {
          video: {
            facingMode: { ideal: targetMode },
            width: { ideal: 1280 },
            height: { ideal: 720 },
          },
          audio: false,
        };
        mediaStream = await navigator.mediaDevices.getUserMedia(idealConstraints);
        console.log('[Camera] Success with ideal constraint');
      }

      // Get actual camera info
      const videoTrack = mediaStream.getVideoTracks()[0];
      const settings = videoTrack.getSettings() as any;
      const capabilities = videoTrack.getCapabilities() as any;

      console.log('[Camera] Active camera - facingMode:', settings.facingMode, 'deviceId:', settings.deviceId);
      console.log('[Camera] Capabilities:', capabilities);

      // CRITICAL: Use actual facingMode from camera, not what we requested
      // Some devices might give us a different camera than requested
      const actualFacingMode = settings.facingMode || targetMode;
      console.log('[Camera] Requested:', targetMode, ', Actual:', actualFacingMode);

      // Check for flash/torch
      if (capabilities.torch) {
        setHasFlash(true);
      } else {
        setHasFlash(false);
      }

      // Check for zoom
      if (capabilities.zoom) {
        setZoomRange({
          min: capabilities.zoom.min || 1,
          max: capabilities.zoom.max || 1,
        });
        setZoom(capabilities.zoom.min || 1);
      }

      // Update state with ACTUAL camera facingMode from device
      setFacingMode(actualFacingMode);
      setStream(mediaStream);
      setCameraMode('preview');

      console.log('[Camera] Camera started successfully - actual facingMode:', actualFacingMode);
    } catch (err) {
      console.error('[Camera] Failed to start camera:', err);
      setError(t('errors.cameraAccessFailed'));
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
    console.log('[Camera] Switch requested - current facingMode:', facingMode);

    // Calculate new mode BEFORE stopping camera
    const newMode = facingMode === 'user' ? 'environment' : 'user';
    console.log('[Camera] Switching to:', newMode);

    // Stop current camera
    if (stream) {
      stream.getTracks().forEach(track => {
        track.stop();
        console.log('[Camera] Stopped track:', track.label);
      });
      setStream(null);
    }
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }

    // Wait for cleanup
    await new Promise(resolve => setTimeout(resolve, 300));

    // Start with explicit new mode - DO NOT update state here, let startCamera handle it
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
      setError(t('errors.captureImageFailed'));
      return;
    }

    const video = videoRef.current;
    const canvas = canvasRef.current;

    // Check if video has valid dimensions
    if (video.videoWidth === 0 || video.videoHeight === 0) {
      console.error('Video dimensions are 0:', { width: video.videoWidth, height: video.videoHeight });
      setError(t('errors.captureImageFailed'));
      return;
    }

    console.log('Capturing photo:', { width: video.videoWidth, height: video.videoHeight });

    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;

    const ctx = canvas.getContext('2d');
    if (!ctx) {
      console.error('Could not get canvas context');
      setError(t('errors.captureImageFailed'));
      return;
    }

    // Draw current video frame to canvas
    ctx.drawImage(video, 0, 0);

    // Convert to blob
    canvas.toBlob((blob) => {
      if (!blob) {
        setError(t('errors.captureImageFailed'));
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
      setError(t('errors.imageTooLarge'));
      return;
    }

    if (!file.type.startsWith('image/')) {
      setError(t('errors.invalidImageType'));
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
    // A photo that already lives on the server is only removed when the user saves.
    if (isEditMode && imagePreview && !imagePreview.startsWith('data:')) {
      setRemoveServerImage(true);
    }
    setImageFile(null);
    setImagePreview(null);
    setCapturedImage(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  // Photo step once the item exists: upload a new photo, or delete the removed one.
  const applyPhoto = async (itemId: number) => {
    if (imageFile) {
      await itemService.uploadItemImage(itemId, imageFile);
    } else if (removeServerImage) {
      await itemService.deleteItemImage(itemId);
    }
  };

  // Item (and photo) done: continue with an empty form (next) or close.
  const finish = (next: boolean, created: boolean) => {
    onSuccess?.(created ? t('success.itemCreated') : t('success.itemUpdated'));
    if (created) onAddItem?.(); else onUpdateItem?.();
    if (next && created) {
      setName('');
      setImageFile(null);
      setImagePreview(null);
      setCapturedImage(null);
      setPhotoFailure(null);
      setError(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
      setFocusTick((n) => n + 1);
    } else {
      handleClose();
    }
  };

  const handleRetryPhoto = async () => {
    if (!photoFailure) return;
    setSubmitting(true);
    try {
      await applyPhoto(photoFailure.itemId);
      finish(photoFailure.next, photoFailure.created);
    } catch {
      // stays in the warning state; retry again or continue without photo
    } finally {
      setSubmitting(false);
    }
  };

  const handleSkipPhoto = () => {
    if (photoFailure) finish(photoFailure.next, photoFailure.created);
  };

  const submit = async (next: boolean) => {
    if (submitting || photoFailure) return;
    if (!name.trim()) {
      setError(t('validation.itemNameRequired'));
      return;
    }
    const created = !(isEditMode && editItemId);
    if (created && !currentBoxId) {
      setError(t('errors.invalidBoxId'));
      return;
    }

    setSubmitting(true);
    setError(null);

    let itemId: number;
    try {
      if (created) {
        itemId = (await itemService.createItem({ name: name.trim(), boxId: currentBoxId! })).id;
      } else {
        itemId = editItemId!;
        await itemService.updateItem(itemId, { name: name.trim() });
      }
    } catch (err) {
      const errorMsg = extractApiError(
        err,
        created ? t('errors.itemCreationFailed') : t('errors.itemUpdateFailed'),
      );
      setError(errorMsg);
      onError?.(errorMsg);
      setSubmitting(false);
      return;
    }

    try {
      await applyPhoto(itemId);
      finish(next, created);
    } catch {
      // The item itself is saved: refresh the list and warn instead of reporting success.
      if (created) onAddItem?.(); else onUpdateItem?.();
      setPhotoFailure({ itemId, next, created });
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmit = (e?: React.FormEvent) => {
    e?.preventDefault();
    void submit(false);
  };

  return (
    <>
      {/* FAB Button - only for create mode */}
      {!hideFab && (
        <Fab
          color="primary"
          aria-label={t('items.addNew')}
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
      )}

      {/* Main Dialog */}
      <Dialog
        open={open && cameraMode === 'none'}
        onClose={handleClose}
        maxWidth="sm"
        fullWidth
        fullScreen={fullScreen}
        slotProps={{ paper: { component: 'form', onSubmit: handleSubmit } }}
      >
        <DialogTitle>
          {isEditMode ? t('items.editItem') : t('items.addNew')}
        </DialogTitle>

        <DialogContent sx={{ pt: 2 }}>
          {error && (
            <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
              {error}
            </Alert>
          )}
          {photoFailure && (
            <Alert
              severity="warning"
              sx={{ mb: 2 }}
              action={
                <>
                  <Button color="inherit" size="small" onClick={handleRetryPhoto} disabled={submitting}>
                    {t('common.retry')}
                  </Button>
                  <Button color="inherit" size="small" onClick={handleSkipPhoto} disabled={submitting}>
                    {t('items.skipPhoto')}
                  </Button>
                </>
              }
            >
              {t('items.photoFailedWarning')}
            </Alert>
          )}

          <Stack spacing={2}>
            <TextField
              label={t('items.itemName')}
              value={name}
              onChange={(e) => setName(e.target.value)}
              fullWidth
              placeholder={t('items.itemNamePlaceholder')}
              disabled={submitting || !!photoFailure}
              autoFocus
              inputRef={nameInputRef}
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
                  alt={t('items.previewAlt')}
                  style={{
                    maxWidth: '100%',
                    maxHeight: '250px',
                    objectFit: 'contain',
                  }}
                />
                {!submitting && (
                  <IconButton
                    onClick={handleRemoveImage}
                    aria-label={t('items.removePhoto')}
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
              <Tooltip title={t('items.takePhoto')}>
                <Box sx={{ flex: 1 }}>
                  <IconButton
                    color="primary"
                    onClick={() => startCamera('environment')}
                    aria-label={t('items.takePhoto')}
                    disabled={submitting}
                    size="large"
                    sx={{ width: '100%', justifyContent: 'center' }}
                  >
                    <PhotoCamera sx={{ fontSize: 40 }} />
                  </IconButton>
                </Box>
              </Tooltip>
              <Tooltip title={t('items.selectFromGallery')}>
                <Box sx={{ flex: 1 }}>
                  <IconButton
                    color="primary"
                    onClick={() => fileInputRef.current?.click()}
                    aria-label={t('items.selectFromGallery')}
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
            {t('common.cancel')}
          </Button>
          {!isEditMode && (
            <Button
              variant="outlined"
              onClick={() => void submit(true)}
              disabled={submitting || !!photoFailure || !name.trim()}
            >
              {t('items.saveAndNext')}
            </Button>
          )}
          <Button
            type="submit"
            variant="contained"
            disabled={submitting || !!photoFailure || !name.trim()}
          >
            {submitting ? (
              <>
                <CircularProgress size={20} sx={{ mr: 1 }} />
                {t('common.saving')}
              </>
            ) : isEditMode ? (
              t('common.save')
            ) : (
              t('common.add')
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
              }}>
                <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                  <IconButton onClick={() => { stopCamera(); setCameraMode('none'); }} aria-label={t('common.close')} sx={{ color: 'white' }}>
                    <Close />
                  </IconButton>

                  {hasFlash && (
                    <IconButton onClick={cycleFlash} aria-label={t('items.flash')} sx={{ color: 'white' }}>
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
                  bottom: 200,
                  left: '50%',
                  transform: 'translateX(-50%)',
                  width: '80%',
                  maxWidth: 300,
                }}>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
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
                bottom: 80,
                left: 0,
                right: 0,
                p: 3,
              }}>
                <Stack direction="row" sx={{ justifyContent: 'space-around', alignItems: 'center' }}>
                  <IconButton onClick={switchCamera} aria-label={t('items.switchCamera')} sx={{ color: 'white' }}>
                    <Cameraswitch sx={{ fontSize: 32 }} />
                  </IconButton>

                  <IconButton
                    onClick={capturePhoto}
                    aria-label={t('items.capturePhoto')}
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
                alt={t('items.capturedAlt')}
                style={{
                  width: '100%',
                  height: '100%',
                  objectFit: 'contain',
                }}
              />

              <Box sx={{
                position: 'absolute',
                bottom: 80,
                left: 0,
                right: 0,
                p: 3,
                background: 'linear-gradient(to top, rgba(0,0,0,0.8), transparent)',
              }}>
                <Stack direction="row" sx={{ justifyContent: 'space-around', alignItems: 'center' }}>
                  <IconButton
                    onClick={retakePhoto}
                    aria-label={t('common.retake')}
                    sx={{
                      width: 64,
                      height: 64,
                      bgcolor: 'rgba(255,255,255,0.2)',
                      backdropFilter: 'blur(10px)',
                      color: 'white',
                      '&:hover': {
                        bgcolor: 'rgba(255,255,255,0.3)',
                      }
                    }}
                  >
                    <Replay sx={{ fontSize: 32 }} />
                  </IconButton>

                  <IconButton
                    onClick={acceptPhoto}
                    aria-label={t('common.accept')}
                    color="primary"
                    sx={{
                      width: 64,
                      height: 64,
                      bgcolor: 'primary.main',
                      color: 'white',
                      '&:hover': {
                        bgcolor: 'primary.dark',
                      }
                    }}
                  >
                    <CheckCircle sx={{ fontSize: 32 }} />
                  </IconButton>
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

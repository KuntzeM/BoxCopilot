import React, { useState, useRef, useEffect } from 'react';
import {
  Box,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  IconButton,
  Typography,
  Alert,
  Stack,
} from '@mui/material';
import {
  PhotoCamera,
  Upload,
  Delete,
  Close,
} from '@mui/icons-material';
import { uploadItemImage, deleteItemImage } from '../services/itemService';

interface ItemImageUploadProps {
  itemId: number;
  currentImageUrl?: string;
  onImageUpdated: () => void;
  onError: (message: string) => void;
  onSuccess: (message: string) => void;
}

export default function ItemImageUpload({
  itemId,
  currentImageUrl,
  onImageUpdated,
  onError,
  onSuccess,
}: ItemImageUploadProps) {
  const [isCameraOpen, setIsCameraOpen] = useState(false);
  const [stream, setStream] = useState<MediaStream | null>(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [isOnline, setIsOnline] = useState(navigator.onLine);
  const [imagePreviewOpen, setImagePreviewOpen] = useState(false);
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => setIsOnline(false);

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  useEffect(() => {
    return () => {
      if (stream) {
        stream.getTracks().forEach(track => track.stop());
      }
    };
  }, [stream]);

  const startCamera = async () => {
    if (!isOnline) {
      onError('Kamera nur online verfügbar');
      return;
    }

    try {
      const mediaStream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' },
      });
      setStream(mediaStream);
      setIsCameraOpen(true);
      
      // Wait for next tick to ensure dialog is open
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
      onError('Kamerazugriff fehlgeschlagen');
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

    canvas.toBlob(async (blob) => {
      if (!blob) {
        onError('Fehler beim Aufnehmen');
        return;
      }

      const file = new File([blob], 'photo.jpg', { type: 'image/jpeg' });
      await handleUpload(file);
      stopCamera();
    }, 'image/jpeg', 0.9);
  };

  const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      handleUpload(file);
    }
  };

  const handleUpload = async (file: File) => {
    if (!isOnline) {
      onError('Upload nur online verfügbar');
      return;
    }

    try {
      await uploadItemImage(itemId, file);
      onSuccess('Bild hochgeladen');
      onImageUpdated();
    } catch (error) {
      console.error('Upload error:', error);
      onError('Fehler beim Hochladen');
    }
  };

  const handleDelete = async () => {
    if (!isOnline) {
      onError('Löschen nur online verfügbar');
      return;
    }

    try {
      await deleteItemImage(itemId);
      onSuccess('Bild gelöscht');
      onImageUpdated();
      setDeleteDialogOpen(false);
    } catch (error) {
      console.error('Delete error:', error);
      onError('Fehler beim Löschen');
      setDeleteDialogOpen(false);
    }
  };

  return (
    <Box>
      {!isOnline && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Offline - Bildfunktionen nicht verfügbar
        </Alert>
      )}

      {currentImageUrl && (
        <Box sx={{ mb: 2, textAlign: 'center' }}>
          <img
            src={currentImageUrl}
            alt="Item"
            style={{ 
              maxWidth: '200px', 
              maxHeight: '200px', 
              borderRadius: 8,
              cursor: 'pointer'
            }}
            onClick={() => setImagePreviewOpen(true)}
          />
          <Typography variant="caption" display="block" color="text.secondary">
            Zum Vergrößern klicken
          </Typography>
        </Box>
      )}

      <Stack direction="row" spacing={1} flexWrap="wrap">
        <Button
          variant="outlined"
          startIcon={<PhotoCamera />}
          onClick={startCamera}
          disabled={!isOnline}
        >
          Foto aufnehmen
        </Button>

        <Button
          variant="outlined"
          startIcon={<Upload />}
          onClick={() => fileInputRef.current?.click()}
          disabled={!isOnline}
        >
          Hochladen
        </Button>

        {currentImageUrl && (
          <Button
            variant="outlined"
            color="error"
            startIcon={<Delete />}
            onClick={() => setDeleteDialogOpen(true)}
            disabled={!isOnline}
          >
            Löschen
          </Button>
        )}
      </Stack>

      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        capture="environment"
        style={{ display: 'none' }}
        onChange={handleFileSelect}
      />

      {/* Camera Dialog */}
      <Dialog open={isCameraOpen} onClose={stopCamera} maxWidth="md" fullWidth>
        <DialogTitle>
          Foto aufnehmen
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
          <Button onClick={stopCamera}>Abbrechen</Button>
          <Button variant="contained" onClick={capturePhoto}>
            Aufnehmen
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)}>
        <DialogTitle>Bild löschen?</DialogTitle>
        <DialogContent>
          <Typography>Möchten Sie das Bild wirklich löschen?</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>Abbrechen</Button>
          <Button variant="contained" color="error" onClick={handleDelete}>
            Löschen
          </Button>
        </DialogActions>
      </Dialog>

      {/* Image Preview Dialog */}
      <Dialog 
        open={imagePreviewOpen} 
        onClose={() => setImagePreviewOpen(false)}
        maxWidth="lg"
        fullWidth
      >
        <DialogTitle>
          Bild Vorschau
          <IconButton
            onClick={() => setImagePreviewOpen(false)}
            sx={{ position: 'absolute', right: 8, top: 8 }}
          >
            <Close />
          </IconButton>
        </DialogTitle>
        <DialogContent>
          <Box sx={{ textAlign: 'center', p: 2 }}>
            <img
              src={`/api/v1/items/${itemId}/image/large`}
              alt="Item Large"
              style={{ 
                maxWidth: '100%', 
                maxHeight: '80vh',
                objectFit: 'contain'
              }}
              onError={(e) => {
                // Fallback to thumbnail if large image not available
                e.currentTarget.src = currentImageUrl || '';
              }}
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setImagePreviewOpen(false)}>Schließen</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

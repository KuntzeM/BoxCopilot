import React from 'react';
import { Box, TextField, FormControlLabel, Checkbox, Typography } from '@mui/material';
import { useTranslation } from '../hooks/useTranslation';

export interface BoxFormData {
  currentRoom?: string;
  targetRoom?: string;
  description?: string;
  isFragile?: boolean;
  noStack?: boolean;
  isMovedToTarget?: boolean;
  labelPrinted?: boolean;
}

interface BoxFormProps {
  data: BoxFormData;
  onChange: (data: BoxFormData) => void;
}

const BoxForm: React.FC<BoxFormProps> = ({ data, onChange }) => {
  const { t } = useTranslation();
  
  const handleCurrentRoomChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange({ ...data, currentRoom: e.target.value });
  };

  const handleTargetRoomChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange({ ...data, targetRoom: e.target.value });
  };

  const handleDescriptionChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange({ ...data, description: e.target.value });
  };

  const handleFragileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange({ ...data, isFragile: e.target.checked });
  };

  const handleNoStackChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange({ ...data, noStack: e.target.checked });
  };

  const handleMovedChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange({ ...data, isMovedToTarget: e.target.checked });
  };

  const handleLabelPrintedChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange({ ...data, labelPrinted: e.target.checked });
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <TextField
        label={t('boxes.currentRoom')}
        value={data.currentRoom || ''}
        onChange={handleCurrentRoomChange}
        fullWidth
        placeholder={t('boxes.placeholderCurrentRoom')}
      />
      <TextField
        label={t('boxes.targetRoom')}
        value={data.targetRoom || ''}
        onChange={handleTargetRoomChange}
        fullWidth
        placeholder={t('boxes.placeholderTargetRoom')}
      />
      <TextField
        label={t('boxes.description')}
        value={data.description || ''}
        onChange={handleDescriptionChange}
        fullWidth
        multiline
        rows={4}
        placeholder={t('boxes.placeholderDescription')}
      />
      
      <Box sx={{ p: 2, bgcolor: 'action.hover', borderRadius: 1, border: 1, borderColor: 'divider' }}>
        <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1.5 }}>
          {t('boxes.transportHints')}
        </Typography>
        <FormControlLabel
          control={
            <Checkbox
              checked={data.isFragile || false}
              onChange={handleFragileChange}
              color="warning"
            />
          }
          label={t('boxes.fragileEmoji')}
        />
        <FormControlLabel
          control={
            <Checkbox
              checked={data.noStack || false}
              onChange={handleNoStackChange}
              color="error"
            />
          }
          label={t('boxes.noStackEmoji')}
        />
      </Box>
      
      <Box sx={{ p: 2, bgcolor: 'action.hover', borderRadius: 1, border: 1, borderColor: 'divider' }}>
        <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1.5 }}>
          {t('boxes.statusManagement')}
        </Typography>
        <FormControlLabel
          control={
            <Checkbox
              checked={data.isMovedToTarget || false}
              onChange={handleMovedChange}
              color="success"
            />
          }
          label={`🚚 ${t('boxes.moved')}`}
        />
        <FormControlLabel
          control={
            <Checkbox
              checked={data.labelPrinted || false}
              onChange={handleLabelPrintedChange}
              color="primary"
            />
          }
          label={`🏷️ ${t('boxes.labelPrinted')}`}
        />
      </Box>
    </Box>
  );
};

export default BoxForm;

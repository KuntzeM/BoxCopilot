import React from 'react';
import { Box, TextField } from '@mui/material';

export interface BoxFormData {
  currentRoom?: string;
  targetRoom?: string;
  description?: string;
}

interface BoxFormProps {
  data: BoxFormData;
  onChange: (data: BoxFormData) => void;
}

const BoxForm: React.FC<BoxFormProps> = ({ data, onChange }) => {
  const handleCurrentRoomChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange({ ...data, currentRoom: e.target.value });
  };

  const handleTargetRoomChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange({ ...data, targetRoom: e.target.value });
  };

  const handleDescriptionChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange({ ...data, description: e.target.value });
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <TextField
        label="Aktuelles Zimmer"
        value={data.currentRoom || ''}
        onChange={handleCurrentRoomChange}
        fullWidth
        placeholder="z.B. Wohnzimmer"
      />
      <TextField
        label="Zielzimmer"
        value={data.targetRoom || ''}
        onChange={handleTargetRoomChange}
        fullWidth
        placeholder="z.B. Keller"
      />
      <TextField
        label="Beschreibung"
        value={data.description || ''}
        onChange={handleDescriptionChange}
        fullWidth
        multiline
        rows={4}
        placeholder="Optionale Beschreibung der Box..."
      />
    </Box>
  );
};

export default BoxForm;

import React from 'react';
import { Box, TextField, FormControlLabel, Checkbox, Typography } from '@mui/material';

export interface BoxFormData {
  currentRoom?: string;
  targetRoom?: string;
  description?: string;
  isFragile?: boolean;
  noStack?: boolean;
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

  const handleFragileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange({ ...data, isFragile: e.target.checked });
  };

  const handleNoStackChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange({ ...data, noStack: e.target.checked });
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
      
      <Box sx={{ p: 2, backgroundColor: 'grey.100', borderRadius: 1 }}>
        <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1.5 }}>
          Transport-Hinweise
        </Typography>
        <FormControlLabel
          control={
            <Checkbox
              checked={data.isFragile || false}
              onChange={handleFragileChange}
              color="warning"
            />
          }
          label="🔔 Zerbrechlich / Fragile"
        />
        <FormControlLabel
          control={
            <Checkbox
              checked={data.noStack || false}
              onChange={handleNoStackChange}
              color="error"
            />
          }
          label="⛔ Nichts drauf stellen / Do Not Stack"
        />
      </Box>
    </Box>
  );
};

export default BoxForm;

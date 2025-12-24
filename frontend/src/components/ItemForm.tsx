import React, { useState } from 'react';
import { Box, TextField, Button, Card, CardContent, Typography } from '@mui/material';
import { CreateItemPayload } from '../types/models';

interface ItemFormProps {
  onAddItem: (data: CreateItemPayload) => Promise<void>;
  boxUuid: string;
  isLoading?: boolean;
}

const ItemForm: React.FC<ItemFormProps> = ({ onAddItem, boxUuid, isLoading = false }) => {
  const [formData, setFormData] = useState<CreateItemPayload>({
    boxUuid,
    name: '',
  });

  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.name.trim()) return;

    setIsSubmitting(true);
    try {
      await onAddItem(formData);
      setFormData({ boxUuid, name: '' });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Card sx={{ mt: 3 }}>
      <CardContent>
        <Typography variant="h6" sx={{ mb: 2 }}>
          Neues Item hinzufügen
        </Typography>
        <Box component="form" onSubmit={handleSubmit} sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField
            label="Name"
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            fullWidth
            required
            disabled={isSubmitting || isLoading}
          />
          <Button
            type="submit"
            variant="contained"
            disabled={!formData.name.trim() || isSubmitting || isLoading}
          >
            Hinzufügen
          </Button>
        </Box>
      </CardContent>
    </Card>
  );
};

export default ItemForm;

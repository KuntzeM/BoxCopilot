import { Dialog, DialogContent, DialogTitle, LinearProgress, Typography, Box } from '@mui/material';
import { useTranslation } from '../hooks/useTranslation';

export interface LabelGenerationProgressProps {
  open: boolean;
  current: number;
  total: number;
  currentBoxNumber: string;
}

/**
 * Progress dialog shown during label PDF generation
 * Displays current box being processed and overall progress
 */
export const LabelGenerationProgress = ({
  open,
  current,
  total,
  currentBoxNumber,
}: LabelGenerationProgressProps) => {
  const { t } = useTranslation();

  const progressPercent = total > 0 ? (current / total) * 100 : 0;

  return (
    <Dialog
      open={open}
      disableEscapeKeyDown
      aria-labelledby="label-generation-progress-title"
      maxWidth="sm"
      fullWidth
    >
      <DialogTitle id="label-generation-progress-title">
        {t('boxes.generatingLabels') || 'Generating Labels...'}
      </DialogTitle>
      <DialogContent>
        <Box sx={{ width: '100%', mt: 2 }}>
          {/* Progress Bar */}
          <LinearProgress
            variant="determinate"
            value={progressPercent}
            sx={{ height: 10, borderRadius: 5, mb: 2 }}
          />

          {/* Progress Text */}
          <Typography variant="body1" align="center" gutterBottom>
            {t('boxes.generatingLabelProgress', {
              current,
              total,
            }) || `${current} of ${total} labels`}
          </Typography>

          {/* Current Box */}
          <Typography variant="body2" color="text.secondary" align="center">
            {t('boxes.currentBox') || 'Current box'}: <strong>Box {currentBoxNumber}</strong>
          </Typography>
        </Box>
      </DialogContent>
    </Dialog>
  );
};

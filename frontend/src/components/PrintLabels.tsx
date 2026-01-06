import React from 'react';
import { Box, Paper, Stack, Typography } from '@mui/material';
import { QRCodeCanvas } from 'qrcode.react';
import { Box as BoxModel } from '../types/models';
import { truncateToFirstLine } from '../utils/textUtils';
import { useTranslation } from '../hooks/useTranslation';

interface PrintLabelsProps {
  boxes: BoxModel[];
  isPrinting: boolean;
}

/**
 * PrintLabels Component
 * 
 * Renders print-optimized QR code labels for boxes.
 * Only visible during printing (controlled by isPrinting prop).
 * Styled via `print.css` for cross-platform compatibility (Desktop/Android/iOS).
 */
export const PrintLabels: React.FC<PrintLabelsProps> = ({ boxes, isPrinting }) => {
  const { t } = useTranslation();

  if (!isPrinting || boxes.length === 0) {
    return null;
  }

  console.log('[DEBUG] PrintLabels rendering', boxes.length, 'boxes');

  return (
    <Box className="print-area" sx={{ display: 'grid', gap: 0 }}>
      {boxes.map((box) => (
        <Paper
          key={`print-${box.id}`}
          className="print-label"
          elevation={0}
          sx={{ border: 'none', boxShadow: 'none' }}
        >
          {/* Left: QR Code - optimized for mobile print */}
          <Box 
            sx={{ 
              display: 'grid', 
              placeItems: 'center', 
              flexShrink: 0, 
              width: '7cm', 
              height: '100%',
              /* Ensure QR renders on Android - critical for print */
              '@media print': {
                WebkitPrintColorAdjust: 'exact !important',
                printColorAdjust: 'exact !important',
              }
            }}
          >
            <QRCodeCanvas 
              value={box.publicUrl || ''} 
              size={160} 
              includeMargin 
              level="H"
              /* Canvas rendering optimization for mobile print */
              style={{ display: 'block', maxWidth: '100%', height: 'auto' }}
            />
          </Box>

          {/* Right: Text content */}
          <Stack direction="column" spacing={1} sx={{ flex: 1, pl: 2, pr: 1 }}>
            {/* Prominent transport badges above ID */}
            {(box.isFragile || box.noStack) && (
              <Stack direction="row" spacing={1} sx={{ mb: 1 }}>
                {box.isFragile && (
                  <Typography
                    sx={{
                      fontSize: '1.1rem',
                      fontWeight: 900,
                      textTransform: 'uppercase',
                      backgroundColor: '#000',
                      color: '#fff',
                      px: 1.5,
                      py: 0.5,
                      lineHeight: 1,
                      letterSpacing: '0.04em',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 0.5,
                    }}
                  >
                    <span style={{ fontSize: '1.2rem', lineHeight: 1 }}>🔔</span>
                    {t('boxes.fragilePrintLabel')}
                  </Typography>
                )}
                {box.noStack && (
                  <Typography
                    sx={{
                      fontSize: '1.1rem',
                      fontWeight: 900,
                      textTransform: 'uppercase',
                      backgroundColor: '#000',
                      color: '#fff',
                      px: 1.5,
                      py: 0.5,
                      lineHeight: 1,
                      letterSpacing: '0.04em',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 0.5,
                    }}
                  >
                    <span style={{ fontSize: '1.2rem', lineHeight: 1 }}>⛔</span>
                    {t('boxes.noStackPrintLabel')}
                  </Typography>
                )}
              </Stack>
            )}
            
            {/* Box #ID */}
            <Typography sx={{ fontSize: '2rem', fontWeight: 800, lineHeight: 1 }}>
              {t('boxes.boxNumber', { number: box.id })}
            </Typography>

            {/* Target Room */}
            <Typography sx={{ fontSize: '1.5rem', fontWeight: 700, lineHeight: 1.2, textTransform: 'uppercase' }}>
              {t('boxes.targetRoom')}: {box.targetRoom || '-'}
            </Typography>

            {/* Description */}
            {box.description && (
              <Typography
                sx={{
                  fontSize: '0.9rem',
                  fontStyle: 'italic',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  display: '-webkit-box',
                  WebkitLineClamp: 3,
                  WebkitBoxOrient: 'vertical',
                }}
              >
                {truncateToFirstLine(box.description)}
              </Typography>
            )}
          </Stack>
        </Paper>
      ))}
    </Box>
  );
};

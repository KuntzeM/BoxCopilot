import QRCode from 'qrcode';

/**
 * QR Code Generation Utilities for Label Printing
 */

export interface QRCodeOptions {
  errorCorrectionLevel?: 'L' | 'M' | 'Q' | 'H';
  width?: number;
  margin?: number;
  color?: {
    dark?: string;
    light?: string;
  };
}

/**
 * Generates a high-quality QR code as data URL for PDF embedding
 * @param url - The URL to encode in the QR code
 * @param options - QR code generation options
 * @returns Promise resolving to data URL string
 */
export async function generateQRCodeDataURL(
  url: string,
  options: QRCodeOptions = {}
): Promise<string> {
  const defaultOptions: QRCodeOptions = {
    errorCorrectionLevel: 'M', // 15% recovery (good balance for labels)
    width: 300, // 300px at 96 DPI = high quality for printing
    margin: 4, // 4 modules quiet zone (standard)
    color: {
      dark: '#000000', // Pure black for best contrast
      light: '#FFFFFF', // Pure white background
    },
  };

  const finalOptions = { ...defaultOptions, ...options };

  try {
    return await QRCode.toDataURL(url, finalOptions);
  } catch (error) {
    console.error('[QRCode] Failed to generate QR code:', error);
    throw new Error(`QR code generation failed: ${error instanceof Error ? error.message : 'Unknown error'}`);
  }
}

/**
 * Progress callback for batch QR code generation
 */
export type QRCodeProgressCallback = (current: number, total: number, boxNumber: string) => void;

/**
 * Generates QR codes for multiple boxes with progress tracking
 * @param boxes - Array of boxes with publicUrl and id
 * @param onProgress - Optional callback for progress updates
 * @returns Promise resolving to array of data URLs in same order as input
 */
export async function generateQRCodesForBoxes<T extends { publicUrl?: string; id: number }>(
  boxes: T[],
  onProgress?: QRCodeProgressCallback
): Promise<string[]> {
  const qrCodes: string[] = [];

  for (let i = 0; i < boxes.length; i++) {
    const box = boxes[i];
    const url = box.publicUrl || '';

    if (onProgress) {
      onProgress(i + 1, boxes.length, `#${box.id}`);
    }

    try {
      const qrCode = await generateQRCodeDataURL(url);
      qrCodes.push(qrCode);
    } catch (error) {
      console.error(`[QRCode] Failed to generate QR code for box #${box.id}:`, error);
      // Push empty string on error to maintain array indices
      qrCodes.push('');
    }

    // Yield to UI thread to prevent blocking
    await new Promise(resolve => setTimeout(resolve, 0));
  }

  return qrCodes;
}

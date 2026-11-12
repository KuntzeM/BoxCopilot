import { createElement, type ReactElement } from 'react';
import type { DocumentProps } from '@react-pdf/renderer';
import type { Box } from '../types/models';
import type { QRCodeProgressCallback } from './printUtils';

const BASE_TIMEOUT_MS = 30000; // base timeout for PDF generation
const PER_BOX_TIMEOUT_MS = 2000; // additional time per box

/** Renders the label PDF for the given boxes. PDF libs are imported lazily to keep them out of initial chunks. */
export async function generateLabelPdf(
  boxes: Box[],
  t: (key: string) => string,
  onProgress: QRCodeProgressCallback,
): Promise<Blob> {
  const [{ pdf }, { LabelPDFDocument }, { generateQRCodesForBoxes }, { registerPDFFonts }] = await Promise.all([
    import('@react-pdf/renderer'),
    import('../components/LabelPDFDocument'),
    import('./printUtils'),
    import('./pdfFonts'),
  ]);
  registerPDFFonts();
  onProgress(0, boxes.length, '');
  const qrCodes = await generateQRCodesForBoxes(boxes, onProgress);

  // Translations are passed as props (hooks cannot be used inside PDF components).
  // Box number prefix = template ("#{{number}}") without the placeholder.
  const template = t('boxes.boxNumber');
  const idx = template.indexOf('{{number}}');
  const translations = {
    boxNumber: idx !== -1 ? template.slice(0, idx).trimEnd() : '#',
    targetRoom: t('boxes.targetRoom').toUpperCase(),
    fragile: t('boxes.fragilePrintLabel'),
    doNotStack: t('boxes.noStackPrintLabel'),
  };

  const blobPromise = pdf(
    createElement(LabelPDFDocument, { boxes, qrCodes, translations }) as unknown as ReactElement<DocumentProps>,
  ).toBlob();
  const timeout = new Promise<Blob>((_, reject) => {
    setTimeout(() => reject(new Error('PDF generation timeout')), BASE_TIMEOUT_MS + boxes.length * PER_BOX_TIMEOUT_MS);
  });
  return Promise.race([blobPromise, timeout]);
}

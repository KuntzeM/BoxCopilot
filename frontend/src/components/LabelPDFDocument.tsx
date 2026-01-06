import { Document, Page, View, Text, Image, StyleSheet } from '@react-pdf/renderer';
import { Box } from '../types/models';

/**
 * Badge color constants for handling flags
 */
const BADGE_FRAGILE_COLOR = '#FF9800'; // Orange for fragile items
const BADGE_NO_STACK_COLOR = '#F44336'; // Red for no-stack warning

/**
 * Translations interface for PDF labels
 * Translations must be passed as props since PDF components cannot use hooks
 */
export interface LabelTranslations {
  boxNumber: string; // "Box #"
  targetRoom: string; // "TARGET ROOM" / "ZIELRAUM"
  fragile: string; // "FRAGILE" / "ZERBRECHLICH"
  doNotStack: string; // "DO NOT STACK" / "NICHT STAPELN"
}

interface LabelPDFDocumentProps {
  boxes: Box[];
  qrCodes: string[]; // Data URLs in same order as boxes
  translations: LabelTranslations;
}

// PDF Styles using @react-pdf/renderer StyleSheet
const styles = StyleSheet.create({
  page: {
    padding: 0,
    margin: 0,
    backgroundColor: '#FFFFFF',
  },
  labelContainer: {
    display: 'flex',
    flexDirection: 'column',
  },
  label: {
    width: '210mm', // A4 portrait width
    height: '95mm', // 3 labels per 297mm height (297/3 ≈ 99mm, minus lines = 95mm)
    padding: '5mm',
    display: 'flex',
    flexDirection: 'row',
    alignItems: 'stretch',
    gap: '5mm',
    position: 'relative',
  },
  dashedLine: {
    width: '100%',
    height: 0,
    borderBottom: '1pt dashed #CCCCCC',
  },
  qrContainer: {
    width: '60mm', // QR code area
    height: '85mm',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
  qrImage: {
    width: '60mm',
    height: '60mm',
  },
  textContent: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'space-between',
  },
  topSection: {
    display: 'flex',
    flexDirection: 'column',
    gap: '3mm',
  },
  badgeContainer: {
    display: 'flex',
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: '3mm',
  },
  badgeFragile: {
    backgroundColor: BADGE_FRAGILE_COLOR,
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: 900,
    padding: '3mm 5mm',
    borderRadius: '3mm',
    textTransform: 'uppercase',
    fontFamily: 'Helvetica-Bold',
  },
  badgeNoStack: {
    backgroundColor: BADGE_NO_STACK_COLOR,
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: 900,
    padding: '3mm 5mm',
    borderRadius: '3mm',
    textTransform: 'uppercase',
    fontFamily: 'Helvetica-Bold',
  },
  boxNumber: {
    fontSize: 36,
    fontWeight: 'bold',
    fontFamily: 'Helvetica-Bold',
  },
  targetRoomLabel: {
    fontSize: 14,
    fontWeight: 'bold',
    fontFamily: 'Helvetica-Bold',
    color: '#666666',
    marginBottom: '2mm',
  },
  targetRoomValue: {
    fontSize: 24,
    fontWeight: 'bold',
    fontFamily: 'Helvetica-Bold',
    textTransform: 'uppercase',
  },
  description: {
    fontSize: 11,
    fontFamily: 'Helvetica',
    color: '#333333',
    lineHeight: 1.4,
    marginTop: '3mm',
  },
});

/**
 * Individual label component for one box
 */
const Label = ({
  box,
  qrCode,
  translations,
  showDashedLine,
}: {
  box: Box;
  qrCode: string;
  translations: LabelTranslations;
  showDashedLine: boolean;
}) => {
  // Truncate description to max ~200 characters
  const truncateDescription = (text: string | undefined, maxLength: number = 200): string => {
    if (!text) return '';
    if (text.length <= maxLength) return text;
    return text.substring(0, maxLength).trim() + '...';
  };

  return (
    <>
      <View style={styles.label}>
        {/* QR Code - Left Side */}
        <View style={styles.qrContainer}>
          <Image src={qrCode} style={styles.qrImage} />
        </View>

        {/* Text Content - Right Side */}
        <View style={styles.textContent}>
          {/* Top Section: Box Number, Badges, Target Room */}
          <View style={styles.topSection}>
            {/* Box Number */}
            <Text style={styles.boxNumber}>
              {translations.boxNumber}{box.boxNumber}
            </Text>

            {/* Handling Badges */}
            {(box.isFragile || box.noStack) && (
              <View style={styles.badgeContainer}>
                {box.isFragile && (
                  <Text style={styles.badgeFragile}>! {translations.fragile}</Text>
                )}
                {box.noStack && (
                  <Text style={styles.badgeNoStack}>X {translations.doNotStack}</Text>
                )}
              </View>
            )}

            {/* Target Room */}
            {box.targetRoom && (
              <View>
                <Text style={styles.targetRoomLabel}>{translations.targetRoom}:</Text>
                <Text style={styles.targetRoomValue}>{box.targetRoom}</Text>
              </View>
            )}
          </View>

          {/* Description - Bottom */}
          {box.description && (
            <Text style={styles.description}>
              {truncateDescription(box.description)}
            </Text>
          )}
        </View>
      </View>
      {/* Dashed line separator (except for last label on page) */}
      {showDashedLine && <View style={styles.dashedLine} />}
    </>
  );
};

/**
 * PDF Document containing all labels
 * 3 labels per A4 portrait page with dashed separator lines
 */
export const LabelPDFDocument = ({ boxes, qrCodes, translations }: LabelPDFDocumentProps) => {
  // Group labels into pages (3 labels per page)
  const labelsPerPage = 3;
  const pageCount = Math.ceil(boxes.length / labelsPerPage);

  const pages = Array.from({ length: pageCount }, (_, pageIndex) => {
    const startIdx = pageIndex * labelsPerPage;
    const endIdx = Math.min(startIdx + labelsPerPage, boxes.length);
    return boxes.slice(startIdx, endIdx).map((box, idx) => ({
      box,
      qrCode: qrCodes[startIdx + idx],
      isLast: startIdx + idx === boxes.length - 1 || idx === labelsPerPage - 1,
    }));
  });

  return (
    <Document
      title="BoxCopilot Labels"
      author="BoxCopilot"
      subject="Box Labels for Moving"
      keywords="moving, boxes, labels, qr"
    >
      {pages.map((pageLabels, pageIdx) => (
        <Page key={pageIdx} size="A4" orientation="portrait" style={styles.page}>
          <View style={styles.labelContainer}>
            {pageLabels.map(({ box, qrCode, isLast }, labelIdx) => (
              <Label
                key={box.id}
                box={box}
                qrCode={qrCode}
                translations={translations}
                showDashedLine={!isLast}
              />
            ))}
          </View>
        </Page>
      ))}
    </Document>
  );
};

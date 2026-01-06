import { useState, useEffect, useMemo, createElement } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box as BoxModel, CreateBoxPayload } from '../types/models';
import { fetchBoxes, createBox, deleteBox } from '../services/boxService';
import { searchItems } from '../services/itemService';
import { useTranslation } from './useTranslation';

type SnackbarState = { open: boolean; message: string; severity: 'success' | 'error' | 'info' };

// PDF Generation Constants
const BASE_TIMEOUT_MS = 30000; // 30 seconds base timeout for PDF generation
const PER_BOX_TIMEOUT_MS = 2000; // Additional 2 seconds per box
const BLOB_CLEANUP_TIMEOUT_MS = 300000; // 5 minutes fallback cleanup for blob URLs

/**
 * Custom Hook: useBoxListLogic
 * 
 * Encapsulates all business logic for the BoxList component:
 * - Data fetching and filtering
 * - Box selection and expansion
 * - Print functionality
 * - CRUD operations
 * - Drawer/dialog state management
 */
export const useBoxListLogic = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();

  // === State Management ===
  const [allBoxes, setAllBoxes] = useState<BoxModel[]>([]);
  const [filteredBoxes, setFilteredBoxes] = useState<BoxModel[]>([]);
  const [matchedItemIds, setMatchedItemIds] = useState<Set<number>>(new Set());
  const [isLoading, setIsLoading] = useState(true);
  
  // Filter state
  const [itemQuery, setItemQuery] = useState('');
  const [roomQuery, setRoomQuery] = useState('');
  const [filterFragile, setFilterFragile] = useState(false);
  const [filterNoStack, setFilterNoStack] = useState(false);
  const [showAdvancedFilters, setShowAdvancedFilters] = useState(false);
  
  // Selection & UI state
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [expandedBoxes, setExpandedBoxes] = useState<Set<number>>(new Set());
  const [snackbar, setSnackbar] = useState<SnackbarState>({ open: false, message: '', severity: 'success' });
  
  // PDF generation progress state
  const [pdfProgress, setPdfProgress] = useState<{
    open: boolean;
    current: number;
    total: number;
    currentBoxNumber: string;
  }>({ open: false, current: 0, total: 0, currentBoxNumber: '' });
  
  // Dialog state
  const [openBoxDialog, setOpenBoxDialog] = useState(false);
  const [boxFormData, setBoxFormData] = useState<CreateBoxPayload>({ 
    currentRoom: '', 
    targetRoom: '', 
    description: '',
    isFragile: false,
    noStack: false
  });
  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null);
  const [fullImageUrl, setFullImageUrl] = useState<string | null>(null);
  
  // Drawer state
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedBoxForActions, setSelectedBoxForActions] = useState<BoxModel | null>(null);

  // === Computed Values ===
  const selectedBoxes = useMemo(
    () => filteredBoxes.filter((b) => selectedIds.includes(b.id)),
    [selectedIds, filteredBoxes]
  );

  const apiBase = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');
  const withApiBase = (path: string) =>
    apiBase ? `${apiBase}${path.startsWith('/') ? path : `/${path}`}` : path;
  const resolveImageUrl = (url?: string) => {
    if (!url) return undefined;
    if (url.startsWith('http://') || url.startsWith('https://')) return url;
    return withApiBase(url.startsWith('/') ? url : `/${url}`);
  };

  // === Effects ===
  useEffect(() => {
    loadData();
  }, []);

  useEffect(() => {
    if (allBoxes.length === 0 && !isLoading) {
      return;
    }

    const timeoutId = setTimeout(() => {
      handleSearch();
    }, 400);

    return () => clearTimeout(timeoutId);
  }, [itemQuery, roomQuery, filterFragile, filterNoStack, allBoxes]);

  // === Data Operations ===
  const loadData = async () => {
    setIsLoading(true);
    try {
      const boxes = await fetchBoxes();

      const withUrls = boxes
        .sort((a, b) => (a.boxNumber || 0) - (b.boxNumber || 0))
        .map((b) => ({
          ...b,
          publicUrl: `${window.location.origin}/public/${b.uuid}`,
        })) as BoxModel[];

      setAllBoxes(withUrls);
      setFilteredBoxes(withUrls);
      setSelectedIds([]);
    } catch (error) {
      setSnackbar({ open: true, message: t('errors.boxesFetchFailed'), severity: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  const handleResetFilter = () => {
    setItemQuery('');
    setRoomQuery('');
    setFilterFragile(false);
    setFilterNoStack(false);
    setMatchedItemIds(new Set());
    setFilteredBoxes(allBoxes);
  };

  const handleSearch = async () => {
    try {
      let next = [...allBoxes];
      const roomTerm = roomQuery.trim().toLowerCase();
      const itemTerm = itemQuery.trim();

      if (roomTerm) {
        // Check if roomTerm is a number (search by box number)
        const boxNumSearch = parseInt(roomTerm, 10);
        if (!isNaN(boxNumSearch)) {
          next = next.filter((b) => b.boxNumber === boxNumSearch);
        } else {
          // Search by room name
          next = next.filter((b) => (b.currentRoom || '').toLowerCase().includes(roomTerm));
        }
      }

      if (itemTerm) {
        const items = await searchItems(itemTerm);
        const ids = new Set(items.map((i) => i.boxId));
        setMatchedItemIds(new Set(items.map((i) => i.id)));
        next = next.filter((b) => ids.has(b.id));
      } else {
        setMatchedItemIds(new Set());
      }

      if (filterFragile) {
        next = next.filter((b) => b.isFragile === true);
      }

      if (filterNoStack) {
        next = next.filter((b) => b.noStack === true);
      }

      setFilteredBoxes(next);
      if (next.length === 0) {
        setSnackbar({ open: true, message: t('errors.noSearchResults'), severity: 'info' });
      }
    } catch (error) {
      setMatchedItemIds(new Set());
      setSnackbar({ open: true, message: t('errors.searchFailed'), severity: 'error' });
    }
  };

  // === UI Handlers ===
  const toggleSelect = (id: number) => {
    setSelectedIds((prev) => prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]);
  };

  const toggleExpandBox = (id: number) => {
    setExpandedBoxes((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const handleCopyLink = async (url?: string) => {
    if (!url) {
      setSnackbar({ open: true, message: t('errors.noPublicLink'), severity: 'error' });
      return;
    }
    try {
      await navigator.clipboard.writeText(url);
      setSnackbar({ open: true, message: t('success.linkCopied'), severity: 'success' });
    } catch (error) {
      setSnackbar({ open: true, message: t('errors.copyFailed'), severity: 'error' });
    }
  };

  const handleOpenPublicLink = (url?: string) => {
    if (!url) {
      setSnackbar({ open: true, message: t('errors.noPublicLink'), severity: 'error' });
      return;
    }
    window.open(url, '_blank');
  };

  const handlePrintLabels = async () => {
    if (selectedBoxes.length === 0) {
      setSnackbar({ open: true, message: t('boxes.noBoxSelected'), severity: 'info' });
      return;
    }
    
    try {
      // Dynamically import PDF dependencies (code splitting)
      const [{ pdf }, { LabelPDFDocument }, { generateQRCodesForBoxes }, { registerPDFFonts }] = await Promise.all([
        import('@react-pdf/renderer'),
        import('../components/LabelPDFDocument'),
        import('../utils/printUtils'),
        import('../utils/pdfFonts'),
      ]);
      
      // Register fonts for PDF
      registerPDFFonts();
      
      // Show progress dialog
      setPdfProgress({ open: true, current: 0, total: selectedBoxes.length, currentBoxNumber: '' });
      
      // Generate QR codes with progress updates
      const qrCodes = await generateQRCodesForBoxes(
        selectedBoxes,
        (current, total, boxNumber) => {
          setPdfProgress({ open: true, current, total, currentBoxNumber: boxNumber });
        }
      );
      
      // Prepare translations (cannot use hooks inside PDF components)
      // Extract box number prefix by removing the {{number}} placeholder
      // Expected format: "#{{number}}" or "Box {{number}}"
      const boxNumberTemplate = t('boxes.boxNumber');
      const boxNumberPlaceholder = '{{number}}';
      const placeholderIndex = boxNumberTemplate.indexOf(boxNumberPlaceholder);
      const boxNumberPrefix =
        placeholderIndex !== -1
          ? boxNumberTemplate.slice(0, placeholderIndex).trimEnd()
          : '#'; // Fallback to '#' if placeholder not found
      
      const translations = {
        boxNumber: boxNumberPrefix,
        targetRoom: t('boxes.targetRoom').toUpperCase(),
        fragile: t('boxes.fragilePrintLabel'),
        doNotStack: t('boxes.noStackPrintLabel'),
      };
      
      // Generate PDF blob with dynamic timeout (base 30s + 2s per box)
      const timeoutDuration = BASE_TIMEOUT_MS + (selectedBoxes.length * PER_BOX_TIMEOUT_MS);
      const pdfBlobPromise = pdf(
        createElement(LabelPDFDocument, { boxes: selectedBoxes, qrCodes, translations })
      ).toBlob();
      
      const timeoutPromise = new Promise<Blob>((_, reject) => {
        setTimeout(() => reject(new Error('PDF generation timeout')), timeoutDuration);
      });
      
      const pdfBlob = await Promise.race([pdfBlobPromise, timeoutPromise]);
      
      // Close progress dialog
      setPdfProgress({ open: false, current: 0, total: 0, currentBoxNumber: '' });
      
      // Create blob URL and open in new tab
      const blobUrl = URL.createObjectURL(pdfBlob);
      
      // Set up cleanup timeout as fallback (5 minutes)
      const cleanupTimeoutId = setTimeout(() => {
        URL.revokeObjectURL(blobUrl);
      }, BLOB_CLEANUP_TIMEOUT_MS);
      
      const printWindow = window.open(blobUrl, '_blank');
      
      if (!printWindow) {
        URL.revokeObjectURL(blobUrl);
        clearTimeout(cleanupTimeoutId);
        throw new Error('Failed to open print window. Please check popup blocker settings.');
      }
      
      // Trigger print dialog after PDF loads (with delay for rendering)
      printWindow.addEventListener('load', () => {
        setTimeout(() => {
          printWindow.print();
          
          // Cleanup blob URL after print dialog closes
          printWindow.addEventListener('afterprint', () => {
            URL.revokeObjectURL(blobUrl);
            clearTimeout(cleanupTimeoutId);
          });
        }, 500);
      });
      
    } catch (error) {
      console.error('[PDF] Label generation failed:', error);
      
      // Close progress dialog
      setPdfProgress({ open: false, current: 0, total: 0, currentBoxNumber: '' });
      
      // Show error message
      const errorMessage = error instanceof Error && error.message.includes('timeout')
        ? t('boxes.pdfGenerationTimeout')
        : t('boxes.pdfGenerationFailed');
      
      setSnackbar({ open: true, message: errorMessage, severity: 'error' });
    }
  };

  // === Box CRUD ===
  const handleOpenBoxDialog = () => {
    setBoxFormData({ currentRoom: '', targetRoom: '', description: '', isFragile: false, noStack: false });
    setOpenBoxDialog(true);
  };

  const handleSubmitBox = async () => {
    try {
      const newBox = await createBox(boxFormData as CreateBoxPayload);
      setSnackbar({ open: true, message: t('success.boxCreated'), severity: 'success' });
      setOpenBoxDialog(false);
      setBoxFormData({ currentRoom: '', targetRoom: '', description: '', isFragile: false, noStack: false });
      navigate(`/app/boxes/${newBox.id}/edit`);
    } catch (error) {
      setSnackbar({ open: true, message: t('errors.boxCreateFailed'), severity: 'error' });
    }
  };

  const handleDeleteBox = async () => {
    if (deleteConfirmId === null) return;
    try {
      await deleteBox(deleteConfirmId);
      setSnackbar({ open: true, message: t('success.boxDeleted'), severity: 'success' });
      setDeleteConfirmId(null);
      await loadData();
    } catch (error) {
      setSnackbar({ open: true, message: t('errors.boxDeleteFailed'), severity: 'error' });
    }
  };

  // === Drawer Actions ===
  const handleOpenBoxActions = (box: BoxModel) => {
    setSelectedBoxForActions(box);
    setDrawerOpen(true);
  };

  const handleCloseDrawer = () => {
    setDrawerOpen(false);
    setSelectedBoxForActions(null);
  };

  const handleDrawerEdit = () => {
    if (selectedBoxForActions) {
      navigate(`/app/boxes/${selectedBoxForActions.id}/edit`);
    }
    handleCloseDrawer();
  };

  const handleDrawerCopyLink = async () => {
    if (selectedBoxForActions?.publicUrl) {
      await handleCopyLink(selectedBoxForActions.publicUrl);
    }
    handleCloseDrawer();
  };

  const handleDrawerOpenLink = () => {
    if (selectedBoxForActions?.publicUrl) {
      handleOpenPublicLink(selectedBoxForActions.publicUrl);
    }
    handleCloseDrawer();
  };

  const handleDrawerDelete = () => {
    if (selectedBoxForActions) {
      setDeleteConfirmId(selectedBoxForActions.id);
    }
    handleCloseDrawer();
  };

  return {
    // State
    allBoxes,
    filteredBoxes,
    matchedItemIds,
    isLoading,
    itemQuery,
    setItemQuery,
    roomQuery,
    setRoomQuery,
    filterFragile,
    setFilterFragile,
    filterNoStack,
    setFilterNoStack,
    showAdvancedFilters,
    setShowAdvancedFilters,
    selectedIds,
    expandedBoxes,
    snackbar,
    setSnackbar,
    pdfProgress,
    selectedBoxes,
    openBoxDialog,
    setOpenBoxDialog,
    boxFormData,
    setBoxFormData,
    deleteConfirmId,
    setDeleteConfirmId,
    fullImageUrl,
    setFullImageUrl,
    drawerOpen,
    selectedBoxForActions,
    
    // Computed
    resolveImageUrl,
    withApiBase,
    
    // Handlers
    handleResetFilter,
    toggleSelect,
    toggleExpandBox,
    handleCopyLink,
    handleOpenPublicLink,
    handlePrintLabels,
    handleOpenBoxDialog,
    handleSubmitBox,
    handleDeleteBox,
    handleOpenBoxActions,
    handleCloseDrawer,
    handleDrawerEdit,
    handleDrawerCopyLink,
    handleDrawerOpenLink,
    handleDrawerDelete,
  };
};

import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box as BoxModel, CreateBoxPayload } from '../types/models';
import { fetchBoxes, createBox, deleteBox } from '../services/boxService';
import { searchItems } from '../services/itemService';
import { useTranslation } from './useTranslation';

type SnackbarState = { open: boolean; message: string; severity: 'success' | 'error' | 'info' };

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
  const [isPrinting, setIsPrinting] = useState(false);
  
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
    () => {
      const filtered = filteredBoxes.filter((b) => selectedIds.includes(b.id));
      console.log('[DEBUG] selectedBoxes computed:', { 
        selectedIds, 
        filteredBoxesLength: filteredBoxes.length, 
        selectedBoxesLength: filtered.length,
        boxes: filtered.map(b => ({ id: b.id, currentRoom: b.currentRoom }))
      });
      return filtered;
    },
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
    const handleAfterPrint = () => {
      console.log('[DEBUG] Print dialog closed, resetting isPrinting');
      setIsPrinting(false);
    };
    window.addEventListener('afterprint', handleAfterPrint);
    return () => window.removeEventListener('afterprint', handleAfterPrint);
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
        .sort((a, b) => b.id - a.id)
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
        next = next.filter((b) => (b.currentRoom || '').toLowerCase().includes(roomTerm));
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
    console.log('[DEBUG] toggleSelect clicked for box:', id);
    setSelectedIds((prev) => {
      const newIds = prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id];
      console.log('[DEBUG] selectedIds updated:', { previous: prev, new: newIds, toggledId: id });
      return newIds;
    });
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

  const handlePrintLabels = () => {
    console.log('[DEBUG] handlePrintLabels called:', { selectedBoxesCount: selectedBoxes.length });
    
    if (selectedBoxes.length === 0) {
      console.warn('[DEBUG] No boxes selected for printing');
      setSnackbar({ open: true, message: t('boxes.noBoxSelected'), severity: 'info' });
      return;
    }
    
    setIsPrinting(true);
    setTimeout(() => {
      console.log('[DEBUG] ✓ Triggering print dialog with', selectedBoxes.length, 'boxes');
      window.print();
    }, 100);
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
    isPrinting,
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

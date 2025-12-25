import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  Collapse,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  GlobalStyles,
  IconButton,
  Paper,
  Snackbar,
  Stack,
  TextField,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import {
  Add,
  ExpandMore,
  ExpandLess,
  ContentCopy,
  Delete,
  Edit,
  Link as LinkIcon,
  Print,
  Search,
  OpenInNew,
  BrokenImage,
  DoNotDisturb,
} from '@mui/icons-material';
import { QRCodeCanvas } from 'qrcode.react';
import { Box as BoxModel, Item, CreateBoxPayload } from '../types/models';
import { fetchBoxes, createBox, deleteBox } from '../services/boxService';
import { searchItems } from '../services/itemService';
import { truncateToFirstLine } from '../utils/textUtils';

type SnackbarState = { open: boolean; message: string; severity: 'success' | 'error' | 'info' };

interface BoxHandlingBadgesProps {
  box: BoxModel;
}

const BoxHandlingBadges: React.FC<BoxHandlingBadgesProps> = ({ box }) => {
  if (!box.isFragile && !box.noStack) {
    return null;
  }

  return (
    <Box sx={{ display: 'flex', gap: 1, mt: 1, flexWrap: 'wrap' }}>
      {box.isFragile && (
        <Chip
          icon={<BrokenImage />}
          label="Zerbrechlich"
          color="warning"
          size="small"
        />
      )}
      {box.noStack && (
        <Chip
          icon={<DoNotDisturb />}
          label="Nicht stapeln"
          color="error"
          size="small"
        />
      )}
    </Box>
  );
};

export default function BoxList() {
  const navigate = useNavigate();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));

  const [allBoxes, setAllBoxes] = useState<BoxModel[]>([]);
  const [filteredBoxes, setFilteredBoxes] = useState<BoxModel[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [itemQuery, setItemQuery] = useState('');
  const [roomQuery, setRoomQuery] = useState('');
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [expandedBoxes, setExpandedBoxes] = useState<Set<number>>(new Set());
  const [snackbar, setSnackbar] = useState<SnackbarState>({ open: false, message: '', severity: 'success' });

  const [openBoxDialog, setOpenBoxDialog] = useState(false);
  const [boxFormData, setBoxFormData] = useState<CreateBoxPayload>({ 
    currentRoom: '', 
    targetRoom: '', 
    description: '',
    isFragile: false,
    noStack: false
  });

  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null);

  const [printOpen, setPrintOpen] = useState(false);

  const selectedBoxes = useMemo(
    () => filteredBoxes.filter((b) => selectedIds.includes(b.id)),
    [filteredBoxes, selectedIds]
  );

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setIsLoading(true);
    try {
      const boxes = await fetchBoxes();

      const withUrls = boxes.map((b) => ({
        ...b,
        publicUrl: `${window.location.origin}/public/${b.uuid}`,
      })) as BoxModel[];

      setAllBoxes(withUrls);
      setFilteredBoxes(withUrls);
      setSelectedIds([]);
    } catch (error) {
      setSnackbar({ open: true, message: 'Fehler beim Laden der Boxen', severity: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  const handleResetFilter = () => {
    setItemQuery('');
    setRoomQuery('');
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
        next = next.filter((b) => ids.has(b.id));
      }

      setFilteredBoxes(next);
      if (next.length === 0) {
        setSnackbar({ open: true, message: 'Keine Boxen zur Suche gefunden', severity: 'info' });
      }
    } catch (error) {
      setSnackbar({ open: true, message: 'Suche fehlgeschlagen', severity: 'error' });
    }
  };

  const toggleSelect = (id: number) => {
    setSelectedIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
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
      setSnackbar({ open: true, message: 'Kein Public Link verfügbar', severity: 'error' });
      return;
    }
    try {
      await navigator.clipboard.writeText(url);
      setSnackbar({ open: true, message: 'Public Link kopiert', severity: 'success' });
    } catch (error) {
      setSnackbar({ open: true, message: 'Kopieren nicht möglich', severity: 'error' });
    }
  };

  const handleOpenPublicLink = (url?: string) => {
    if (!url) {
      setSnackbar({ open: true, message: 'Kein Public Link verfügbar', severity: 'error' });
      return;
    }
    window.open(url, '_blank');
  };

  // Box Dialogs
  const handleOpenBoxDialog = () => {
    setBoxFormData({ currentRoom: '', targetRoom: '', description: '', isFragile: false, noStack: false });
    setOpenBoxDialog(true);
  };

  const handleSubmitBox = async () => {
    try {
      const newBox = await createBox(boxFormData as CreateBoxPayload);
      setSnackbar({ open: true, message: 'Box erstellt', severity: 'success' });
      setOpenBoxDialog(false);
      setBoxFormData({ currentRoom: '', targetRoom: '', description: '', isFragile: false, noStack: false });
      navigate(`/app/boxes/${newBox.id}/edit`);
    } catch (error) {
      setSnackbar({ open: true, message: 'Box konnte nicht erstellt werden', severity: 'error' });
    }
  };

  const handleDeleteBox = async () => {
    if (deleteConfirmId === null) return;
    try {
      await deleteBox(deleteConfirmId);
      setSnackbar({ open: true, message: 'Box gelöscht', severity: 'success' });
      setDeleteConfirmId(null);
      await loadData();
    } catch (error) {
      setSnackbar({ open: true, message: 'Box konnte nicht gelöscht werden', severity: 'error' });
    }
  };

  return (
    <Box sx={{ width: '100%', pb: 4 }}>
      <GlobalStyles
        styles={{
          '@page': { size: 'A4 portrait', margin: '10mm' },
          '@media print': {
            'body *': { visibility: 'hidden' },
            '.print-area, .print-area *': { visibility: 'visible' },
            '.print-area': { position: 'absolute', left: 0, top: 0, width: '100%' },
            '.print-label': {
              border: 'none',
              borderRadius: '0',
              padding: '8px 12px',
              height: '9.2cm',
              boxSizing: 'border-box',
              display: 'flex',
              flexDirection: 'row',
              justifyContent: 'flex-start',
              alignItems: 'center',
              pageBreakInside: 'avoid',
              borderBottom: '2px dashed #000',
            },
            '.print-label:last-child': {
              borderBottom: 'none',
            },
          },
        }}
      />

      <Stack spacing={2} sx={{ mb: 2 }}>
        <Paper sx={{ p: 2, borderRadius: 2, boxShadow: 2 }}>
          <Stack direction={isMobile ? 'column' : 'row'} spacing={2} alignItems={isMobile ? 'stretch' : 'center'}>
            <TextField
              fullWidth
              label="Item-Name"
              placeholder="z.B. Hammer"
              value={itemQuery}
              onChange={(e) => setItemQuery(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            />
            <TextField
              fullWidth
              label="Zielraum"
              placeholder="z.B. Keller"
              value={roomQuery}
              onChange={(e) => setRoomQuery(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            />
            <Stack direction="row" spacing={1} justifyContent={isMobile ? 'flex-end' : 'flex-start'}>
              <Button variant="contained" startIcon={<Search />} onClick={handleSearch}>
                Filtern
              </Button>
              <Button variant="text" onClick={handleResetFilter}>
                Zurücksetzen
              </Button>
            </Stack>
          </Stack>
        </Paper>

        <Stack direction={isMobile ? 'column' : 'row'} spacing={1.5} justifyContent="space-between" alignItems={isMobile ? 'stretch' : 'center'}>
          <Typography variant="h5" fontWeight={700}>Boxen</Typography>
          <Stack direction="row" spacing={1} justifyContent={isMobile ? 'flex-start' : 'flex-end'}>
            <Button
              variant="outlined"
              startIcon={<Print />}
              onClick={() => setPrintOpen(true)}
              disabled={selectedIds.length === 0}
            >
              Label drucken
            </Button>
            <Button variant="contained" startIcon={<Add />} onClick={handleOpenBoxDialog}>
              Neue Box
            </Button>
          </Stack>
        </Stack>
      </Stack>

      {isLoading ? (
        <Paper sx={{ p: 3, textAlign: 'center' }}>Lade Boxen...</Paper>
      ) : filteredBoxes.length === 0 ? (
        <Alert severity="info">Keine Boxen gefunden.</Alert>
      ) : (
        <Stack spacing={2}>
          {filteredBoxes.map((box) => (
            <Paper key={box.id} sx={{ borderRadius: 2, boxShadow: 2 }}>
              <Box sx={{ p: 2 }}>
                <Stack direction="row" spacing={2} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                  <Stack direction="row" spacing={2} alignItems="center" sx={{ flex: 1, minWidth: 200 }}>
                    <Checkbox
                      checked={selectedIds.includes(box.id)}
                      onChange={() => toggleSelect(box.id)}
                      inputProps={{ 'aria-label': `Box ${box.id} auswählen` }}
                    />
                    <Box sx={{ flex: 1 }}>
                      <Typography variant="h6">Box #{box.id}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        Aktuell: {box.currentRoom || '-'}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Ziel: {box.targetRoom || '-'}
                      </Typography>
                      {box.description && (
                        <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic', mt: 0.5 }}>
                          {truncateToFirstLine(box.description)}
                        </Typography>
                      )}
                      <BoxHandlingBadges box={box} />
                    </Box>
                  </Stack>

                  <Stack direction="row" spacing={0.5} flexWrap="wrap" justifyContent="flex-end">
                    <IconButton
                      size="small"
                      title="Bearbeiten"
                      onClick={() => navigate(`/app/boxes/${box.id}/edit`)}
                      color="primary"
                    >
                      <Edit fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      title="Public Link kopieren"
                      onClick={() => handleCopyLink(box.publicUrl)}
                    >
                      <ContentCopy fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      title="Public Link öffnen"
                      onClick={() => handleOpenPublicLink(box.publicUrl)}
                      color="info"
                    >
                      <OpenInNew fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      title="Löschen"
                      color="error"
                      onClick={() => setDeleteConfirmId(box.id)}
                    >
                      <Delete fontSize="small" />
                    </IconButton>
                  </Stack>
                </Stack>
              </Box>

              <Divider />

              <Box sx={{ p: 2 }}>
                <Stack
                  direction="row"
                  spacing={1}
                  alignItems="center"
                  onClick={() => toggleExpandBox(box.id)}
                  sx={{ cursor: 'pointer', userSelect: 'none' }}
                >
                  {expandedBoxes.has(box.id) ? <ExpandLess /> : <ExpandMore />}
                  <Typography variant="subtitle1" fontWeight={700}>
                    {box.items?.length || 0} Items
                  </Typography>
                </Stack>

                <Collapse in={expandedBoxes.has(box.id)} timeout="auto" unmountOnExit>
                  <Stack spacing={1.5} sx={{ mt: 2 }}>
                    {box.items && box.items.length > 0 ? (
                      box.items.map((item) => (
                        <Paper key={item.id} variant="outlined" sx={{ p: 1.25, borderRadius: 1.5 }}>
                          <Stack direction="row" alignItems="center" spacing={1.5}>
                            <Box sx={{ flex: 1, minWidth: 160 }}>
                              <Typography variant="body2" fontWeight={500}>
                                {item.name}
                              </Typography>
                            </Box>
                          </Stack>
                        </Paper>
                      ))
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        Keine Items in dieser Box.
                      </Typography>
                    )}
                  </Stack>
                </Collapse>
              </Box>
            </Paper>
          ))}
        </Stack>
      )}

      {/* Box Dialog - Create new box */}
      <Dialog open={openBoxDialog} onClose={() => setOpenBoxDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Neue Box erstellen</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Aktuelles Zimmer"
              fullWidth
              value={boxFormData.currentRoom || ''}
              onChange={(e) => setBoxFormData({ ...boxFormData, currentRoom: e.target.value })}
              placeholder="z.B. Wohnzimmer"
            />
            <TextField
              label="Zielzimmer"
              fullWidth
              value={boxFormData.targetRoom || ''}
              onChange={(e) => setBoxFormData({ ...boxFormData, targetRoom: e.target.value })}
              placeholder="z.B. Keller"
            />
            <TextField
              label="Beschreibung"
              fullWidth
              multiline
              rows={4}
              value={boxFormData.description || ''}
              onChange={(e) => setBoxFormData({ ...boxFormData, description: e.target.value })}
              placeholder="Optionale Beschreibung der Box..."
            />
            <Box sx={{ p: 2, backgroundColor: 'grey.100', borderRadius: 1 }}>
              <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1.5 }}>
                Transport-Hinweise
              </Typography>
              <FormControlLabel
                control={
                  <Checkbox
                    checked={boxFormData.isFragile || false}
                    onChange={(e) => setBoxFormData({ ...boxFormData, isFragile: e.target.checked })}
                    color="warning"
                  />
                }
                label="🔔 Zerbrechlich / Fragile"
              />
              <FormControlLabel
                control={
                  <Checkbox
                    checked={boxFormData.noStack || false}
                    onChange={(e) => setBoxFormData({ ...boxFormData, noStack: e.target.checked })}
                    color="error"
                  />
                }
                label="⛔ Nichts drauf stellen / Do Not Stack"
              />
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpenBoxDialog(false)}>Abbrechen</Button>
          <Button 
            variant="contained" 
            onClick={handleSubmitBox}
          >
            Erstellen
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog
        open={deleteConfirmId !== null}
        onClose={() => setDeleteConfirmId(null)}
      >
        <DialogTitle>Box löschen?</DialogTitle>
        <DialogContent>
          <Typography>Diese Box wird dauerhaft gelöscht. Dies kann nicht rückgängig gemacht werden.</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteConfirmId(null)}>Abbrechen</Button>
          <Button color="error" variant="contained" onClick={handleDeleteBox}>
            Löschen
          </Button>
        </DialogActions>
      </Dialog>

      {/* Label print dialog */}
      <Dialog open={printOpen} onClose={() => setPrintOpen(false)} maxWidth="lg" fullWidth>
        <DialogTitle>Label drucken</DialogTitle>
        <DialogContent>
          {selectedBoxes.length === 0 ? (
            <Alert severity="info">Keine Box ausgewählt.</Alert>
          ) : (
            <Box className="print-area" sx={{ display: 'grid', gap: 0 }}>
              {selectedBoxes.map((box) => (
                <Paper key={`print-${box.id}`} className="print-label" sx={{ borderColor: '#000', background: '#fff' }}>
                  <Box sx={{ display: 'grid', placeItems: 'center', flexShrink: 0, width: '9cm', height: '9cm' }}>
                    <QRCodeCanvas value={box.publicUrl || ''} size={240} includeMargin level="H" />
                  </Box>
                  <Stack direction="column" spacing={0.5} sx={{ flex: 1, justifyContent: 'flex-start', pl: 1.5 }}>
                    <Box sx={{ height: '5cm', display: 'flex', flexDirection: 'column', alignItems: 'flex-start', justifyContent: 'center' }}>
                      <Typography variant="h2" fontWeight={900} sx={{ lineHeight: 1, color: '#000', fontSize: '3rem' }}>
                        Box #{box.id}
                      </Typography>
                      <Typography variant="h2" fontWeight={900} sx={{ lineHeight: 1, color: '#000', fontSize: '3rem' }}>
                        {box.currentRoom || 'Zielraum'}
                      </Typography>
                    </Box>
                    {box.description && (
                      <Typography variant="body2" sx={{ fontSize: '0.75rem', fontStyle: 'italic', color: '#000', overflow: 'hidden', textOverflow: 'ellipsis', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical' }}>
                        {truncateToFirstLine(box.description)}
                      </Typography>
                    )}
                    
                    {(box.isFragile || box.noStack) && (
                      <Box sx={{ borderTop: '2px dashed #000', mt: 1.5, pt: 1.5, display: 'flex', gap: 2, alignItems: 'center' }}>
                        {box.isFragile && (
                          <Box sx={{ textAlign: 'center' }}>
                            <Typography sx={{ fontSize: '48px', lineHeight: 1 }}>🔔</Typography>
                            <Typography sx={{ fontSize: '0.7rem', fontWeight: 700, color: '#000', mt: 0.5 }}>FRAGILE</Typography>
                          </Box>
                        )}
                        {box.noStack && (
                          <Box sx={{ textAlign: 'center' }}>
                            <Typography sx={{ fontSize: '48px', lineHeight: 1 }}>⛔</Typography>
                            <Typography sx={{ fontSize: '0.7rem', fontWeight: 700, color: '#000', mt: 0.5 }}>DO NOT STACK</Typography>
                          </Box>
                        )}
                      </Box>
                    )}
                  </Stack>
                </Paper>
              ))}
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPrintOpen(false)}>Schließen</Button>
          <Button variant="contained" startIcon={<Print />} onClick={() => window.print()} disabled={selectedBoxes.length === 0}>
            Drucken
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={4000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
      >
        <Alert severity={snackbar.severity} onClose={() => setSnackbar({ ...snackbar, open: false })}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
}

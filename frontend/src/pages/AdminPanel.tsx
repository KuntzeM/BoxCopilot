import React, { useState, useEffect } from 'react';
import {
  Box,
  Paper,
  Typography,
  Tabs,
  Tab,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  IconButton,
  Chip,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Alert,
  Snackbar,
  Switch,
  FormControlLabel,
  Fab,
  useMediaQuery,
  useTheme,
  Card,
  CardContent,
  Stack,
  Divider,
  Collapse,
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Lock as LockIcon,
  LockOpen as LockOpenIcon,
  AdminPanelSettings as AdminIcon,
  Person as PersonIcon,
  Link as LinkIcon,
  FilterList as FilterIcon,
  ExpandMore,
  ExpandLess,
} from '@mui/icons-material';
import { useTranslation } from '../hooks/useTranslation';
import { userService } from '../services/userService';
import { User, CreateUserPayload, UpdateUserPayload, Role, AuthProvider } from '../types/models';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`admin-tabpanel-${index}`}
      aria-labelledby={`admin-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ py: 3 }}>{children}</Box>}
    </div>
  );
}

export default function AdminPanel() {
  const { t } = useTranslation();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  
  const [tabValue, setTabValue] = useState(0);
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [showFilters, setShowFilters] = useState(false);

  // Filters
  const [roleFilter, setRoleFilter] = useState<Role | 'all'>('all');
  const [authTypeFilter, setAuthTypeFilter] = useState<AuthProvider | 'all'>('all');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'inactive' | 'locked'>('all');

  // Dialogs
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [passwordDialogOpen, setPasswordDialogOpen] = useState(false);
  const [expiryConfigDialogOpen, setExpiryConfigDialogOpen] = useState(false);
  const [magicLinkDialogOpen, setMagicLinkDialogOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [userForMagicLink, setUserForMagicLink] = useState<User | null>(null);
  const [selectedExpiry, setSelectedExpiry] = useState<number>(24);

  // Form state
  const [formData, setFormData] = useState<Partial<CreateUserPayload | UpdateUserPayload>>({});
  const [newPassword, setNewPassword] = useState('');
  const [magicLinkData, setMagicLinkData] = useState<{ url: string; expiresAt: string } | null>(null);

  useEffect(() => {
    loadUsers();
  }, []);

  const loadUsers = async () => {
    try {
      setLoading(true);
      const filters: any = {};
      if (roleFilter !== 'all') filters.role = roleFilter;
      if (authTypeFilter !== 'all') filters.authProvider = authTypeFilter;
      if (statusFilter === 'active') filters.enabled = true;
      if (statusFilter === 'inactive') filters.enabled = false;

      const data = await userService.getUsers(filters);
      setUsers(data);
      setError(null);
    } catch (err: any) {
      setError(err.message || 'Failed to load users');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadUsers();
  }, [roleFilter, authTypeFilter, statusFilter]);

  const handleCreateUser = async () => {
    try {
      await userService.createUser(formData as CreateUserPayload);
      setSuccessMessage(t('admin.userCreated'));
      setCreateDialogOpen(false);
      setFormData({});
      loadUsers();
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to create user');
    }
  };

  const handleUpdateUser = async () => {
    if (!selectedUser) return;
    try {
      await userService.updateUser(selectedUser.id, formData as UpdateUserPayload);
      setSuccessMessage(t('admin.userUpdated'));
      setEditDialogOpen(false);
      setFormData({});
      setSelectedUser(null);
      loadUsers();
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to update user');
    }
  };

  const handleDeleteUser = async () => {
    if (!selectedUser) return;
    try {
      await userService.deleteUser(selectedUser.id);
      setSuccessMessage(t('admin.userDeleted'));
      setDeleteDialogOpen(false);
      setSelectedUser(null);
      loadUsers();
    } catch (err: any) {
      if (err.response?.status === 400) {
        setError(t('admin.cannotDeleteSelf'));
      } else {
        setError(err.response?.data?.message || err.message || 'Failed to delete user');
      }
    }
  };

  const handleSetPassword = async () => {
    if (!selectedUser) return;
    try {
      await userService.setPassword(selectedUser.id, { password: newPassword });
      setSuccessMessage(t('admin.passwordSet'));
      setPasswordDialogOpen(false);
      setNewPassword('');
      setSelectedUser(null);
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to set password');
    }
  };

  const handleUnlockUser = async (user: User) => {
    try {
      await userService.unlockUser(user.id);
      setSuccessMessage(t('admin.userUnlocked'));
      loadUsers();
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to unlock user');
    }
  };

  const handleToggleAdmin = async (user: User) => {
    try {
      const newRole = user.role === Role.ADMIN ? Role.USER : Role.ADMIN;
      await userService.updateUser(user.id, { role: newRole });
      setSuccessMessage(t('admin.userUpdated'));
      loadUsers();
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to update role');
    }
  };

  const handleGenerateMagicLink = (user: User) => {
    setUserForMagicLink(user);
    setSelectedExpiry(24); // Reset to default
    setExpiryConfigDialogOpen(true);
  };

  const handleConfirmGeneration = async () => {
    if (!userForMagicLink) return;
    try {
      const response = await userService.createMagicLink(userForMagicLink.id, { expiresInHours: selectedExpiry });
      setMagicLinkData({
        url: response.url,
        expiresAt: response.expiresAt,
      });
      setSelectedUser(userForMagicLink);
      setExpiryConfigDialogOpen(false);
      setMagicLinkDialogOpen(true);
      setSuccessMessage(t('admin.magicLinkGenerated'));
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to generate magic link');
      // Keep config dialog open on error
    }
  };

  const handleCopyMagicLink = () => {
    if (magicLinkData) {
      navigator.clipboard.writeText(magicLinkData.url);
      setSuccessMessage(t('admin.magicLinkCopied'));
    }
  };

  const openEditDialog = (user: User) => {
    setSelectedUser(user);
    setFormData({
      username: user.username,
      name: user.name,
      role: user.role,
      enabled: user.enabled,
    });
    setEditDialogOpen(true);
  };

  const openDeleteDialog = (user: User) => {
    setSelectedUser(user);
    setDeleteDialogOpen(true);
  };

  const openPasswordDialog = (user: User) => {
    setSelectedUser(user);
    setPasswordDialogOpen(true);
  };

  const openCreateDialog = () => {
    setFormData({
      username: '',
      name: '',
      password: '',
      role: Role.USER,
      enabled: true,
    });
    setCreateDialogOpen(true);
  };

  const getStatusChip = (user: User) => {
    if (user.lockedUntil && new Date(user.lockedUntil) > new Date()) {
      return <Chip label={t('admin.locked')} color="error" size="small" />;
    }
    if (!user.enabled) {
      return <Chip label={t('admin.inactive')} color="warning" size="small" />;
    }
    return <Chip label={t('admin.active')} color="success" size="small" />;
  };

  const isUserLocked = (user: User) => {
    return user.lockedUntil && new Date(user.lockedUntil) > new Date();
  };

  const renderUserCard = (user: User) => (
    <Card key={user.id} sx={{ mb: 2 }}>
      <CardContent>
        <Stack spacing={2}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <Box>
              <Typography variant="h6">{user.name}</Typography>
              <Typography variant="body2" color="text.secondary">@{user.username}</Typography>
            </Box>
            {getStatusChip(user)}
          </Box>

          <Stack direction="row" spacing={1} flexWrap="wrap">
            <Chip
              label={user.role === Role.ADMIN ? t('admin.admin') : t('admin.user')}
              color={user.role === Role.ADMIN ? 'primary' : 'default'}
              size="small"
              icon={user.role === Role.ADMIN ? <AdminIcon /> : <PersonIcon />}
            />
            <Chip
              label={user.authProvider === AuthProvider.NEXTCLOUD ? t('admin.nextcloud') : t('admin.local')}
              variant="outlined"
              size="small"
            />
            {user.authProvider === AuthProvider.LOCAL && !user.hasPassword && (
              <Chip
                label={t('admin.passwordless')}
                color="warning"
                variant="outlined"
                size="small"
              />
            )}
          </Stack>

          <Divider />

          <Stack direction="row" spacing={1} flexWrap="wrap">
            <Button
              size="small"
              variant="outlined"
              startIcon={<EditIcon />}
              onClick={() => openEditDialog(user)}
              fullWidth={isMobile}
              sx={{ minHeight: 44 }}
            >
              {t('admin.editUser')}
            </Button>
            {isUserLocked(user) && (
              <Button
                size="small"
                variant="outlined"
                startIcon={<LockOpenIcon />}
                onClick={() => handleUnlockUser(user)}
                fullWidth={isMobile}
                sx={{ minHeight: 44 }}
              >
                {t('admin.unlock')}
              </Button>
            )}
            {user.authProvider === AuthProvider.LOCAL && (
              <Button
                size="small"
                variant="outlined"
                startIcon={<LockIcon />}
                onClick={() => openPasswordDialog(user)}
                fullWidth={isMobile}
                sx={{ minHeight: 44 }}
              >
                {t('admin.setPassword')}
              </Button>
            )}
            <Button
              size="small"
              variant="outlined"
              startIcon={<LinkIcon />}
              onClick={() => handleGenerateMagicLink(user)}
              fullWidth={isMobile}
              sx={{ minHeight: 44 }}
            >
              Magic Link
            </Button>
            <Button
              size="small"
              variant="outlined"
              color="error"
              startIcon={<DeleteIcon />}
              onClick={() => openDeleteDialog(user)}
              fullWidth={isMobile}
              sx={{ minHeight: 44 }}
            >
              {t('admin.deleteUser')}
            </Button>
          </Stack>

          <Box>
            <Typography variant="caption" color="text.secondary">
              Erstellt: {new Date(user.createdAt).toLocaleDateString()}
            </Typography>
            {user.lastLogin && (
              <Typography variant="caption" color="text.secondary" sx={{ ml: 2 }}>
                Login: {new Date(user.lastLogin).toLocaleDateString()}
              </Typography>
            )}
          </Box>
        </Stack>
      </CardContent>
    </Card>
  );

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4">{t('admin.title')}</Typography>
      </Box>

      <Paper sx={{ width: '100%' }}>
        <Tabs value={tabValue} onChange={(_, newValue) => setTabValue(newValue)}>
          <Tab label={t('admin.users')} />
          <Tab label={t('admin.settings')} />
        </Tabs>

        <TabPanel value={tabValue} index={0}>
          {/* User Management Tab */}
          <Box sx={{ mb: 3 }}>
            {/* Filters Section */}
            <Button
              variant="outlined"
              startIcon={showFilters ? <ExpandLess /> : <ExpandMore />}
              onClick={() => setShowFilters(!showFilters)}
              sx={{ mb: 2, minHeight: 44 }}
              fullWidth={isMobile}
            >
              <FilterIcon sx={{ mr: 1 }} />
              Filter {showFilters ? 'ausblenden' : 'anzeigen'}
            </Button>

            <Collapse in={showFilters}>
              <Stack 
                direction={isMobile ? 'column' : 'row'} 
                spacing={2} 
                sx={{ mb: 3 }}
              >
                <FormControl fullWidth>
                  <InputLabel>{t('admin.filterRole')}</InputLabel>
                  <Select
                    value={roleFilter}
                    label={t('admin.filterRole')}
                    onChange={(e) => setRoleFilter(e.target.value as Role | 'all')}
                  >
                    <MenuItem value="all">{t('admin.all')}</MenuItem>
                    <MenuItem value={Role.USER}>{t('admin.user')}</MenuItem>
                    <MenuItem value={Role.ADMIN}>{t('admin.admin')}</MenuItem>
                  </Select>
                </FormControl>

                <FormControl fullWidth>
                  <InputLabel>{t('admin.filterAuthType')}</InputLabel>
                  <Select
                    value={authTypeFilter}
                    label={t('admin.filterAuthType')}
                    onChange={(e) => setAuthTypeFilter(e.target.value as AuthProvider | 'all')}
                  >
                    <MenuItem value="all">{t('admin.all')}</MenuItem>
                    <MenuItem value={AuthProvider.LOCAL}>{t('admin.local')}</MenuItem>
                    <MenuItem value={AuthProvider.NEXTCLOUD}>{t('admin.nextcloud')}</MenuItem>
                  </Select>
                </FormControl>

                <FormControl fullWidth>
                  <InputLabel>{t('admin.filterStatus')}</InputLabel>
                  <Select
                    value={statusFilter}
                    label={t('admin.filterStatus')}
                    onChange={(e) => setStatusFilter(e.target.value as any)}
                  >
                    <MenuItem value="all">{t('admin.all')}</MenuItem>
                    <MenuItem value="active">{t('admin.active')}</MenuItem>
                    <MenuItem value="inactive">{t('admin.inactive')}</MenuItem>
                    <MenuItem value="locked">{t('admin.locked')}</MenuItem>
                  </Select>
                </FormControl>
              </Stack>
            </Collapse>

            {!isMobile && (
              <Button 
                variant="contained" 
                startIcon={<AddIcon />} 
                onClick={openCreateDialog}
                sx={{ mb: 2, minHeight: 44 }}
              >
                {t('admin.createUser')}
              </Button>
            )}
          </Box>

          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

          {/* Desktop: Table View */}
          {!isMobile && (
            <TableContainer component={Paper}>
              <Table>
              <TableHead>
                <TableRow>
                  <TableCell>{t('admin.username')}</TableCell>
                  <TableCell>{t('admin.name')}</TableCell>
                  <TableCell>{t('admin.role')}</TableCell>
                  <TableCell>{t('admin.status')}</TableCell>
                  <TableCell>{t('admin.authType')}</TableCell>
                  <TableCell>{t('admin.createdAt')}</TableCell>
                  <TableCell>{t('admin.lastLogin')}</TableCell>
                  <TableCell>{t('admin.failedAttempts')}</TableCell>
                  <TableCell>Magic Link</TableCell>
                  <TableCell>{t('admin.actions')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {loading ? (
                  <TableRow>
                    <TableCell colSpan={9} align="center">
                      {t('app.loading')}
                    </TableCell>
                  </TableRow>
                ) : users.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={9} align="center">
                      No users found
                    </TableCell>
                  </TableRow>
                ) : (
                  users.map((user) => (
                    <TableRow key={user.id}>
                      <TableCell>{user.username}</TableCell>
                      <TableCell>{user.name}</TableCell>
                      <TableCell>
                        <Chip
                          label={user.role === Role.ADMIN ? t('admin.admin') : t('admin.user')}
                          color={user.role === Role.ADMIN ? 'primary' : 'default'}
                          size="small"
                          icon={user.role === Role.ADMIN ? <AdminIcon /> : <PersonIcon />}
                        />
                      </TableCell>
                      <TableCell>{getStatusChip(user)}</TableCell>
                      <TableCell>
                        <Chip
                          label={user.authProvider === AuthProvider.NEXTCLOUD ? t('admin.nextcloud') : t('admin.local')}
                          variant="outlined"
                          size="small"
                        />
                        {user.authProvider === AuthProvider.LOCAL && !user.hasPassword && (
                          <Chip
                            label={t('admin.passwordless')}
                            color="warning"
                            variant="outlined"
                            size="small"
                            sx={{ ml: 1 }}
                          />
                        )}
                      </TableCell>
                      <TableCell>{new Date(user.createdAt).toLocaleDateString()}</TableCell>
                      <TableCell>
                        {user.lastLogin ? new Date(user.lastLogin).toLocaleDateString() : '-'}
                      </TableCell>
                      <TableCell>{user.failedLoginAttempts}</TableCell>
                      <TableCell>
                        {user.lastMagicLinkCreatedAt ? (
                          <Chip
                            label={user.lastMagicLinkUsed ? t('admin.magicLinkUsed') ?? 'Used' : t('admin.magicLinkActive') ?? 'Open'}
                            color={user.lastMagicLinkUsed ? 'default' : 'success'}
                            size="small"
                          />
                        ) : (
                          '-' 
                        )}
                      </TableCell>
                      <TableCell>
                        <IconButton size="small" onClick={() => openEditDialog(user)} title={t('admin.editUser')}>
                          <EditIcon fontSize="small" />
                        </IconButton>
                        <IconButton size="small" onClick={() => openDeleteDialog(user)} title={t('admin.deleteUser')}>
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                        {isUserLocked(user) && (
                          <IconButton size="small" onClick={() => handleUnlockUser(user)} title={t('admin.unlock')}>
                            <LockOpenIcon fontSize="small" />
                          </IconButton>
                        )}
                        {user.authProvider === AuthProvider.LOCAL && (
                          <IconButton
                            size="small"
                            onClick={() => openPasswordDialog(user)}
                            title={t('admin.setPassword')}
                          >
                            <LockIcon fontSize="small" />
                          </IconButton>
                        )}
                        <IconButton
                          size="small"
                          onClick={() => handleGenerateMagicLink(user)}
                          title={t('admin.generateMagicLink')}
                        >
                          <LinkIcon fontSize="small" />
                        </IconButton>
                        <IconButton
                          size="small"
                          onClick={() => handleToggleAdmin(user)}
                          title={user.role === Role.ADMIN ? t('admin.removeAdmin') : t('admin.makeAdmin')}
                        >
                          <AdminIcon fontSize="small" color={user.role === Role.ADMIN ? 'primary' : 'inherit'} />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </TableContainer>
          )}

          {/* Mobile: Card View */}
          {isMobile && (
            <Box>
              {loading ? (
                <Box sx={{ textAlign: 'center', py: 4 }}>
                  <Typography>{t('app.loading')}</Typography>
                </Box>
              ) : users.length === 0 ? (
                <Alert severity="info">No users found</Alert>
              ) : (
                users.map(renderUserCard)
              )}
            </Box>
          )}
        </TabPanel>

        <TabPanel value={tabValue} index={1}>
          {/* Settings Tab - Placeholder */}
          <Typography variant="body1" color="text.secondary">
            {t('admin.settings')} - Coming soon
          </Typography>
        </TabPanel>
      </Paper>

      {/* Create User Dialog */}
      <Dialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('admin.createUser')}</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            margin="normal"
            label={t('admin.username')}
            value={formData.username || ''}
            onChange={(e) => setFormData({ ...formData, username: e.target.value })}
            required
          />
          <TextField
            fullWidth
            margin="normal"
            label={t('admin.name')}
            value={formData.name || ''}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            required
          />
          <TextField
            fullWidth
            margin="normal"
            label={t('admin.passwordOptional')}
            type="password"
            value={(formData as CreateUserPayload).password || ''}
            onChange={(e) => setFormData({ ...formData, password: e.target.value })}
            helperText={t('admin.passwordless')}
          />
          <FormControl fullWidth margin="normal">
            <InputLabel>{t('admin.role')}</InputLabel>
            <Select
              value={formData.role || Role.USER}
              label={t('admin.role')}
              onChange={(e) => setFormData({ ...formData, role: e.target.value as Role })}
            >
              <MenuItem value={Role.USER}>{t('admin.user')}</MenuItem>
              <MenuItem value={Role.ADMIN}>{t('admin.admin')}</MenuItem>
            </Select>
          </FormControl>
          <FormControlLabel
            control={
              <Switch
                checked={formData.enabled !== false}
                onChange={(e) => setFormData({ ...formData, enabled: e.target.checked })}
              />
            }
            label={t('admin.enabled')}
          />
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button 
            onClick={() => setCreateDialogOpen(false)}
            fullWidth
            size="large"
            variant="outlined"
            sx={{ minHeight: 56 }}
          >
            {t('admin.cancel')}
          </Button>
          <Button 
            onClick={handleCreateUser} 
            variant="contained"
            fullWidth
            size="large"
            sx={{ minHeight: 56 }}
          >
            {t('admin.save')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Edit User Dialog */}
      <Dialog open={editDialogOpen} onClose={() => setEditDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('admin.editUser')}</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            margin="normal"
            label={t('admin.username')}
            value={formData.username || ''}
            onChange={(e) => setFormData({ ...formData, username: e.target.value })}
            disabled={selectedUser?.authProvider === AuthProvider.NEXTCLOUD}
          />
          <TextField
            fullWidth
            margin="normal"
            label={t('admin.name')}
            value={formData.name || ''}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
          />
          <FormControl fullWidth margin="normal">
            <InputLabel>{t('admin.role')}</InputLabel>
            <Select
              value={formData.role || Role.USER}
              label={t('admin.role')}
              onChange={(e) => setFormData({ ...formData, role: e.target.value as Role })}
            >
              <MenuItem value={Role.USER}>{t('admin.user')}</MenuItem>
              <MenuItem value={Role.ADMIN}>{t('admin.admin')}</MenuItem>
            </Select>
          </FormControl>
          <FormControlLabel
            control={
              <Switch
                checked={formData.enabled !== false}
                onChange={(e) => setFormData({ ...formData, enabled: e.target.checked })}
              />
            }
            label={t('admin.enabled')}
          />
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button 
            onClick={() => setEditDialogOpen(false)}
            fullWidth
            size="large"
            variant="outlined"
            sx={{ minHeight: 56 }}
          >
            {t('admin.cancel')}
          </Button>
          <Button 
            onClick={handleUpdateUser} 
            variant="contained"
            fullWidth
            size="large"
            sx={{ minHeight: 56 }}
          >
            {t('admin.save')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete User Dialog */}
      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)}>
        <DialogTitle>{t('admin.deleteUser')}</DialogTitle>
        <DialogContent>
          <Typography>
            {t('admin.deleteConfirm', { username: selectedUser?.username })}
          </Typography>
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button 
            onClick={() => setDeleteDialogOpen(false)}
            fullWidth
            size="large"
            variant="outlined"
            sx={{ minHeight: 56 }}
          >
            {t('admin.cancel')}
          </Button>
          <Button 
            onClick={handleDeleteUser} 
            variant="contained" 
            color="error"
            fullWidth
            size="large"
            sx={{ minHeight: 56 }}
          >
            {t('boxes.delete')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Set Password Dialog */}
      <Dialog open={passwordDialogOpen} onClose={() => setPasswordDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('admin.setPassword')}</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            margin="normal"
            label={t('admin.password')}
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            required
          />
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button 
            onClick={() => setPasswordDialogOpen(false)}
            fullWidth
            size="large"
            variant="outlined"
            sx={{ minHeight: 56 }}
          >
            {t('admin.cancel')}
          </Button>
          <Button 
            onClick={handleSetPassword} 
            variant="contained"
            fullWidth
            size="large"
            sx={{ minHeight: 56 }}
          >
            {t('admin.save')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Expiry Configuration Dialog */}
      <Dialog 
        open={expiryConfigDialogOpen} 
        onClose={() => setExpiryConfigDialogOpen(false)}
        maxWidth="sm" 
        fullWidth
      >
        <DialogTitle>{t('admin.configMagicLinkTitle')}</DialogTitle>
        <DialogContent>
          <FormControl fullWidth margin="normal" sx={{ mt: 2 }}>
            <InputLabel>{t('admin.selectExpiryLabel')}</InputLabel>
            <Select
              value={selectedExpiry}
              label={t('admin.selectExpiryLabel')}
              onChange={(e) => setSelectedExpiry(e.target.value as number)}
            >
              <MenuItem value={1}>{t('admin.expiry1h')}</MenuItem>
              <MenuItem value={4}>{t('admin.expiry4h')}</MenuItem>
              <MenuItem value={8}>{t('admin.expiry8h')}</MenuItem>
              <MenuItem value={24}>{t('admin.expiry24h')}</MenuItem>
              <MenuItem value={168}>{t('admin.expiry1week')}</MenuItem>
              <MenuItem value={744}>{t('admin.expiry1month')}</MenuItem>
            </Select>
          </FormControl>
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button 
            onClick={() => setExpiryConfigDialogOpen(false)}
            fullWidth
            size="large"
            variant="outlined"
            sx={{ minHeight: 56 }}
          >
            {t('admin.cancel')}
          </Button>
          <Button 
            onClick={handleConfirmGeneration} 
            variant="contained"
            fullWidth
            size="large"
            sx={{ minHeight: 56 }}
          >
            {t('admin.generate')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Magic Link Dialog */}
      <Dialog 
        open={magicLinkDialogOpen} 
        onClose={() => {
          setMagicLinkDialogOpen(false);
          setMagicLinkData(null);
        }} 
        maxWidth="md" 
        fullWidth
      >
        <DialogTitle>{t('admin.magicLinkTitle')}</DialogTitle>
        <DialogContent>
          {magicLinkData && (
            <Box sx={{ mt: 2 }}>
              <TextField
                fullWidth
                label={t('admin.magicLinkUrl')}
                value={magicLinkData.url}
                InputProps={{
                  readOnly: true,
                }}
                margin="normal"
                multiline
                rows={3}
              />
              <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                {t('admin.magicLinkExpiry')}: {new Date(magicLinkData.expiresAt).toLocaleString()}
              </Typography>
            </Box>
          )}
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button 
            onClick={() => {
              setMagicLinkDialogOpen(false);
              setMagicLinkData(null);
            }}
            fullWidth
            size="large"
            variant="outlined"
            sx={{ minHeight: 56 }}
          >
            {t('admin.magicLinkClose')}
          </Button>
          <Button 
            onClick={handleCopyMagicLink} 
            variant="contained" 
            startIcon={<LinkIcon />}
            fullWidth
            size="large"
            sx={{ minHeight: 56 }}
          >
            {t('admin.magicLinkCopy')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Success Snackbar */}
      <Snackbar
        open={!!successMessage}
        autoHideDuration={3000}
        onClose={() => setSuccessMessage(null)}
        message={successMessage}
      />

      {/* FAB for Creating User (Mobile) */}
      {isMobile && (
        <Fab
          color="primary"
          aria-label={t('admin.createUser')}
          onClick={openCreateDialog}
          sx={{
            position: 'fixed',
            bottom: 80,
            right: 16,
            zIndex: 1000,
          }}
        >
          <AddIcon />
        </Fab>
      )}
    </Box>
  );
}

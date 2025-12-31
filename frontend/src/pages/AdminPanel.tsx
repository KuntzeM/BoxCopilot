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
  const [tabValue, setTabValue] = useState(0);
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Filters
  const [roleFilter, setRoleFilter] = useState<Role | 'all'>('all');
  const [authTypeFilter, setAuthTypeFilter] = useState<AuthProvider | 'all'>('all');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'inactive' | 'locked'>('all');

  // Dialogs
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [passwordDialogOpen, setPasswordDialogOpen] = useState(false);
  const [magicLinkDialogOpen, setMagicLinkDialogOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState<User | null>(null);

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

  const handleGenerateMagicLink = async (user: User) => {
    try {
      const response = await userService.createMagicLink(user.id);
      setMagicLinkData({
        url: response.url,
        expiresAt: response.expiresAt,
      });
      setSelectedUser(user);
      setMagicLinkDialogOpen(true);
      setSuccessMessage(t('admin.magicLinkGenerated'));
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to generate magic link');
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
          <Box sx={{ mb: 3, display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap' }}>
            <FormControl sx={{ minWidth: 150 }}>
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

            <FormControl sx={{ minWidth: 150 }}>
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

            <FormControl sx={{ minWidth: 150 }}>
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

            <Box sx={{ flexGrow: 1 }} />

            <Button variant="contained" startIcon={<AddIcon />} onClick={openCreateDialog}>
              {t('admin.createUser')}
            </Button>
          </Box>

          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

          <TableContainer>
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
        <DialogActions>
          <Button onClick={() => setCreateDialogOpen(false)}>{t('admin.cancel')}</Button>
          <Button onClick={handleCreateUser} variant="contained">
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
        <DialogActions>
          <Button onClick={() => setEditDialogOpen(false)}>{t('admin.cancel')}</Button>
          <Button onClick={handleUpdateUser} variant="contained">
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
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>{t('admin.cancel')}</Button>
          <Button onClick={handleDeleteUser} variant="contained" color="error">
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
        <DialogActions>
          <Button onClick={() => setPasswordDialogOpen(false)}>{t('admin.cancel')}</Button>
          <Button onClick={handleSetPassword} variant="contained">
            {t('admin.save')}
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
        <DialogActions>
          <Button onClick={() => {
            setMagicLinkDialogOpen(false);
            setMagicLinkData(null);
          }}>
            {t('admin.magicLinkClose')}
          </Button>
          <Button onClick={handleCopyMagicLink} variant="contained" startIcon={<LinkIcon />}>
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
    </Box>
  );
}

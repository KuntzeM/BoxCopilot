import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { LanguageProvider } from '../context/LanguageContext';
import { userService } from '../services/userService';
import { AuthProvider, Role, User } from '../types/models';
import AdminPanel from './AdminPanel';

vi.mock('../services/userService', () => ({
  userService: {
    getUsers: vi.fn(),
    updateUser: vi.fn(),
  },
}));

function buildUser(overrides: Partial<User> = {}): User {
  return {
    id: 7,
    username: 'alice',
    name: 'Alice',
    authProvider: AuthProvider.LOCAL,
    role: Role.USER,
    enabled: true,
    createdAt: '2026-01-01T10:00:00',
    failedLoginAttempts: 0,
    hasPassword: true,
    mcpAllowed: false,
    ...overrides,
  };
}

async function openEditDialogFor(user: User) {
  vi.mocked(userService.getUsers).mockResolvedValue([user]);
  render(
    <LanguageProvider>
      <AdminPanel />
    </LanguageProvider>,
  );
  fireEvent.click(await screen.findByTitle('Edit User'));
  return screen.findByRole('checkbox', { name: 'MCP' });
}

describe('AdminPanel MCP switch', () => {
  beforeEach(() => {
    vi.mocked(userService.getUsers).mockReset();
    vi.mocked(userService.updateUser).mockReset();
    document.cookie = 'app-language=en';
  });

  afterEach(() => {
    cleanup();
  });

  it('reflects the stored mcpAllowed value of the user', async () => {
    const off = await openEditDialogFor(buildUser({ mcpAllowed: false }));
    expect((off as HTMLInputElement).checked).toBe(false);

    cleanup();

    const on = await openEditDialogFor(buildUser({ mcpAllowed: true }));
    expect((on as HTMLInputElement).checked).toBe(true);
  });

  it('sends mcpAllowed=true when the admin switches MCP on and saves', async () => {
    const user = buildUser({ mcpAllowed: false });
    vi.mocked(userService.updateUser).mockResolvedValue({ ...user, mcpAllowed: true });
    const toggle = await openEditDialogFor(user);

    fireEvent.click(toggle);
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(userService.updateUser).toHaveBeenCalledTimes(1));
    expect(userService.updateUser).toHaveBeenCalledWith(
      7,
      expect.objectContaining({ mcpAllowed: true, enabled: true, role: Role.USER }),
    );
  });

  it('sends mcpAllowed=false when the admin revokes MCP and saves', async () => {
    const user = buildUser({ mcpAllowed: true });
    vi.mocked(userService.updateUser).mockResolvedValue({ ...user, mcpAllowed: false });
    const toggle = await openEditDialogFor(user);

    fireEvent.click(toggle);
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(userService.updateUser).toHaveBeenCalledTimes(1));
    expect(userService.updateUser).toHaveBeenCalledWith(7, expect.objectContaining({ mcpAllowed: false }));
  });
});

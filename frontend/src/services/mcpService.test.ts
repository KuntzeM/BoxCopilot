import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import axios from './axiosConfig';
import { getMcpConnectorUrl, getMcpContinueUrl, mcpService } from './mcpService';

vi.mock('./axiosConfig', () => ({
  default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() },
}));

describe('mcpService', () => {
  beforeEach(() => {
    vi.mocked(axios.post).mockReset();
    vi.mocked(axios.delete).mockReset();
  });

  it('gets the client via POST and returns the client info', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: { clientId: 'client-1' } });

    await expect(mcpService.getClient()).resolves.toEqual({ clientId: 'client-1' });

    expect(axios.post).toHaveBeenCalledWith('/api/v1/me/mcp-client');
  });

  it('regenerates the client and returns the new client info', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: { clientId: 'client-2' } });

    await expect(mcpService.regenerateClient()).resolves.toEqual({ clientId: 'client-2' });

    expect(axios.post).toHaveBeenCalledWith('/api/v1/me/mcp-client/regenerate');
  });

  it('revokes all connections via DELETE', async () => {
    vi.mocked(axios.delete).mockResolvedValue({ status: 204 });

    await expect(mcpService.revokeConnections()).resolves.toBeUndefined();

    expect(axios.delete).toHaveBeenCalledWith('/api/v1/me/mcp-connections');
  });

  it.each([
    ['getClient', () => mcpService.getClient()],
    ['regenerateClient', () => mcpService.regenerateClient()],
  ])('propagates a failing request of %s', async (_name, call) => {
    vi.mocked(axios.post).mockRejectedValue(new Error('403'));

    await expect(call()).rejects.toThrow('403');
  });

  it('propagates a failing revoke request', async () => {
    vi.mocked(axios.delete).mockRejectedValue(new Error('404'));

    await expect(mcpService.revokeConnections()).rejects.toThrow('404');
  });
});

describe('MCP URLs', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('derives the continue and connector URLs from the API base URL', () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.example.com/');

    expect(getMcpContinueUrl()).toBe('https://api.example.com/api/v1/mcp/continue');
    expect(getMcpConnectorUrl()).toBe('https://api.example.com/mcp');
  });

  it('uses a relative continue URL and no connector URL without a configured base URL', () => {
    vi.stubEnv('VITE_API_BASE_URL', '');

    expect(getMcpContinueUrl()).toBe('/api/v1/mcp/continue');
    expect(getMcpConnectorUrl()).toBeNull();
  });
});

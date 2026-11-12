import axios from './axiosConfig';
import { McpClientInfo } from '../types/models';

/**
 * Backend base URL as used by axios, without a trailing slash. Empty when the SPA talks to a same-origin backend.
 */
function getApiBaseUrl(): string {
  return (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');
}

/**
 * URL that resumes a pending MCP authorization after login. It must be opened as a top-level navigation.
 */
export function getMcpContinueUrl(): string {
  return `${getApiBaseUrl()}/api/v1/mcp/continue`;
}

/**
 * URL of the MCP endpoint to enter in the Claude connector, or null when the backend base URL is not configured.
 */
export function getMcpConnectorUrl(): string | null {
  const base = getApiBaseUrl();
  return base ? `${base}/mcp` : null;
}

/**
 * Service for the "Claude connection" of the current user.
 * Requests are session and CSRF protected; the CSRF header is set globally after the auth check.
 */
export const mcpService = {
  /**
   * Get the OAuth client ID of the current user; the backend creates it lazily and idempotently
   */
  async getClient(): Promise<McpClientInfo> {
    const response = await axios.post<McpClientInfo>('/api/v1/me/mcp-client');
    return response.data;
  },

  /**
   * Replace the client ID; the old client and all its connections are deleted
   */
  async regenerateClient(): Promise<McpClientInfo> {
    const response = await axios.post<McpClientInfo>('/api/v1/me/mcp-client/regenerate');
    return response.data;
  },

  /**
   * Revoke all MCP connections of the current user
   */
  async revokeConnections(): Promise<void> {
    await axios.delete('/api/v1/me/mcp-connections');
  },
};

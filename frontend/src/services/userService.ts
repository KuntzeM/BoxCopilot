import axios from './axiosConfig';
import { User, CreateUserPayload, UpdateUserPayload, SetPasswordPayload, AuthProvider, Role } from '../types/models';

/**
 * User management service for admin operations
 */

interface GetUsersFilters {
  role?: Role;
  authProvider?: AuthProvider;
  enabled?: boolean;
}

export const userService = {
  /**
   * Get all users with optional filters
   */
  async getUsers(filters?: GetUsersFilters): Promise<User[]> {
    const params = new URLSearchParams();
    if (filters?.role) params.append('role', filters.role);
    if (filters?.authProvider) params.append('authProvider', filters.authProvider);
    if (filters?.enabled !== undefined) params.append('enabled', filters.enabled.toString());
    
    const queryString = params.toString();
    const url = `/api/v1/admin/users${queryString ? `?${queryString}` : ''}`;
    
    const response = await axios.get<User[]>(url);
    return response.data;
  },

  /**
   * Get user by ID
   */
  async getUserById(id: number): Promise<User> {
    const response = await axios.get<User>(`/api/v1/admin/users/${id}`);
    return response.data;
  },

  /**
   * Create new local user
   */
  async createUser(payload: CreateUserPayload): Promise<User> {
    const response = await axios.post<User>('/api/v1/admin/users', payload);
    return response.data;
  },

  /**
   * Update existing user
   */
  async updateUser(id: number, payload: UpdateUserPayload): Promise<User> {
    const response = await axios.put<User>(`/api/v1/admin/users/${id}`, payload);
    return response.data;
  },

  /**
   * Set user password
   */
  async setPassword(id: number, payload: SetPasswordPayload): Promise<void> {
    await axios.put(`/api/v1/admin/users/${id}/password`, payload);
  },

  /**
   * Unlock user account
   */
  async unlockUser(id: number): Promise<void> {
    await axios.put(`/api/v1/admin/users/${id}/unlock`);
  },

  /**
   * Delete user
   */
  async deleteUser(id: number): Promise<void> {
    await axios.delete(`/api/v1/admin/users/${id}`);
  },
};

export interface Item {
  id: number;
  name: string;
  boxId?: number;
  boxUuid?: string;
  boxCurrentRoom?: string;
  boxTargetRoom?: string;
  imageUrl?: string;
}

export interface Box {
  id: number;
  uuid: string;
  currentRoom?: string;
  targetRoom?: string;
  description?: string;
  createdAt?: string;
  items?: Item[];
  publicUrl?: string;
  isFragile?: boolean;
  noStack?: boolean;
}

export interface BoxPreviewItem {
  id: number;
  name: string;
  imageUrl?: string;
}

export interface BoxPreview {
  id: number;
  uuid: string;
  currentRoom?: string;
  targetRoom?: string;
  description?: string;
  items: BoxPreviewItem[];
  isFragile?: boolean;
  noStack?: boolean;
}

export interface CreateBoxPayload {
  currentRoom?: string;
  targetRoom?: string;
  description?: string;
  isFragile?: boolean;
  noStack?: boolean;
}

export type UpdateBoxPayload = CreateBoxPayload;

export interface CreateItemPayload {
  name: string;
  boxUuid: string;
}

export interface UpdateItemPayload {
  name: string;
}

// User management types
export enum AuthProvider {
  NEXTCLOUD = 'NEXTCLOUD',
  LOCAL = 'LOCAL'
}

export enum Role {
  USER = 'USER',
  ADMIN = 'ADMIN'
}

export interface User {
  id: number;
  username: string;
  name?: string;
  authProvider: AuthProvider;
  role: Role;
  enabled: boolean;
  createdAt: string;
  lastLogin?: string;
  failedLoginAttempts: number;
  lockedUntil?: string;
}

export interface CreateUserPayload {
  username: string;
  name: string;
  password?: string; // Now optional for passwordless accounts
  role: Role;
  enabled?: boolean;
}

export interface UpdateUserPayload {
  username?: string;
  name?: string;
  role?: Role;
  enabled?: boolean;
}

export interface SetPasswordPayload {
  password: string;
}

export interface CreateMagicLinkPayload {
  expiresInHours?: number;
}

export interface MagicLinkResponse {
  token: string;
  url: string;
  expiresAt: string;
}

export interface UserPrincipal {
  id?: number;
  username?: string;
  name?: string;
  role?: Role;
  authProvider?: AuthProvider;
  isAdmin: boolean;
  csrfToken?: string;
  authenticated: boolean;
  // Legacy OIDC fields (for backward compatibility)
  sub?: string;
  email?: string;
  preferredUsername?: string;
}

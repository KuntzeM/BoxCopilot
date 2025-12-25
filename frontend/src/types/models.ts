export interface Item {
  id: number;
  name: string;
  boxId?: number;
  boxUuid?: string;
  boxCurrentRoom?: string;
  boxTargetRoom?: string;
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
  name: string;
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

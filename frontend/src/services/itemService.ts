import axios from './axiosConfig';
import { CreateItemPayload, Item, UpdateItemPayload } from '../types/models';

export async function createItem(payload: CreateItemPayload): Promise<Item> {
  const response = await axios.post('/api/v1/items', payload);
  return response.data;
}

export async function updateItem(id: number, payload: UpdateItemPayload): Promise<Item> {
  const response = await axios.put(`/api/v1/items/${id}`, payload);
  return response.data;
}

export async function deleteItem(id: number): Promise<void> {
  await axios.delete(`/api/v1/items/${id}`);
}

export async function searchItems(query: string): Promise<Item[]> {
  const response = await axios.get('/api/v1/items/search', { params: { q: query } });
  return response.data || [];
}

export async function uploadItemImage(itemId: number, file: File): Promise<Item> {
  const formData = new FormData();
  formData.append('file', file);
  const response = await axios.post(`/api/v1/items/${itemId}/image`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
  return response.data;
}

export async function deleteItemImage(itemId: number): Promise<Item> {
  const response = await axios.delete(`/api/v1/items/${itemId}/image`);
  return response.data;
}

export async function moveItem(itemId: number, targetBoxId: number): Promise<Item> {
  const response = await axios.put(`/api/v1/items/${itemId}/move`, { targetBoxId });
  return response.data;
}

export async function moveItems(itemIds: number[], targetBoxId: number): Promise<void> {
  await axios.put('/api/v1/items/move-bulk', { itemIds, targetBoxId });
}


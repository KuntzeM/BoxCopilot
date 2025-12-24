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

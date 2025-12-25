import axios from './axiosConfig';
import { Box, CreateBoxPayload, UpdateBoxPayload } from '../types/models';

export async function fetchBoxes(): Promise<Box[]> {
  const response = await axios.get('/api/v1/boxes');
  return response.data;
}

export async function listBoxes(): Promise<Box[]> {
  return fetchBoxes();
}

export async function createBox(payload: CreateBoxPayload): Promise<Box> {
  const response = await axios.post('/api/v1/boxes', payload);
  return response.data;
}

export async function updateBox(id: number, payload: UpdateBoxPayload): Promise<Box> {
  const response = await axios.put(`/api/v1/boxes/${id}`, payload);
  return response.data;
}

export async function deleteBox(id: number): Promise<void> {
  await axios.delete(`/api/v1/boxes/${id}`);
}


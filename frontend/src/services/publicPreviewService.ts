import axios from './axiosConfig';
import { BoxPreview } from '../types/models';

export async function fetchPublicPreview(token: string): Promise<BoxPreview> {
  const response = await axios.get(`/api/v1/public/${token}`);
  return response.data;
}

import axios, { AxiosError } from 'axios';
import { tokenStore } from '../auth/tokenStore';
import type { ApiError } from './types';

/**
 * Cliente HTTP único. Adjunta el Bearer del IdP en cada request y convierte los errores
 * de la API al contrato {@link ApiError} para que las pantallas muestren `message`.
 */
export const http = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '',
  timeout: 15_000,
});

http.interceptors.request.use((config) => {
  const token = tokenStore.get();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

http.interceptors.response.use(
  (r) => r,
  (error: AxiosError<ApiError>) => Promise.reject(toApiError(error)),
);

/** Normaliza cualquier fallo (red, timeout, 5xx sin cuerpo) al mismo formato. */
export function toApiError(error: AxiosError<ApiError>): ApiError {
  const body = error.response?.data;
  if (body && typeof body === 'object' && 'error' in body) return body;
  return {
    status: error.response?.status ?? 0,
    error: error.code ?? 'NETWORK_ERROR',
    message: error.response ? 'El servidor respondió con un error' : 'No se pudo conectar con el servidor',
    timestamp: new Date().toISOString(),
  };
}

export const isApiError = (e: unknown): e is ApiError =>
  typeof e === 'object' && e !== null && 'error' in e && 'message' in e;

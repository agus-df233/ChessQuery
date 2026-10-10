import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
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

/**
 * Qué hacer ante un 401 (token vencido o revocado): lo registra la capa de sesión (auth/session.tsx). Devuelve un token
 * nuevo si logró renovar la sesión en silencio, o null si hay que volver a entrar.
 */
type UnauthorizedHandler = () => Promise<string | null>;
let onUnauthorized: UnauthorizedHandler | null = null;
export const setUnauthorizedHandler = (handler: UnauthorizedHandler | null) => { onUnauthorized = handler; };

type RetriableConfig = InternalAxiosRequestConfig & { _renewed?: boolean };

/** Ante un 401 renueva la sesión una sola vez y repite el pedido; si no se puede, devuelve el error normalizado. */
export async function retryOnUnauthorized(error: AxiosError<ApiError>) {
  const config = error.config as RetriableConfig | undefined;
  if (error.response?.status !== 401 || !config || config._renewed || !onUnauthorized) {
    throw toApiError(error);
  }
  config._renewed = true;
  const token = await onUnauthorized();
  if (!token) throw toApiError(error);
  config.headers.Authorization = `Bearer ${token}`;
  return http(config);
}

http.interceptors.response.use((r) => r, retryOnUnauthorized);

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

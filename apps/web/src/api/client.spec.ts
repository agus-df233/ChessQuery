import { describe, expect, it } from 'vitest';
import type { AxiosError } from 'axios';
import { http, isApiError, toApiError } from './client';
import { tokenStore } from '../auth/tokenStore';
import type { ApiError } from './types';

describe('cliente HTTP', () => {
  it('adjunta el Bearer cuando hay token', async () => {
    tokenStore.set('abc');
    const handler = (http.interceptors.request as unknown as { handlers: { fulfilled: (c: { headers: Record<string, string> }) => { headers: Record<string, string> } }[] }).handlers[0];
    expect(handler.fulfilled({ headers: {} }).headers.Authorization).toBe('Bearer abc');
    tokenStore.set(null);
    expect(handler.fulfilled({ headers: {} }).headers.Authorization).toBeUndefined();
  });

  it('propaga el cuerpo de error de la API y normaliza fallos de red', () => {
    const body: ApiError = { status: 409, error: 'RUT_TAKEN', message: 'ya existe', timestamp: 't' };
    expect(toApiError({ response: { data: body } } as AxiosError<ApiError>)).toBe(body);
    const net = toApiError({ code: 'ECONNABORTED' } as AxiosError<ApiError>);
    expect(net.error).toBe('ECONNABORTED');
    expect(net.message).toMatch(/conectar/);
    const html = toApiError({ response: { status: 502, data: '<html>' as unknown as ApiError } } as AxiosError<ApiError>);
    expect(html.status).toBe(502);
    expect(isApiError(body)).toBe(true);
    expect(isApiError('x')).toBe(false);
  });
});

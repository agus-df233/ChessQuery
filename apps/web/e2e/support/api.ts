import { expect, request as playwrightRequest, type APIRequestContext, type APIResponse, type Browser, type Page } from '@playwright/test';
import { login, persona } from './helpers';

/** Armado de datos por API con sesiones reales del IdP simulado (lo comparten la galería y la suite de seguridad). */
export const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:5173';

export interface Actor { page: Page; token: string; id: number; api: APIRequestContext }

/** Access token que la SPA guardó en sessionStorage tras el login (oidc-client-ts). */
export const tokenOf = (page: Page) => page.evaluate(() => {
  const key = Object.keys(sessionStorage).find((k) => k.startsWith('oidc.user:'));
  return key ? (JSON.parse(sessionStorage.getItem(key) ?? '{}').access_token as string) : '';
});

export const apiFor = (token?: string) =>
  playwrightRequest.newContext({ baseURL: BASE, extraHTTPHeaders: token ? { Authorization: `Bearer ${token}` } : {} });

/** Entra por la portada y deja listo un cliente de API con su token. */
export async function actor(browser: Browser, name: string): Promise<Actor> {
  const page = await login(browser, persona(name));
  const token = await tokenOf(page);
  const api = await apiFor(token);
  const me = await (await api.get('/api/users/me')).json();
  return { page, token, id: me.profile.id, api };
}

/** La llamada de preparación salió bien (si no, el mensaje muestra la respuesta). */
export const ok = async (res: APIResponse) => expect(res.ok(), `${res.url()} → ${res.status()} ${await res.text()}`).toBeTruthy();

/** Igual que {@link ok}, y devuelve el JSON de la respuesta. */
export const json = async <T = any>(res: Promise<APIResponse>): Promise<T> => { // eslint-disable-line @typescript-eslint/no-explicit-any
  const r = await res;
  await ok(r);
  return r.json();
};

export const tournamentBody = (name: string, extra: Record<string, unknown> = {}) => ({
  name, startDate: new Date().toISOString().slice(0, 10), format: 'SWISS', rounds: 3, baseMinutes: 10, incrementSeconds: 5, ...extra,
});

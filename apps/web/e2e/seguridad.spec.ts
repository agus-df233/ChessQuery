import { expect, request as playwrightRequest, test, type APIResponse } from '@playwright/test';
import { RUN } from './support/helpers';
import { type Actor, actor, apiFor, ok, tournamentBody } from './support/api';

/**
 * Pruebas de abuso (caja negra) contra el stack local, nunca contra la nube: cada intento debe fallar de forma segura.
 * Cubre JWT manipulado, IDOR y escalamiento, adivinar tokens, datos personales en respuestas públicas (Ley 21.719),
 * XSS almacenado, inyección de fórmulas en el TRF, WebSocket sin permiso y cabeceras de seguridad.
 * El throttling y la CSP los pone API Gateway / S3 en la nube: se verifican allá (docs/verificacion/plan-de-pruebas.md).
 */
const IDP = 'http://localhost:8090';
const PII_KEYS = ['rut', 'rutHash', 'email', 'birthDate', 'gender'];
const XSS_NAME = `<img src=x onerror="window.__xss=1">Torneo ${RUN}`;
const FORMULA = '=HYPERLINK("http://malo.example","x")';

/** Token de máquina del IdP simulado (sub = client_id, aud = scope); con otra ruta, otro issuer. */
async function machineToken(issuerPath: string, scope: string) {
  const ctx = await playwrightRequest.newContext();
  const res = await ctx.post(`${IDP}/${issuerPath}/token`, {
    form: { grant_type: 'client_credentials', client_id: `intruso-${RUN}`, client_secret: 'x', scope },
  });
  return (await res.json()).access_token as string;
}

/** RUT ficticio distinto en cada corrida (la base local persiste), con dígito verificador válido (módulo 11). */
const fakeRut = () => {
  const body = String(10_000_000 + (Date.now() % 9_000_000));
  const sum = [...body].reverse().reduce((acc, d, i) => acc + Number(d) * (2 + (i % 6)), 0);
  const dv = 11 - (sum % 11);
  return `${body}-${dv === 11 ? '0' : dv === 10 ? 'K' : dv}`;
};

const b64url = (o: object) => Buffer.from(JSON.stringify(o)).toString('base64url');
const randomToken = () => Buffer.from(crypto.getRandomValues(new Uint8Array(16))).toString('base64url');

/** Todas las claves de un JSON, a cualquier profundidad. */
const keysOf = (v: unknown): string[] => {
  if (Array.isArray(v)) return v.flatMap(keysOf);
  if (v && typeof v === 'object') return Object.entries(v).flatMap(([k, x]) => [k, ...keysOf(x)]);
  return [];
};

/** Denegado sin filtrar nada: 403 o 404 (en el mensaje va la respuesta, para entender una falla). */
const denied = async (res: APIResponse) => expect([403, 404], `${res.url()} → ${res.status()} ${await res.text()}`).toContain(res.status());

test.describe.serial('seguridad: abusos contra el stack local', () => {
  let orga: Actor; let jugador: Actor; let otra: Actor;
  let tournamentId: number; let roomId: number; let gameId: number; let rosterId: number;

  test.beforeAll(async ({ browser }) => {
    orga = await actor(browser, 'Orga');
    jugador = await actor(browser, 'Jugo');
    otra = await actor(browser, 'Otra');
    expect((await orga.api.post('/api/organizations', { data: { name: `Club Seguro ${RUN}` } })).status()).toBe(201);
    const t = await orga.api.post('/api/tournaments', { data: tournamentBody(XSS_NAME) });
    await ok(t);
    tournamentId = (await t.json()).tournament.id;
    const r = await orga.api.post('/api/rooms', { data: { name: 'Sala segura', boards: 2, minutes: 5, incrementSeconds: 0 } });
    roomId = (await r.json()).id;
    const roster = await orga.api.post('/api/organizations/me/roster', { data: { firstName: 'Planilla', lastName: FORMULA } });
    await ok(roster);
    rosterId = (await roster.json()).id;
    const g = await jugador.api.post('/api/games', { data: { opponentId: otra.id, minutes: 5, incrementSeconds: 0, color: 'WHITE', rated: false } });
    gameId = (await g.json()).id;
    await ok(await otra.api.post(`/api/games/${gameId}/accept`));
  });

  test('JWT ausente, sin firma, alterado o de otra audiencia u otro emisor: 401', async () => {
    const anonymous = await apiFor();
    expect((await anonymous.get('/api/users/me')).status()).toBe(401);

    const [header, payload, signature] = jugador.token.split('.');
    const claims = JSON.parse(Buffer.from(payload, 'base64url').toString());
    const forged = [
      `${b64url({ alg: 'none', typ: 'JWT' })}.${payload}.`, // alg:none
      `${header}.${b64url({ ...claims, sub: `otra-${RUN}` })}.${signature}`, // cambio de sujeto con la firma vieja
      `${header}.${payload}.${signature.slice(0, -4)}AAAA`, // firma alterada
      await machineToken('chessquery', 'otra-api'), // emisor correcto, audiencia ajena
      await machineToken('otro-emisor', 'default'), // audiencia correcta, emisor ajeno
    ];
    for (const token of forged) {
      expect((await (await apiFor(token)).get('/api/users/me')).status(), token.slice(0, 24)).toBe(401);
    }
  });

  test('IDOR y escalamiento: un jugador no toca lo de otro organizador ni juega partidas ajenas', async () => {
    const j = jugador.api;
    await denied(await j.put(`/api/tournaments/${tournamentId}`, { data: tournamentBody('Ahora es mío') }));
    await denied(await j.post(`/api/tournaments/${tournamentId}/rounds`));
    await denied(await j.post(`/api/tournaments/${tournamentId}/finish`));
    await denied(await j.get(`/api/tournaments/${tournamentId}/registrations`));
    await denied(await j.get(`/api/tournaments/${tournamentId}/trf`));
    await denied(await j.put(`/api/rooms/${roomId}/boards/1`, { data: { whitePlayerId: jugador.id, blackPlayerId: null } }));
    await denied(await j.post(`/api/organizations/me/roster/${rosterId}/invite`));
    // La organizadora no juega por otros: la partida es de Jugo y Otra
    await denied(await orga.api.post(`/api/games/${gameId}/moves`, { data: { uci: 'e2e4' } }));
    // Y Otra (negras) no mueve por las blancas
    expect((await otra.api.post(`/api/games/${gameId}/moves`, { data: { uci: 'e2e4' } })).status()).toBeGreaterThanOrEqual(400);
  });

  test('tokens de invitación, desafío abierto y acreditación: 128 bits y sin enumeración', async () => {
    const invite = await (await orga.api.post(`/api/organizations/me/roster/${rosterId}/invite`)).json();
    expect(invite.token).toMatch(/^[A-Za-z0-9_-]{22}$/);
    const guesses = [randomToken(), randomToken()];
    const answers = await Promise.all(guesses.map((g) => jugador.api.get(`/api/users/claim-invite/${g}`)));
    expect(answers.map((a) => a.status())).toEqual([404, 404]);
    expect(new Set(await Promise.all(answers.map(async (a) => (await a.json()).error))).size).toBe(1);
    expect((await jugador.api.get(`/api/games/open/${randomToken()}`)).status()).toBe(404);
    const checkin = await jugador.api.post(`/api/tournaments/${tournamentId}/checkin`, { data: { code: randomToken() } });
    expect(checkin.status()).toBeGreaterThanOrEqual(400);
    expect(checkin.status()).toBeLessThan(500);
  });

  test('Ley 21.719: lo público no trae datos personales y un menor sale con apellido abreviado', async () => {
    const minorBirth = new Date(Date.now() - 11 * 365.25 * 24 * 3600 * 1000).toISOString().slice(0, 10);
    await ok(await jugador.api.put('/api/users/me/profile', { data: { birthDate: minorBirth, rut: fakeRut(), gender: 'M' } }));
    await ok(await orga.api.post(`/api/tournaments/${tournamentId}/registrations`, { data: { playerId: jugador.id } }));

    const anonymous = await apiFor();
    for (const path of [`/api/public/tournaments/${tournamentId}`, `/api/public/tournaments/${tournamentId}/live`, '/api/public/ranking?limit=50']) {
      const res = await anonymous.get(path);
      await ok(res);
      const leaked = keysOf(await res.json()).filter((k) => PII_KEYS.includes(k));
      expect(leaked, path).toEqual([]);
    }
    const profile = await (await otra.api.get(`/api/users/${jugador.id}/public-profile`)).json();
    expect(keysOf(profile).filter((k) => PII_KEYS.includes(k))).toEqual([]);
    expect(profile.lastName).toMatch(/^E\.$/); // «E2E…» abreviado: es menor y no hay consentimiento parental
  });

  test('XSS almacenado: el nombre del torneo se muestra como texto y no ejecuta nada', async ({ browser }) => {
    const page = await (await browser.newContext()).newPage();
    let dialog = false;
    page.on('dialog', async (d) => { dialog = true; await d.dismiss(); });
    await page.goto(`/torneos/${tournamentId}`);
    await expect(page.getByText(XSS_NAME).first()).toBeVisible();
    expect(await page.evaluate(() => (window as unknown as { __xss?: number }).__xss)).toBeUndefined();
    expect(await page.locator('img[src="x"]').count()).toBe(0);
    expect(dialog).toBe(false);
  });

  test('TRF: un nombre que parece fórmula sale neutralizado', async () => {
    await ok(await orga.api.post(`/api/tournaments/${tournamentId}/registrations`, { data: { playerId: rosterId } }));
    const trf = await (await orga.api.get(`/api/tournaments/${tournamentId}/trf`)).text();
    const line = trf.split('\n').find((l) => l.includes('HYPERLINK'));
    expect(line?.substring(14, 16)).toBe("'=");
    expect(trf).not.toMatch(/^012 .*\n.*<img/m); // el encabezado sigue en una sola línea
  });

  test('WebSocket: sin token se cierra y un extraño no se suscribe a una sala', async () => {
    const page = jugador.page;
    const closeCode = await page.evaluate(() => new Promise<number>((resolve) => {
      const ws = new WebSocket(`ws://${location.host}/ws`);
      ws.onclose = (e) => resolve(e.code);
    }));
    expect(closeCode).toBe(1008);
    const reply = await page.evaluate(({ token, room }) => new Promise<string>((resolve) => {
      const ws = new WebSocket(`ws://${location.host}/ws?token=${encodeURIComponent(token)}`);
      ws.onopen = () => ws.send(JSON.stringify({ action: 'subscribe', roomId: room }));
      ws.onmessage = (e) => { resolve(String(e.data)); ws.close(); };
      ws.onclose = () => resolve('cerrado');
    }), { token: jugador.token, room: roomId });
    expect(reply).toContain('NOT_IN_ROOM');
  });

  test('cabeceras de seguridad y formato de error en la API', async () => {
    const res = await jugador.api.get('/api/users/me');
    expect(res.headers()['x-content-type-options']).toBe('nosniff');
    expect(res.headers()['x-frame-options']).toBe('DENY');
    // Recurso inexistente y método que la ruta no acepta: 4xx con el formato común, nunca un 500
    const format = ['status', 'error', 'message', 'timestamp'];
    const missing = await jugador.api.get('/api/public/tournaments/999999999');
    expect(missing.status()).toBe(404);
    expect(Object.keys(await missing.json())).toEqual(expect.arrayContaining(format));
    const wrongMethod = await jugador.api.get(`/api/tournaments/${tournamentId}`);
    expect(wrongMethod.status()).toBe(405);
    expect(Object.keys(await wrongMethod.json())).toEqual(expect.arrayContaining(format));
  });
});

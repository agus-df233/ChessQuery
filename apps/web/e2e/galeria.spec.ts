import { mkdirSync, writeFileSync } from 'node:fs';
import { expect, test, type Browser, type Page } from '@playwright/test';
import { RUN, expectAccessible } from './support/helpers';
import { type Actor, actor, json, ok, tournamentBody } from './support/api';

/**
 * Galería de auditoría: todas las vistas (público, jugador y organizador) y sus estados interactivos, en 375, 768 y
 * 1280 px. Por cada una guarda la captura y mide lo que se rompe en un celular:
 *   - scroll horizontal del documento,
 *   - controles cortados por el borde (fuera de un contenedor con scroll propio),
 *   - objetivos táctiles de menos de 24 px (WCAG 2.5.8),
 *   - títulos pegados al borde.
 * Las fallas de axe y el scroll horizontal son blandas (expect.soft): la corrida termina y deja el informe completo en
 * e2e/capturas/galeria/hallazgos.json, que alimenta docs/verificacion/auditoria-ux.md.
 */
const WIDTHS = [375, 768, 1280] as const;
const OUT = 'e2e/capturas/galeria';
const findings: Record<string, unknown>[] = [];

interface View { name: string; path: string; who: 'publico' | 'jugador' | 'organizador' | 'nueva'; before?: (p: Page) => Promise<void> }

/** Lo que se mide dentro de la página (corre en el navegador). */
const measure = (page: Page) => page.evaluate(() => {
  const vw = window.innerWidth;
  const label = (e: Element) => ((e.getAttribute('aria-label') || e.textContent || e.getAttribute('name') || e.tagName) ?? '')
    .replace(/\s+/g, ' ').trim().slice(0, 40);
  const insideScroller = (e: Element) => {
    for (let p = e.parentElement; p; p = p.parentElement) {
      if (/(auto|scroll)/.test(getComputedStyle(p).overflowX) && p.scrollWidth > p.clientWidth) return true;
    }
    return false;
  };
  const visible = [...document.querySelectorAll('a[href],button,input,select,textarea,[role="button"]')]
    .filter((e) => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0 && r.right > 0 && r.left < vw; });
  const cut = visible.filter((e) => { const r = e.getBoundingClientRect(); return (r.right > vw + 1 || r.left < -1) && !insideScroller(e); });
  const small = visible.filter((e) => {
    const r = e.getBoundingClientRect();
    const inlineLink = e.tagName === 'A' && getComputedStyle(e).display === 'inline';
    const radio = e instanceof HTMLInputElement && ['radio', 'checkbox'].includes(e.type);
    return !inlineLink && !radio && (r.width < 24 || r.height < 24);
  });
  const tight = [...document.querySelectorAll('h1, h2')]
    .filter((h) => { const r = h.getBoundingClientRect(); return r.width > 0 && (r.left < 12 || vw - r.right < 0); });
  return {
    overflow: document.documentElement.scrollWidth - vw,
    cut: cut.map(label), small: small.map(label), tight: tight.map(label),
  };
});

async function audit(page: Page, view: View) {
  for (const width of WIDTHS) {
    await page.setViewportSize({ width, height: width === 375 ? 812 : 900 });
    await page.goto(view.path);
    await page.locator('h1, h2').first().waitFor({ timeout: 20_000 });
    if (view.before) await view.before(page);
    await page.waitForFunction(() => document.getAnimations().every((a) => a.playState !== 'running'));
    await page.screenshot({ path: `${OUT}/${view.name}-${width}.png`, fullPage: true });
    const m = await measure(page);
    findings.push({ vista: view.name, rol: view.who, ancho: width, ...m });
    expect.soft(m.overflow, `${view.name} @${width}: scroll horizontal`).toBeLessThanOrEqual(1);
    expect.soft(m.cut, `${view.name} @${width}: controles cortados`).toEqual([]);
  }
  await page.setViewportSize({ width: 1280, height: 900 });
  await expectAccessibleSoft(page, view.name);
}

async function expectAccessibleSoft(page: Page, name: string) {
  try {
    await expectAccessible(page);
  } catch (e) {
    findings.push({ vista: name, axe: String(e).slice(0, 2000) });
    expect.soft(String(e), `${name}: axe`).toBe('');
  }
}

/** Datos para que cada vista tenga contenido real (todo ficticio). */
async function scenario(browser: Browser) {
  const orga = await actor(browser, 'Gala');
  const jug = await actor(browser, 'Gino');
  const otra = await actor(browser, 'Gema');
  const nueva = await actor(browser, 'Nadia'); // no completa la bienvenida
  for (const a of [orga, jug, otra]) await ok(await a.api.put('/api/users/me/profile', { data: { welcomed: true, preferredCategory: 'BLITZ', region: 'Valparaíso' } }));

  await ok(await orga.api.post('/api/organizations', { data: { name: `Club Galería ${RUN}`, city: 'Santiago' } }));
  const rows = ['Tomás González', 'Valentina Muñoz', 'Matías Rojas', 'Catalina Díaz', 'Benjamín Pérez', 'Javiera Soto', 'Vicente Silva', 'Antonia Torres']
    .map((n, i) => ({ firstName: n.split(' ')[0], lastName: `${n.split(' ')[1]} ${RUN}`, eloNational: 1900 - i * 60 }));
  const report = await json(orga.api.post('/api/organizations/me/roster/import', { data: { rows } }));
  const roster: number[] = report.rows.map((r: { playerId: number }) => r.playerId);

  const open = (await json(orga.api.post('/api/tournaments', { data: tournamentBody(`Abierto Galería ${RUN}`, { maxPlayers: 12, requiresApproval: true, checkinRequired: true }) }))).tournament;
  await ok(await orga.api.post(`/api/tournaments/${open.id}/registrations/bulk`, { data: { playerIds: roster.slice(0, 4) } }));
  await ok(await jug.api.post(`/api/tournaments/${open.id}/join`));

  const live = (await json(orga.api.post('/api/tournaments', { data: tournamentBody(`Liga Galería ${RUN}`, { rounds: 4 }) }))).tournament;
  await ok(await orga.api.post(`/api/tournaments/${live.id}/registrations/bulk`, { data: { playerIds: [...roster.slice(2, 8), jug.id] } }));
  for (let round = 1; round <= 2; round++) {
    const r = await json(orga.api.post(`/api/tournaments/${live.id}/rounds`));
    if (round === 2) break; // la ronda 2 queda con resultados pendientes (se ven los selectores)
    for (const b of r.boards.filter((x: { black: unknown; result: unknown }) => x.black && !x.result)) {
      await ok(await orga.api.put(`/api/tournaments/${live.id}/rounds/${r.number}/boards/${b.board}`, { data: { result: b.board % 2 ? 'WHITE_WINS' : 'DRAW' } }));
    }
  }

  const room = await json(orga.api.post('/api/rooms', { data: { name: 'Clase 4°B', boards: 4, maxPlayers: 8, minutes: 10, incrementSeconds: 0 } }));
  for (const a of [jug, otra]) await ok(await a.api.post('/api/rooms/join', { data: { code: room.code } }));
  await ok(await orga.api.put(`/api/rooms/${room.id}/boards/1`, { data: { whitePlayerId: jug.id, blackPlayerId: otra.id } }));

  const req = await json(jug.api.post('/api/friends/requests', { data: { addresseeId: otra.id } }));
  await ok(await otra.api.post(`/api/friends/requests/${req.requestId}/accept`));
  const game = await json(jug.api.post('/api/games', { data: { opponentId: otra.id, minutes: 10, incrementSeconds: 0, color: 'WHITE', rated: true } }));
  await ok(await otra.api.post(`/api/games/${game.id}/accept`));
  await ok(await jug.api.post(`/api/games/${game.id}/moves`, { data: { uci: 'e2e4' } }));
  const challenge = await json(jug.api.post('/api/games/open', { data: { minutes: 3, incrementSeconds: 2, color: 'RANDOM', rated: true } }));
  const invite = await json(orga.api.post(`/api/organizations/me/roster/${roster[0]}/invite`));
  return { orga, jug, otra, nueva, open, live, room, game, challenge, invite };
}

test('galería: todas las vistas en 375, 768 y 1280 px', async ({ browser }) => {
  test.setTimeout(15 * 60_000);
  mkdirSync(OUT, { recursive: true });
  const s = await scenario(browser);
  const publico = await (await browser.newContext()).newPage();
  const pages: Record<View['who'], Page> = { publico, jugador: s.jug.page, organizador: s.orga.page, nueva: s.nueva.page };
  const errors: string[] = [];
  Object.values(pages).forEach((p) => p.on('pageerror', (e) => errors.push(e.message)));

  const views: View[] = [
    { name: 'publico-portada', path: '/', who: 'publico' },
    { name: 'publico-ranking', path: '/ranking', who: 'publico' },
    { name: 'publico-torneos', path: '/torneos', who: 'publico' },
    { name: 'publico-torneo', path: `/torneos/${s.live.id}`, who: 'publico' },
    { name: 'publico-seguir-jugador', path: `/torneos/${s.live.id}`, who: 'publico',
      before: async (p) => { await p.getByLabel('Busca a tu hijo, a un amigo o a cualquier jugador').fill('Gino'); } },
    { name: 'publico-pantalla-sala', path: `/torneos/${s.live.id}/pantalla`, who: 'publico' },
    { name: 'nueva-bienvenida', path: '/app', who: 'nueva' },
    { name: 'jugador-inicio', path: '/app', who: 'jugador' },
    { name: 'jugador-menu-movil', path: '/app', who: 'jugador',
      before: async (p) => { const b = p.getByRole('button', { name: 'Abrir menú' }); if (await b.isVisible()) await b.click(); } },
    { name: 'jugador-perfil', path: '/app/perfil', who: 'jugador' },
    { name: 'jugador-jugadores', path: '/app/jugadores', who: 'jugador' },
    { name: 'jugador-ficha-rival', path: `/app/jugadores/${s.otra.id}`, who: 'jugador' },
    { name: 'jugador-desafio-formulario', path: `/app/jugadores/${s.otra.id}`, who: 'jugador',
      before: async (p) => { await p.getByRole('button', { name: 'Desafiar' }).click(); } },
    { name: 'jugador-ranking', path: '/app/ranking', who: 'jugador' },
    { name: 'jugador-partidas', path: '/app/partidas', who: 'jugador' },
    { name: 'jugador-partida', path: `/app/partidas/${s.game.id}`, who: 'jugador' },
    { name: 'jugador-amigos', path: '/app/amigos', who: 'jugador' },
    { name: 'jugador-torneos', path: '/app/torneos', who: 'jugador' },
    { name: 'jugador-torneo-inscrito', path: `/app/torneos/${s.open.id}`, who: 'jugador' },
    { name: 'jugador-salas', path: '/app/salas', who: 'jugador' },
    { name: 'jugador-sala', path: `/app/salas/${s.room.id}`, who: 'jugador' },
    { name: 'jugador-desafio-abierto', path: `/app/desafio/${s.challenge.token}`, who: 'jugador' },
    { name: 'jugador-reclamar-perfil', path: `/app/reclamar/${s.invite.token}`, who: 'jugador' },
    { name: 'organizador-club', path: '/club', who: 'organizador' },
    { name: 'organizador-torneos', path: '/club/torneos', who: 'organizador' },
    { name: 'organizador-torneo-abierto', path: `/club/torneos/${s.open.id}`, who: 'organizador' },
    { name: 'organizador-torneo-en-curso', path: `/club/torneos/${s.live.id}`, who: 'organizador' },
    { name: 'organizador-acreditacion', path: `/club/torneos/${s.open.id}/acreditacion`, who: 'organizador' },
    { name: 'organizador-credenciales', path: `/club/torneos/${s.open.id}/credenciales`, who: 'organizador' },
    { name: 'organizador-salas', path: '/club/salas', who: 'organizador' },
    { name: 'organizador-sala', path: `/club/salas/${s.room.id}`, who: 'organizador' },
  ];
  for (const view of views) {
    await test.step(view.name, () => audit(pages[view.who], view));
  }
  writeFileSync(`${OUT}/hallazgos.json`, JSON.stringify(findings, null, 2));
  expect(errors, 'errores de JavaScript en las vistas').toEqual([]);
});

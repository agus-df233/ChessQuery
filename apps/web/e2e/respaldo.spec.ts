import { expect, test, type Page } from '@playwright/test';
import { login, persona } from './support/helpers';

/**
 * Respaldo del tiempo real: si el WebSocket no está disponible (red que lo bloquea, API Gateway caído), la partida
 * sigue funcionando por long polling. Se bloquea el WebSocket en los dos navegadores y se juega.
 */
const ana = persona('Rosa');
const luis = persona('Tomas');

async function sinWebSocket(page: Page) {
  await page.routeWebSocket(/\/ws/, (ws) => ws.close({ code: 1011, reason: 'bloqueado en la prueba' }));
}

async function play(page: Page, from: string, to: string) {
  await expect(page.getByText('Tu turno')).toBeVisible();
  await page.getByRole('button', { name: new RegExp(`^${from},`) }).click();
  await page.getByRole('button', { name: new RegExp(`^${to},`) }).click();
}

test('sin WebSocket la partida sigue por long polling', async ({ browser }) => {
  const a = await login(browser, ana);
  const l = await login(browser, luis);
  await sinWebSocket(a);
  await sinWebSocket(l);

  await a.goto('/app/jugadores');
  await a.getByLabel('Buscar jugador').fill(`Tomas ${luis.lastName}`);
  await a.getByRole('button', { name: 'Buscar' }).click();
  await a.getByRole('link', { name: new RegExp(`Tomas ${luis.lastName}`) }).click();
  await a.getByRole('button', { name: 'Desafiar' }).click();
  await a.getByLabel('Juego con').selectOption('WHITE');
  await a.getByRole('button', { name: 'Enviar desafío' }).click();
  await expect(a.getByText('Esperando que tu rival acepte…')).toBeVisible();
  const gameUrl = a.url();
  await l.goto('/app/partidas');
  await l.getByRole('button', { name: 'Aceptar' }).first().click();
  await l.goto(gameUrl);

  await play(a, 'e2', 'e4');
  await play(l, 'e7', 'e5');
  await play(a, 'g1', 'f3');
  await expect(l.getByText('Tu turno')).toBeVisible();
  await expect(l.getByRole('list', { name: 'Jugadas' })).toContainText('Nf3');
});

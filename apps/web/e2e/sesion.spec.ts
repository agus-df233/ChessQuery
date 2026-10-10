import { expect, test, type Page } from '@playwright/test';
import { expectAccessible, persona, type Persona } from './support/helpers';

/**
 * Inicio, vencimiento y cierre de sesión, de punta a punta con el IdP simulado:
 * - la portada ofrece entrar como jugador o como organizador, y cada acceso vuelve a su sección;
 * - quien tiene club cambia de modo (Jugador | Organizador) con el selector y ve el menú de cada modo;
 * - una sesión que no se puede renovar avisa y vuelve al login, de vuelta a la misma página;
 * - cerrar sesión se confirma y la portada lo avisa.
 */
const IDP = /localhost:8090\/chessquery/;

async function fillIdp(page: Page, who: Persona) {
  await page.locator('input[name="username"]').fill(who.sub);
  await page.locator('textarea[name="claims"]').fill(JSON.stringify({
    email: who.email, email_verified: true, given_name: who.firstName, family_name: who.lastName,
  }));
  await page.locator('form').first().evaluate((f: HTMLFormElement) => f.submit());
}

test('portada: el organizador entra a su sección, crea su club y cambia de modo', async ({ browser }) => {
  const page = await (await browser.newContext()).newPage();
  const orga = persona('Octavia');
  await page.goto('/');
  await expectAccessible(page);
  await page.getByRole('button', { name: 'Entrar como organizador con Google' }).click();
  await fillIdp(page, orga);
  await expect(page).toHaveURL(/\/club$/);
  await page.getByLabel('Nombre del club').fill(`Club Sesión ${orga.lastName}`);
  await page.getByRole('button', { name: 'Crear club' }).click();

  const modo = page.getByRole('group', { name: 'Modo' }).first();
  await expect(modo.getByRole('link', { name: 'Organizador' })).toHaveAttribute('aria-current', 'page');
  await expect(page.getByRole('button', { name: 'Torneos del club' }).first()).toBeVisible();
  await modo.getByRole('link', { name: 'Jugador' }).click();
  await expect(page).toHaveURL(/\/app$/);
  await expect(page.getByRole('button', { name: 'Partidas' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Torneos del club' })).toHaveCount(0);
  await expectAccessible(page);
});

test('una ruta protegida sin sesión vuelve a la misma página tras el login', async ({ browser }) => {
  const page = await (await browser.newContext()).newPage();
  await page.goto('/app/amigos');
  await expect(page).toHaveURL(IDP);
  await fillIdp(page, persona('Pablo'));
  await expect(page).toHaveURL(/\/app\/amigos$/);
  await expect(page.getByRole('heading', { name: 'Amigos' })).toBeVisible();
});

test('sesión vencida: avisa y vuelve al login, de vuelta a la misma página; cerrar sesión se confirma y avisa', async ({ browser }) => {
  const page = await (await browser.newContext()).newPage();
  const who = persona('Vale');
  await page.goto('/');
  await page.getByRole('button', { name: 'Entrar como jugador con Google' }).click();
  await fillIdp(page, who);
  await expect(page.getByRole('heading', { name: `Hola, ${who.firstName}` })).toBeVisible();

  // La API rechaza el token y el IdP no deja renovarlo en silencio
  await page.route('**/api/friends**', (route) => route.fulfill({ status: 401, contentType: 'application/json',
    body: JSON.stringify({ status: 401, error: 'UNAUTHORIZED', message: 'Token vencido', timestamp: '' }) }));
  await page.route(IDP, (route) => route.abort());
  await page.getByRole('button', { name: 'Amigos' }).first().click();
  await expect(page.getByRole('alert').filter({ hasText: 'Tu sesión expiró' })).toBeVisible();

  // Vuelve el IdP: login de nuevo y de vuelta a Amigos
  await page.unroute(IDP);
  await page.unroute('**/api/friends**');
  await expect(page).toHaveURL(IDP, { timeout: 10_000 });
  await fillIdp(page, who);
  await expect(page).toHaveURL(/\/app\/amigos$/);

  // Cerrar sesión: se confirma en un diálogo y la portada lo avisa
  await page.getByRole('button', { name: /Cerrar sesión/ }).first().click();
  const confirm = page.getByRole('dialog', { name: '¿Cerrar sesión?' });
  await expectAccessible(page);
  await confirm.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Cerraste sesión' })).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole('button', { name: 'Entrar como jugador con Google' })).toBeVisible();
});

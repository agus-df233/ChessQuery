import { expect, test } from '@playwright/test';
import { captureBoth, expectAccessible, login, persona } from './support/helpers';

/** Todas las vistas del jugador: cargan sin errores, sin violaciones de axe y sin scroll horizontal en 375 px. */
const views: [string, string, string][] = [
  ['/app', 'Hola, Vera', 'inicio'],
  ['/app/perfil', 'Mi perfil', 'perfil'],
  ['/app/jugadores', 'Jugadores', 'jugadores'],
  ['/app/ranking', 'Ranking', 'ranking'],
  ['/app/partidas', 'Mis partidas', 'partidas'],
  ['/app/amigos', 'Amigos', 'amigos'],
  ['/app/torneos', 'Torneos', 'torneos-jugador'],
  ['/club', 'Crear mi club', 'crear-club'],
];

test('todas las vistas del jugador', async ({ browser }) => {
  const page = await login(browser, persona('Vera'));
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  for (const [path, heading, name] of views) {
    await page.goto(path);
    await expect(page.getByRole('heading', { name: heading, exact: false }).first()).toBeVisible();
    await expectAccessible(page);
    await captureBoth(page, `vista-${name}`);
  }
  const publicPage = await (await browser.newContext()).newPage();
  for (const [path, heading, name] of [['/', 'ChessQuery', 'portada'], ['/ranking', 'Ranking', 'ranking-publico']]) {
    await publicPage.goto(path);
    await expect(publicPage.getByRole('heading', { name: heading, exact: false }).first()).toBeVisible();
    await expectAccessible(publicPage);
    await captureBoth(publicPage, `vista-${name}`);
  }
  expect(errors).toEqual([]);
});

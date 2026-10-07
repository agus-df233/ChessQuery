import { expect, test } from '@playwright/test';
import { expectAccessible, login, loginAt, persona } from './support/helpers';

/**
 * Integrarse jugando: Ana publica un desafío abierto (enlace + QR) → Bruno, que no es su amigo y no tiene sesión abierta,
 * abre el enlace, entra con su cuenta y vuelve al desafío → acepta → los dos quedan en la misma partida y juegan.
 */
const ana = persona('Ana');
const bruno = persona('Bruno');

test('desafío abierto: alguien que no es amigo entra por el enlace y juega', async ({ browser }) => {
  const a = await login(browser, ana);
  await a.goto('/app/partidas');
  await a.getByLabel('Color').selectOption('WHITE');
  await a.getByRole('button', { name: 'Crear enlace' }).click();
  const enlace = await a.getByLabel('Enlace del desafío').inputValue();
  expect(enlace).toMatch(/\/app\/desafio\/[A-Za-z0-9_-]{22}$/);
  await expect(a.getByRole('img', { name: 'QR del desafío abierto' })).toBeVisible();
  await expectAccessible(a);

  const b = await loginAt(browser, bruno, new URL(enlace).pathname);
  await expect(b.getByText(/te desafía a una partida relámpago 3\+2, jugarías con negras/)).toBeVisible();
  await expectAccessible(b);
  await b.getByRole('button', { name: 'Aceptar y jugar' }).click();

  // Los dos en la misma partida: Bruno por aceptar, Ana sola (su enlace se entera de que alguien lo aceptó)
  await expect(b).toHaveURL(/\/app\/partidas\/\d+$/);
  await expect(a).toHaveURL(b.url());
  await expect(a.getByText('Tu turno')).toBeVisible();
  await a.getByRole('button', { name: /^e2,/ }).click();
  await a.getByRole('button', { name: /^e4,/ }).click();
  await expect(b.getByText('Tu turno')).toBeVisible();
});

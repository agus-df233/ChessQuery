import { expect, test } from '@playwright/test';
import { RUN, expectAccessible, login, loginAt, persona, reloadUntil } from './support/helpers';

/**
 * Integrar al jugador que el club ya conoce: el organizador carga a una alumna por CSV (en el servidor) y la inscribe
 * en un torneo en el mismo paso → genera su invitación → la alumna abre el enlace sin sesión, entra con su propia cuenta
 * (otro email) y reclama su perfil → el torneo pasa a su cuenta (player.merged → tournament).
 */
const org = persona('Olivia');
const gabi = persona('Gabi');

test('roster: carga masiva, invitación y la alumna reclama su perfil con su cuenta', async ({ browser }) => {
  const page = await login(browser, org);
  await page.goto('/club');
  await page.getByLabel('Nombre del club').fill(`Colegio Peón ${RUN}`);
  await page.getByRole('button', { name: 'Crear club' }).click();

  // Torneo abierto para inscribir a los importados
  await page.getByRole('link', { name: 'Torneos del club' }).click();
  await page.getByLabel('Nombre del torneo').fill(`Interescolar ${RUN}`);
  await page.getByRole('button', { name: 'Crear torneo' }).click();
  await expect(page.getByRole('heading', { name: `Interescolar ${RUN}` })).toBeVisible();

  await page.goto('/club');
  const csv = `nombre,apellido,email,rut,elo\nGabi,Escolar${RUN},,,1300\n`;
  await page.getByLabel('Archivo CSV del roster').setInputFiles({ name: 'roster.csv', mimeType: 'text/csv', buffer: Buffer.from(csv) });
  await page.getByLabel('Inscribir también en').selectOption({ label: `Interescolar ${RUN}` });
  await page.getByRole('button', { name: 'Importar' }).click();
  await expect(page.getByText(`Importados 1, inscritos en Interescolar ${RUN}: 1`)).toBeVisible();

  await page.getByRole('button', { name: `Invitar a Gabi Escolar${RUN} a reclamar su perfil` }).click();
  const enlace = await page.getByLabel(`Enlace de invitación de Gabi Escolar${RUN}`).inputValue();
  await expect(page.getByRole('img', { name: `QR de invitación de Gabi Escolar${RUN}` })).toBeVisible();
  await expectAccessible(page);

  // Gabi abre el enlace sin sesión, entra con su cuenta (otro email y otro apellido) y lo reclama
  const g = await loginAt(browser, gabi, new URL(enlace).pathname);
  await expect(g.getByRole('heading', { name: `¿Eres Gabi Escolar${RUN}?` })).toBeVisible();
  await expect(g.getByText(`Colegio Peón ${RUN} te cargó en su roster`, { exact: false })).toBeVisible();
  await expectAccessible(g);
  await g.getByRole('button', { name: 'Sí, soy yo: unirlo a mi cuenta' }).click();
  await expect(g.getByText(/el perfil quedó unido a tu cuenta/)).toBeVisible();

  // El torneo en que la inscribió su club ya es suyo (asíncrono: users → SNS → SQS → tournament)
  await g.goto('/app/torneos');
  await reloadUntil(g, `Interescolar ${RUN}`);
});

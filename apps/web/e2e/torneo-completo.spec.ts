import { expect, test } from '@playwright/test';
import { RUN, captureBoth, expectAccessible, login, persona } from './support/helpers';

/**
 * El organizador de punta a punta, con las reglas de un torneo real: carga su roster por CSV → crea un torneo con cupo,
 * aprobación y acreditación → inscribe al roster en bloque → una jugadora con cuenta se inscribe y él la aprueba →
 * el día del torneo acredita (con el código de la credencial y a mano) → quien no llegó queda "no se presentó" → genera
 * las rondas solo con los presentes → retira a una jugadora y la ronda 2 ya no la empareja.
 */
const org = persona('Octavio');
const carmen = persona('Carmen');
const roster = ['Beto', 'Dina', 'Elisa', 'Fabio'];
const rosterName = (n: string) => `${n} Club${RUN}`;

test('torneo completo: inscripción con reglas, acreditación, no presentados y retiros', async ({ browser }) => {
  const page = await login(browser, org);
  await page.goto('/club');
  await page.getByLabel('Nombre del club').fill(`Club Alfil ${RUN}`);
  await page.getByRole('button', { name: 'Crear club' }).click();
  const csv = ['nombre,apellido,email,rut,elo', ...roster.map((n, i) => `${n},Club${RUN},,,${1700 - i * 50}`)].join('\n');
  await page.getByLabel('Archivo CSV del roster').setInputFiles({ name: 'roster.csv', mimeType: 'text/csv', buffer: Buffer.from(csv) });
  await page.getByRole('button', { name: 'Importar' }).click();
  await expect(page.getByText('Importados 4')).toBeVisible();

  // Torneo con cupo 8, aprobación y acreditación obligatoria
  await page.getByRole('link', { name: 'Torneos del club' }).click();
  await page.getByLabel('Nombre del torneo').fill(`Copa Alfil ${RUN}`);
  await page.getByLabel('Rondas').fill('2');
  await page.getByLabel('Cupo de jugadores').fill('8');
  await page.getByLabel('Apruebo cada inscripción').check();
  await page.getByLabel('Acreditación con QR el día del torneo').check();
  await page.getByRole('button', { name: 'Crear torneo' }).click();
  await expect(page.getByRole('heading', { name: `Copa Alfil ${RUN}` })).toBeVisible();
  const tournament = page.url();
  await expect(page.getByText(/Inscripción: Cupo 8 · con aprobación del organizador · acreditación con QR/)).toBeVisible();

  // El roster entra en bloque (el organizador inscribe confirmado)
  await page.getByRole('button', { name: 'Inscribir a todo el roster' }).click();
  await expect(page.getByText('Inscritos 4')).toBeVisible();

  // Una jugadora con cuenta se inscribe: queda pendiente hasta que el organizador la apruebe
  const c = await login(browser, carmen);
  await c.goto(tournament.replace('/club/torneos/', '/app/torneos/'));
  await c.getByRole('button', { name: 'Inscribirme' }).click();
  await expect(c.getByText('Tu inscripción espera la aprobación del organizador.')).toBeVisible();
  await page.reload();
  await expect(page.getByText('Esperan tu aprobación (1)')).toBeVisible();
  await page.getByRole('listitem').filter({ hasText: `Carmen ${carmen.lastName}` }).getByRole('button', { name: 'Aprobar' }).click();
  await expect(page.getByText('Inscripciones · 5/8 cupos')).toBeVisible();
  await c.reload();
  await expect(c.getByRole('img', { name: 'Mi QR de acreditación' })).toBeVisible();
  await expectAccessible(c);

  // Credenciales impresas: el código de Carmen (el mismo de su QR)
  await page.getByRole('link', { name: 'Credenciales para imprimir' }).click();
  const credencial = page.getByRole('article', { name: `Credencial de Carmen ${carmen.lastName}` });
  const code = (await credencial.locator('code').textContent())!.trim();
  expect(code).toMatch(/^[A-Za-z0-9_-]{22}$/);
  await captureBoth(page, 'credenciales');

  // Acreditación: Carmen con su código; Beto, Dina y Elisa a mano; Fabio no llega
  await page.goto(`${tournament}/acreditacion`);
  await expect(page.getByText('0 de 5')).toBeVisible();
  await page.getByLabel('Código de acreditación').fill(code);
  await page.getByRole('button', { name: 'Acreditar', exact: true }).click();
  await expect(page.getByText(`Carmen ${carmen.lastName} acreditado`, { exact: true })).toBeVisible();
  for (const n of ['Beto', 'Dina', 'Elisa']) await page.getByRole('button', { name: `Acreditar a ${rosterName(n)}` }).click();
  await expect(page.getByText('4 de 5')).toBeVisible();
  await expectAccessible(page);

  // Ronda 1 solo con los 4 acreditados (2 mesas, sin bye); Fabio queda como "no se presentó"
  await page.goto(tournament);
  await page.getByRole('button', { name: 'Cerrar inscripciones y generar ronda 1' }).click();
  await expect(page.getByText('Ronda 1 · en juego')).toBeVisible();
  await expect(page.getByLabel('Resultado mesa 2')).toBeVisible();
  await expect(page.getByLabel('Resultado mesa 3')).toHaveCount(0);
  await expect(page.getByRole('listitem').filter({ hasText: rosterName('Fabio') })).toContainText('no se presentó');
  for (const board of [1, 2]) await page.getByLabel(`Resultado mesa ${board}`).selectOption('WHITE_WINS');

  // Elisa se retira: la ronda 2 empareja solo a los 3 que siguen (1 mesa + bye)
  await page.getByRole('listitem').filter({ hasText: rosterName('Elisa') }).getByRole('button', { name: 'Retirar' }).click();
  await page.getByRole('dialog', { name: /¿Retirar a Elisa/ }).getByRole('button', { name: 'Retirar' }).click();
  await expect(page.getByRole('listitem').filter({ hasText: rosterName('Elisa') })).toContainText('retirado desde la ronda 2');
  await page.getByRole('button', { name: 'Generar ronda 2' }).click();
  await expect(page.getByText('Ronda 2 · en juego')).toBeVisible();
  await expect(page.getByLabel('Resultado mesa 1')).toBeVisible();
  await expect(page.getByLabel('Resultado mesa 2')).toHaveCount(0); // la mesa 2 es el bye: no lleva resultado
  await captureBoth(page, 'torneo-con-retiro');
  await expectAccessible(page);

  // La vista pública muestra el retiro y no a quien no se presentó
  const publico = await (await browser.newContext()).newPage();
  await publico.goto(tournament.replace('/club/torneos/', '/torneos/'));
  await expect(publico.getByText('Inscritos (4)')).toBeVisible();
  await expect(publico.getByText(/retirado desde la ronda 2/)).toBeVisible();
});

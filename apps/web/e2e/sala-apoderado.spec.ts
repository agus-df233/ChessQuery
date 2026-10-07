import { expect, test } from '@playwright/test';
import { RUN, captureBoth, expectAccessible, login, persona } from './support/helpers';

/**
 * La sala en vivo: el organizador genera la ronda → la pantalla del monitor muestra los emparejamientos → un apoderado,
 * sin cuenta, sigue a su hijo desde el celular → el organizador carga los resultados y la pantalla y el celular se
 * actualizan solos, sin recargar.
 */
const org = persona('Mateo');
const roster = ['Beto', 'Dina', 'Elisa', 'Fabio'];

test('sala en vivo: pantalla del monitor y apoderado siguiendo a un jugador', async ({ browser }) => {
  const page = await login(browser, org);
  await page.goto('/club');
  await page.getByLabel('Nombre del club').fill(`Club Rey ${RUN}`);
  await page.getByRole('button', { name: 'Crear club' }).click();
  const csv = ['nombre,apellido,email,rut,elo', ...roster.map((n, i) => `${n},Sala${RUN},,,${1600 - i * 40}`)].join('\n');
  await page.getByLabel('Archivo CSV del roster').setInputFiles({ name: 'roster.csv', mimeType: 'text/csv', buffer: Buffer.from(csv) });
  await page.getByRole('button', { name: 'Importar' }).click();
  await expect(page.getByText('Importados 4')).toBeVisible();
  await page.getByRole('link', { name: 'Torneos del club' }).click();
  await page.getByLabel('Nombre del torneo').fill(`Sala en vivo ${RUN}`);
  await page.getByLabel('Rondas').fill('2');
  await page.getByRole('button', { name: 'Crear torneo' }).click();
  await expect(page.getByRole('heading', { name: `Sala en vivo ${RUN}` })).toBeVisible();
  await page.getByRole('button', { name: 'Inscribir a todo el roster' }).click();
  await expect(page.getByText('Inscritos 4')).toBeVisible();
  await page.getByRole('button', { name: 'Cerrar inscripciones y generar ronda 1' }).click();
  await expect(page.getByText('Ronda 1 · en juego')).toBeVisible();
  const id = page.url().split('/').pop()!;

  // El monitor de la sala (sin login)
  const monitor = await (await browser.newContext({ viewport: { width: 1920, height: 1080 } })).newPage();
  await monitor.goto(`/torneos/${id}/pantalla`);
  await expect(monitor.getByText('Ronda 1 · emparejamientos')).toBeVisible();
  await expect(monitor.getByRole('img', { name: 'QR para seguir el torneo desde el celular' })).toBeVisible();
  await expectAccessible(monitor);

  // Un apoderado en su celular, sin cuenta, sigue a Beto
  const phone = await (await browser.newContext({ viewport: { width: 375, height: 812 } })).newPage();
  await phone.goto(`/torneos/${id}`);
  await phone.getByLabel('Busca a tu hijo, a un amigo o a cualquier jugador').fill('Beto');
  await phone.getByRole('button', { name: `Seguir a Beto Sala${RUN}` }).click();
  const seguimiento = phone.getByRole('status').filter({ hasText: 'Ronda 1:' });
  await expect(seguimiento).toContainText('en juego');
  await expectAccessible(phone);

  // El organizador carga los resultados: la pantalla (con la rotación en pausa, en las mesas) y el celular se enteran solos
  await monitor.getByRole('button', { name: 'Pausar rotación' }).click();
  await expect(monitor.getByText('Ronda 1 · emparejamientos')).toBeVisible();
  for (const board of [1, 2]) await page.getByLabel(`Resultado mesa ${board}`).selectOption('WHITE_WINS');
  await expect(monitor.getByRole('cell', { name: '1-0' })).toHaveCount(2);
  await expect(seguimiento).toContainText(/ganó|perdió/);
  await expect(phone.getByText(/Va \d+° con \d\.\d puntos/)).toBeVisible();
  await captureBoth(phone, 'apoderado-siguiendo');
  await monitor.screenshot({ path: 'e2e/capturas/pantalla-sala-1920.png' });
});

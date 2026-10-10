import { expect, test } from '@playwright/test';
import { RUN, captureBoth, expectAccessible, login, persona, reloadUntil } from './support/helpers';

/**
 * Recorrido del organizador: crea su club → carga el roster por CSV → etiqueta a un jugador → crea un torneo suizo
 * de 3 rondas → inscribe al roster → genera rondas y carga resultados → cierra el torneo (ratings) → exporta TRF →
 * la ficha del jugador muestra su nuevo rating de plataforma → la sala ve la tabla sin login.
 */
const org = persona('Olga');

test('organizador: club, roster, torneo suizo completo, TRF y vista pública', async ({ browser }) => {
  const page = await login(browser, org);

  await page.goto('/club');
  await page.getByLabel('Nombre del club').fill(`Club Torre ${RUN}`);
  await page.getByRole('button', { name: 'Crear club' }).click();
  await expect(page.getByRole('heading', { name: `Club Torre ${RUN}` })).toBeVisible();

  const csv = ['nombre,apellido,email,rut,elo', ...['Beto', 'Carla', 'Dante', 'Elisa', 'Fabio'].map((n, i) =>
    `${n},Roster${RUN},,,${1800 - i * 60}`)].join('\n');
  await page.getByLabel('Archivo CSV del roster').setInputFiles({ name: 'roster.csv', mimeType: 'text/csv', buffer: Buffer.from(csv) });
  await expect(page.getByText('5 para importar')).toBeVisible();
  await page.getByRole('button', { name: 'Importar' }).click();
  await expect(page.getByText('Importados 5')).toBeVisible();

  // Info de jugadores del club: etiquetas editables en el roster
  await page.getByRole('button', { name: `Editar etiquetas de Beto Roster${RUN}` }).click();
  const etiquetas = page.getByRole('dialog', { name: `Etiquetas de Beto Roster${RUN}` });
  await etiquetas.getByLabel('Etiquetas (separadas por coma)').fill('sub12, federado');
  await etiquetas.getByRole('button', { name: 'Guardar etiquetas' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Etiquetas guardadas' })).toBeVisible();
  const beto = page.getByRole('row').filter({ hasText: `Beto Roster${RUN}` });
  await expect(beto.getByText('sub12')).toBeVisible();
  await expect(beto.getByText('federado')).toBeVisible();
  await captureBoth(page, 'club');
  await expectAccessible(page);

  await page.getByRole('link', { name: 'Torneos del club' }).click();
  await page.getByLabel('Nombre del torneo').fill(`Abierto E2E ${RUN}`);
  await page.getByLabel('Ciudad').fill('Santiago');
  await page.getByLabel('Rondas').fill('3');
  await page.getByRole('button', { name: 'Crear torneo' }).click();
  await expect(page.getByRole('heading', { name: `Abierto E2E ${RUN}` })).toBeVisible();
  const tournamentId = page.url().split('/').pop()!;

  await page.getByRole('button', { name: 'Inscribir a todo el roster' }).click();
  await expect(page.getByText('Inscritos 5')).toBeVisible();
  await expect(page.getByText('Inscritos (5)')).toBeVisible();

  for (let round = 1; round <= 3; round++) {
    await page.getByRole('button', { name: round === 1 ? 'Cerrar inscripciones y generar ronda 1' : `Generar ronda ${round}` }).click();
    await expect(page.getByText(`Ronda ${round} · en juego`)).toBeVisible();
    // 5 jugadores: 2 mesas + bye
    for (const [board, result] of [[1, 'WHITE_WINS'], [2, 'DRAW']] as const) {
      await page.getByLabel(`Resultado mesa ${board}`).selectOption(result);
      await expect(page.getByLabel(`Resultado mesa ${board}`)).toHaveValue(result);
    }
    await expect(page.getByText(`Ronda ${round}`, { exact: true })).toBeVisible();
    if (round === 2) await captureBoth(page, 'torneo-organizador');
  }
  await expectAccessible(page);

  await page.getByRole('button', { name: 'Cerrar torneo' }).click();
  await expect(page.getByText('Torneo cerrado: ratings enviados')).toBeVisible();
  await expect(page.getByText('Terminado')).toBeVisible();

  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Exportar TRF' }).click();
  const trf = await (await download).createReadStream().then(async (s) => {
    const chunks: Buffer[] = [];
    for await (const c of s) chunks.push(c as Buffer);
    return Buffer.concat(chunks).toString('utf-8');
  });
  expect(trf.startsWith(`012 Abierto E2E ${RUN}`)).toBe(true);
  expect(trf.split('\n').filter((l) => l.startsWith('001'))).toHaveLength(5);

  // El cierre actualiza los datos del jugador: el torneo es 60+30 (clásico), así que su ficha muestra el ELO
  // ChessQuery clásico (elo.updated con PLATFORM_CLASSICAL → users)
  await page.goto('/app/jugadores');
  await page.getByLabel('Buscar jugador').fill(`Beto Roster${RUN}`);
  await page.getByRole('button', { name: 'Buscar' }).click();
  await page.getByRole('link', { name: new RegExp(`Beto Roster${RUN}`) }).click();
  await reloadUntil(page, /^Clásica$/);
  await expect(page.getByRole('region', { name: 'Ratings ChessQuery' })).toContainText(/\d{3,4}/);
  await captureBoth(page, 'ficha-jugador-roster');

  // La sala: vista pública sin login
  const sala = await (await browser.newContext()).newPage();
  await sala.goto(`/torneos/${tournamentId}`);
  await expect(sala.getByText('Clasificación')).toBeVisible();
  await expect(sala.getByRole('row')).not.toHaveCount(0);
  await captureBoth(sala, 'torneo-publico');
  await expectAccessible(sala);
  await sala.goto('/torneos');
  await expect(sala.getByText(`Abierto E2E ${RUN}`)).toBeVisible();
  await expect(sala.getByText('ABIERTO FICTICIO E2E').or(sala.getByText('Sin torneos federados próximos.'))).toBeVisible();
  await captureBoth(sala, 'torneos-publico');
});

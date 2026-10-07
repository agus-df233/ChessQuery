import { expect, test, type Page } from '@playwright/test';
import { RUN, captureBoth, expectAccessible, login, persona, reloadUntil } from './support/helpers';

/**
 * Recorrido del jugador: login → editar perfil → vincular ficha federativa (el ETL la trae) → buscar a otro jugador
 * → solicitud de amistad y aceptación → desafiarlo → el rival acepta → partida en vivo hasta el mate (mate del
 * pastor) → ambos ven el resultado y el rating de plataforma actualizado en su inicio.
 */
const ana = persona('Ana');
const luis = persona('Luis');

async function play(page: Page, from: string, to: string) {
  await expect(page.getByText('Tu turno')).toBeVisible();
  await page.getByRole('button', { name: new RegExp(`^${from},`) }).click();
  await page.getByRole('button', { name: new RegExp(`^${to},`) }).click();
}

test('jugador: ficha federativa, desafío, partida en vivo y rating actualizado', async ({ browser }) => {
  const a = await login(browser, ana);
  await captureBoth(a, 'inicio-jugador');
  await expectAccessible(a);

  // Vincular la ficha: users publica federation.lookup.requested; el ETL consulta la Federación (falsa)
  const fichaId = `7${Date.now().toString().slice(-6)}`; // los ids 7xx existen en la Federación falsa
  await a.getByLabel('Mi id federativo').fill(fichaId);
  await a.getByRole('button', { name: 'Vincular mi ficha' }).click();
  await expect(a.getByText(`Ficha federativa ${fichaId}`)).toBeVisible();
  await reloadUntil(a, 'ELO nacional 1720');

  // Datos del perfil
  await a.goto('/app/perfil');
  await a.getByLabel('Región').fill('Valparaíso');
  await a.getByRole('button', { name: 'Guardar cambios' }).click();
  await expect(a.getByText('Perfil guardado')).toBeVisible();

  const l = await login(browser, luis);

  // Ana busca a Luis, le envía solicitud de amistad y Luis la acepta
  await a.goto('/app/jugadores');
  await a.getByLabel('Buscar jugador').fill(`Luis ${luis.lastName}`);
  await a.getByRole('button', { name: 'Buscar' }).click();
  await a.getByRole('link', { name: new RegExp(`Luis ${luis.lastName}`) }).click();
  const perfilLuis = a.url();
  await a.getByRole('button', { name: 'Agregar amigo' }).click();
  // Esperar a que la solicitud quede guardada: si Luis abre Amigos antes, no la ve (la página no se refresca sola)
  await expect(a.getByRole('button', { name: 'Cancelar solicitud' })).toBeVisible();
  await l.goto('/app/amigos');
  await l.getByRole('button', { name: 'Aceptar' }).first().click();
  await expect(l.getByText(`Ana ${ana.lastName}`)).toBeVisible();

  // Ana lo desafía con blancas desde su perfil
  await a.goto(perfilLuis);
  await a.getByRole('button', { name: 'Desafiar' }).click();
  await a.getByLabel('Juego con').selectOption('WHITE');
  await a.getByRole('button', { name: 'Enviar desafío' }).click();
  await expect(a.getByText('Esperando que tu rival acepte…')).toBeVisible();
  const gameUrl = a.url();

  // Luis ve el desafío en "Mis partidas" y lo acepta; Ana se entera sola (long polling)
  await l.goto('/app/partidas');
  await captureBoth(l, 'mis-partidas');
  await l.getByRole('button', { name: 'Aceptar' }).first().click();
  // Las jugadas del rival deben llegar por el WebSocket (frames recibidos con el estado de la partida)
  const framesLuis: string[] = [];
  l.on('websocket', (ws) => ws.on('framereceived', (f) => framesLuis.push(String(f.payload))));
  await l.goto(gameUrl);
  await expect(a.getByText('Tu turno')).toBeVisible();

  // Mate del pastor: 1.e4 e5 2.Ac4 Cc6 3.Dh5 Cf6 4.Dxf7#
  const moves: [Page, string, string][] = [[a, 'e2', 'e4'], [l, 'e7', 'e5'], [a, 'f1', 'c4'], [l, 'b8', 'c6'],
    [a, 'd1', 'h5'], [l, 'g8', 'f6'], [a, 'h5', 'f7']];
  for (const [page, from, to] of moves) await play(page, from, to);

  await expect(a.getByText('Ganaste · jaque mate')).toBeVisible();
  const jugadasPorSocket = framesLuis.filter((f) => f.includes('"type":"game"') && /"ply":[1-7]/.test(f));
  expect(jugadasPorSocket.length, 'Luis recibió las jugadas de Ana por WebSocket').toBeGreaterThanOrEqual(3);
  await expect(l.getByText('Perdiste · jaque mate')).toBeVisible();
  await expect(a.getByRole('link', { name: 'Descargar PGN' })).toBeVisible();
  await captureBoth(a, 'partida-terminada');
  await expectAccessible(a);

  // Rating de plataforma: game publica elo.updated → users lo aplica (asíncrono por SQS)
  const line = await a.getByText(/Ana E2E\w+: \d+ \(\+\d+\)/).first().textContent();
  const newRating = line!.match(/: (\d+) \(/)![1];
  await a.goto('/app');
  await reloadUntil(a, newRating);
  await expect(a.getByRole('region', { name: 'Ratings ChessQuery' })).toContainText(newRating);
  await l.goto('/app/partidas');
  await expect(l.getByText(/Perdiste · jaque mate · −\d+/)).toBeVisible();
});

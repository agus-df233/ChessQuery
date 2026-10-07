import { expect, test, type Page } from '@playwright/test';
import { RUN, captureBoth, expectAccessible, login, loginAt, persona } from './support/helpers';

/**
 * La clase en el colegio: el profesor abre una sala de 2 tableros con cupo 5 → entran 5 alumnos con el código (uno
 * queda mirando) → los asigna e inicia todos → cada alumno pasa solo a su partida y juega → la cuadrícula del profesor
 * y la del espectador se actualizan sin recargar → sube a 3 tableros y le da puesto al espectador.
 */
const profe = persona('Profe');
const alumnos = ['Alu1', 'Alu2', 'Alu3', 'Alu4', 'Alu5'].map(persona);
const nombre = (i: number) => `${alumnos[i].firstName} ${alumnos[i].lastName}`;

async function asignar(page: Page, tablero: number, blancas: number | null, negras: number | null) {
  const board = page.getByRole('article', { name: `Tablero ${tablero}` });
  if (blancas !== null) await board.getByLabel(`Blancas tablero ${tablero}`).selectOption({ label: nombre(blancas) });
  if (negras !== null) await board.getByLabel(`Negras tablero ${tablero}`).selectOption({ label: nombre(negras) });
  await board.getByRole('button', { name: 'Guardar puestos' }).click();
}

test('sala de juego: clase con tableros asignados, espectador y cuadrícula en vivo', async ({ browser }) => {
  const p = await login(browser, profe);
  await p.goto('/club');
  await p.getByLabel('Nombre del club').fill(`Colegio ${RUN}`);
  await p.getByRole('button', { name: 'Crear club' }).click();
  await expect(p.getByRole('heading', { name: `Colegio ${RUN}` })).toBeVisible();

  await p.getByRole('link', { name: 'Salas de juego' }).click();
  await p.getByLabel('Nombre de la sala').fill(`Clase ${RUN}`);
  await p.getByLabel('Tableros', { exact: true }).fill('2');
  await p.getByLabel('Cupo de jugadores').fill('5');
  await p.getByRole('button', { name: 'Abrir sala' }).click();
  const code = (await p.locator('.cq-room-code-value').textContent())!.trim();
  expect(code).toMatch(/^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6}$/);
  await expect(p.getByRole('img', { name: `QR para entrar a la sala con el código ${code}` })).toBeVisible();
  await captureBoth(p, 'sala-organizador');
  await expectAccessible(p);

  // Entran los 5 alumnos con el enlace del QR (el código viene en la URL). El último lo abre sin sesión: tras el
  // login debe volver al enlace, con el código
  const pages: Page[] = [];
  for (const [i, alumno] of alumnos.entries()) {
    const enlace = `/app/salas?codigo=${code.toLowerCase()}`;
    const a = i === alumnos.length - 1 ? await loginAt(browser, alumno, enlace) : await login(browser, alumno);
    if (i < alumnos.length - 1) await a.goto(enlace);
    await expect(a.getByLabel('Código de la sala')).toHaveValue(code.toLowerCase());
    await a.getByRole('button', { name: 'Entrar' }).click();
    await expect(a.getByText('Estás mirando como espectador. El organizador te asignará un tablero.')).toBeVisible();
    pages.push(a);
  }
  await expectAccessible(pages[4]);

  // El profesor asigna 1-2 y 3-4; el 5 sigue mirando. Inicia todo.
  await expect(p.getByText('Jugadores (5/5)')).toBeVisible(); // la lista se actualiza sola al entrar cada alumno
  await asignar(p, 1, 0, 1);
  await asignar(p, 2, 2, 3);
  await expect(pages[0].getByText('Te toca el tablero 1 con blancas: la partida empieza cuando el organizador la inicie.')).toBeVisible();
  await p.getByRole('button', { name: 'Iniciar todos los tableros listos' }).click();

  // Cada alumno pasa solo a su partida; el de blancas del tablero 1 juega e4
  await expect(pages[0]).toHaveURL(/\/app\/partidas\/\d+$/);
  await expect(pages[3]).toHaveURL(/\/app\/partidas\/\d+$/);
  await expect(pages[0].getByText('Tu turno')).toBeVisible();
  await pages[0].getByRole('button', { name: /^e2,/ }).click();
  await pages[0].getByRole('button', { name: /^e4,/ }).click();

  // La jugada llega sola a la cuadrícula del profesor y a la del espectador
  const tablero1 = /Tablero 1: .*\(blancas\) contra .*\(negras\), en juego · jugada 1 · mueven negras/;
  await expect(p.getByRole('img', { name: tablero1 })).toBeVisible();
  await expect(pages[4].getByRole('img', { name: tablero1 })).toBeVisible();
  await captureBoth(p, 'sala-tableros-en-vivo');

  // Sube a 3 tableros y le da puesto al espectador
  await p.getByRole('button', { name: 'Cambiar tableros o cupo' }).click();
  await p.getByLabel('Tableros', { exact: true }).fill('3');
  await p.getByRole('button', { name: 'Guardar', exact: true }).click();
  await expect(p.getByText('Tableros 1–3 de 3')).toHaveCount(0); // 3 tableros caben en una pantalla
  await asignar(p, 3, 4, null);
  await expect(pages[4].getByText('Te toca el tablero 3 con blancas: la partida empieza cuando el organizador la inicie.')).toBeVisible();
  await expectAccessible(p);
});

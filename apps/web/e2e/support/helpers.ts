import { expect, type Browser, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/** Sufijo único por corrida: permite repetir el E2E sin limpiar la base de datos. */
export const RUN = Date.now().toString(36).slice(-5);

export interface Persona { sub: string; firstName: string; lastName: string; email: string }

export const persona = (firstName: string): Persona => ({
  sub: `${firstName.toLowerCase()}-${RUN}`, firstName, lastName: `E2E${RUN}`, email: `${firstName.toLowerCase()}.${RUN}@e2e.cl`,
});

/**
 * Login por la portada con el IdP simulado (hace de Entra): "Entrar con mi correo" → formulario del IdP con el
 * sujeto y los claims que Entra entregaría (email, given_name, family_name).
 */
export async function login(browser: Browser, who: Persona): Promise<Page> {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto('/');
  await page.getByRole('button', { name: 'Entrar con mi correo' }).click();
  await page.locator('input[name="username"]').fill(who.sub);
  await page.locator('textarea[name="claims"]').fill(JSON.stringify({
    email: who.email, email_verified: true, given_name: who.firstName, family_name: who.lastName,
  }));
  await page.locator('form').first().evaluate((f: HTMLFormElement) => f.submit());
  await expect(page.getByRole('heading', { name: `Hola, ${who.firstName}` })).toBeVisible();
  return page;
}

/** axe con WCAG 2 A/AA sobre la página actual. */
export async function expectAccessible(page: Page) {
  // Con una transición en curso (fade-in de la vista) axe mediría colores intermedios que el usuario no ve
  await page.waitForFunction(() => document.getAnimations().every((a) => a.playState !== 'running'));
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
  expect(results.violations.map((v) => `${v.id}: ${v.nodes.map((n) => `${n.target.join(' ')} (${n.failureSummary?.split('\n')[1]?.trim()})`).join(' | ')}`))
    .toEqual([]);
}

/** Captura en escritorio y en 375 px (celular) para el reporte de verificación. */
export async function captureBoth(page: Page, name: string) {
  const size = page.viewportSize();
  await page.screenshot({ path: `e2e/capturas/${name}-escritorio.png`, fullPage: true });
  await page.setViewportSize({ width: 375, height: 812 });
  await page.waitForFunction(() => document.getAnimations().every((a) => a.playState !== 'running'));
  await page.screenshot({ path: `e2e/capturas/${name}-375.png`, fullPage: true });
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow, `${name}: sin scroll horizontal en 375 px`).toBeLessThanOrEqual(1);
  if (size) await page.setViewportSize(size);
}

/** Recarga hasta que aparezca un texto (para efectos asíncronos: SNS → SQS → consumidor). */
export async function reloadUntil(page: Page, text: string | RegExp, tries = 20) {
  for (let i = 0; i < tries; i++) {
    if (await page.getByText(text).first().isVisible().catch(() => false)) return;
    await page.waitForTimeout(1500);
    await page.reload();
  }
  await expect(page.getByText(text).first()).toBeVisible();
}

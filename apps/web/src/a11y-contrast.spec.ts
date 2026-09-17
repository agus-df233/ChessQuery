import { describe, expect, it } from 'vitest';
import { PALETTE, contrastRatio, WCAG_AA_NORMAL } from '@chessquery/ui-lib';

/** Gate WCAG AA: todo color de texto de la paleta debe contrastar ≥ 4.5:1 con el fondo. */
describe('Contraste de la paleta (WCAG AA)', () => {
  it.each([['text', PALETTE.text], ['textDim', PALETTE.textDim], ['textMuted', PALETTE.textMuted]])(
    '%s cumple AA', (_n, color) => expect(contrastRatio(color, PALETTE.bg)).toBeGreaterThanOrEqual(WCAG_AA_NORMAL));
});

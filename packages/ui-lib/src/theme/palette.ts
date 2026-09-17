/**
 * Paleta de texto validada por contraste (EP-A1 / A1-01).
 *
 * Fuente de verdad en JS de los colores de texto sobre el fondo base, para poder
 * validar el contraste WCAG en tests. Los mismos valores viven en `theme.css`
 * como custom properties (`--cq-*`). Si cambia uno, cambiar el otro.
 */
export const PALETTE = {
  bg: '#111210',
  text: '#e8ead4',
  textDim: '#9a9c8c',
  textMuted: '#8b8e7b', // A1-01: subido desde #6a6d5e (3.54:1 → 5.59:1) para pasar AA
  borderFocus: '#4a7c59',
} as const;

/** Escala tipográfica (A1-02). Valores en rem; base del documento = 16px. */
export const TYPE_SCALE = {
  xs: '0.75rem', // 12px — mínimo, solo metadatos, siempre con line-height ≥ 1.4
  sm: '0.875rem', // 14px
  base: '1rem', // 16px — cuerpo por defecto
  lg: '1.125rem', // 18px
  xl: '1.375rem', // 22px
  '2xl': '1.75rem', // 28px
} as const;

function channelLuminance(c: number): number {
  const s = c / 255;
  return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
}

/** Luminancia relativa WCAG de un color `#rrggbb`. */
export function relativeLuminance(hex: string): number {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return 0.2126 * channelLuminance(r) + 0.7152 * channelLuminance(g) + 0.0722 * channelLuminance(b);
}

/** Ratio de contraste WCAG entre dos colores `#rrggbb` (1..21). */
export function contrastRatio(fg: string, bg: string): number {
  const a = relativeLuminance(fg) + 0.05;
  const b = relativeLuminance(bg) + 0.05;
  return Math.max(a, b) / Math.min(a, b);
}

/** WCAG AA para texto normal. */
export const WCAG_AA_NORMAL = 4.5;

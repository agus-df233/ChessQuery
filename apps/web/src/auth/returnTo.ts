/**
 * A dónde volver después del login: la página que se pidió sin sesión (p. ej. el enlace del QR de una sala, con su
 * `?codigo=`). Solo rutas internas de la app: nada que empiece con `//` o lleve `\` (el navegador las trata como otro
 * dominio), para que el parámetro no sirva de redirección abierta.
 */
export const safeReturnPath = (state: unknown): string | null => {
  if (typeof state !== 'string' || !state.startsWith('/')) return null;
  if (state.startsWith('//') || state.includes('\\') || /[\u0000-\u001f]/.test(state)) return null;
  return state;
};

export const currentPath = () => `${window.location.pathname}${window.location.search}`;

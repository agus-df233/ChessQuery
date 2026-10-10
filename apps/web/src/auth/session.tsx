import { ReactNode, useEffect, useRef } from 'react';
import { useAuth } from 'react-oidc-context';
import { useToast } from '@chessquery/ui-lib';
import { setUnauthorizedHandler } from '../api/client';
import { SIGNED_OUT_FLAG, bearerOf } from './provider';
import { currentPath } from './returnTo';
import { tokenStore } from './tokenStore';

/** Tras un breve aviso, vuelve al login conservando la página en la que estaba. */
const RELOGIN_DELAY_MS = 1500;

/**
 * Manejo de la sesión en toda la app:
 * - un 401 de la API intenta renovar la sesión en silencio (refresh token) y repite el pedido;
 * - si no se puede, avisa «Tu sesión expiró» y vuelve al login, de vuelta a la misma página;
 * - al volver de cerrar sesión, avisa «Cerraste sesión».
 */
export const SessionManager = ({ children }: { children: ReactNode }) => {
  const auth = useAuth();
  const toast = useToast();
  // Varios pedidos pueden recibir 401 a la vez: comparten una sola renovación, y si falla se avisa una sola vez.
  const renewing = useRef<Promise<string | null> | null>(null);
  const expired = useRef(false);

  useEffect(() => {
    const renew = async (): Promise<string | null> => {
      try {
        const token = bearerOf(await auth.signinSilent());
        tokenStore.set(token);
        if (token) return token;
      } catch {
        // la renovación falló: se pide entrar de nuevo
      }
      if (!expired.current) {
        expired.current = true;
        toast.error('Tu sesión expiró. Te llevamos a entrar de nuevo…');
        const back = currentPath();
        setTimeout(() => void auth.signinRedirect({ state: back }), RELOGIN_DELAY_MS);
      }
      return null;
    };
    setUnauthorizedHandler(() => {
      renewing.current ??= renew().finally(() => { renewing.current = null; });
      return renewing.current;
    });
    return () => setUnauthorizedHandler(null);
  }, [auth, toast]);

  useEffect(() => {
    try {
      if (sessionStorage.getItem(SIGNED_OUT_FLAG)) {
        sessionStorage.removeItem(SIGNED_OUT_FLAG);
        toast.info('Cerraste sesión. ¡Hasta pronto!');
      }
    } catch {
      // sin sessionStorage (modo privado estricto): no hay aviso, nada más
    }
  }, [toast]);

  return <>{children}</>;
};

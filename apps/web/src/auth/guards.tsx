import { ReactNode, useEffect } from 'react';
import { useAuth } from 'react-oidc-context';
import { tokenStore } from './tokenStore';
import { bearerOf } from './provider';
import { currentPath } from './returnTo';
import { useMe } from '../api/hooks';

/** Sincroniza el token de la sesión OIDC con el cliente HTTP. Va una vez, en la raíz. */
export const TokenSync = ({ children }: { children: ReactNode }) => {
  const auth = useAuth();
  useEffect(() => {
    tokenStore.set(bearerOf(auth.user));
  }, [auth.user]);
  return <>{children}</>;
};

/** Exige sesión: si no la hay, redirige al login del IdP (no hay pantalla de login propia). */
export const RequireAuth = ({ children }: { children: ReactNode }) => {
  const auth = useAuth();
  useEffect(() => {
    if (!auth.isLoading && !auth.isAuthenticated && !auth.activeNavigator) {
      void auth.signinRedirect({ state: currentPath() }); // al volver del login, de vuelta a esta página
    }
  }, [auth]);

  if (auth.isLoading || !auth.isAuthenticated) {
    return <p className="cq-status" role="status">Conectando con tu cuenta…</p>;
  }
  return <>{children}</>;
};

/** Exige ser organizador (tener club). Si no, muestra el camino para crearlo. */
export const RequireOrganizer = ({ children, fallback }: { children: ReactNode; fallback: ReactNode }) => {
  const me = useMe();
  if (me.isLoading) return <p className="cq-status" role="status">Cargando…</p>;
  return <>{me.data?.organizer ? children : fallback}</>;
};
